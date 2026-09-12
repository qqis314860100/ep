#!/usr/bin/env node
/**
 * mock-files.mjs
 * --------------
 * 生成 E2E 全流程测试所需的 mock 文件（真实文件签名，可通过后端校验）。
 * 输出目录：scripts/e2e/.mock-files/
 *
 * 生成内容：
 *   - PDF   三维图纸 / 二维图纸 / 说明附件（%PDF 签名，最小合法 PDF）
 *   - TXT   其他附件（无签名校验）
 *   - X_T  三维源模型（无签名校验）
 *   - PNG   评论图片（合法 1x1 PNG）
 *
 * 用法：node mock-files.mjs [输出目录]
 */
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const outDir = process.argv[2] || join(__dirname, '.mock-files')
mkdirSync(outDir, { recursive: true })

/* ------------------------------------------------------------------ */
/* 最小合法 PDF 生成器（含正确 xref 偏移，可用 pdfinfo/浏览器打开）     */
/* ------------------------------------------------------------------ */
function makePdf(title) {
  const objects = []
  const add = (body) => {
    objects.push(body)
    return objects.length // 1-based object number
  }

  add('<< /Type /Catalog /Pages 2 0 R >>')
  add('<< /Type /Pages /Kids [3 0 R] /Count 1 >>')
  add('<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>')

  const stream = `BT /F1 18 Tf 72 770 Td (${title}) Tj ET`
  add(`<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`)

  add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>')

  let pdf = '%PDF-1.4\n'
  const offsets = []
  objects.forEach((body, index) => {
    offsets.push(pdf.length)
    pdf += `${index + 1} 0 obj\n${body}\nendobj\n`
  })

  const xrefStart = pdf.length
  pdf += `xref\n0 ${objects.length + 1}\n`
  pdf += '0000000000 65535 f \n'
  for (const offset of offsets) {
    pdf += `${String(offset).padStart(10, '0')} 00000 n \n`
  }
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefStart}\n%%EOF\n`
  return Buffer.from(pdf, 'latin1')
}

/* ------------------------------------------------------------------ */
/* 最小合法 1x1 PNG（PNG 签名 + IHDR + IDAT + IEND）                    */
/* ------------------------------------------------------------------ */
function makePng() {
  // 1x1 像素，RGBA，红色
  const signature = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])
  const ihdrData = Buffer.alloc(13)
  ihdrData.writeUInt32BE(1, 0) // width
  ihdrData.writeUInt32BE(1, 4) // height
  ihdrData[8] = 8 // bit depth
  ihdrData[9] = 6 // color type RGBA
  const chunk = (type, data) => {
    const len = Buffer.alloc(4)
    len.writeUInt32BE(data.length, 0)
    const typeBuf = Buffer.from(type, 'ascii')
    const crc = crc32(Buffer.concat([typeBuf, data]))
    const crcBuf = Buffer.alloc(4)
    crcBuf.writeUInt32BE(crc >>> 0, 0)
    return Buffer.concat([len, typeBuf, data, crcBuf])
  }
  const ihdr = chunk('IHDR', ihdrData)
  // 行滤波字节 0 + RGBA(255,0,0,255)
  const idat = chunk('IDAT', zlibDeflate(Buffer.from([0x00, 0xff, 0x00, 0x00, 0xff])))
  const iend = chunk('IEND', Buffer.alloc(0))
  return Buffer.concat([signature, ihdr, idat, iend])
}

let crcTable
function crc32(buf) {
  if (!crcTable) {
    crcTable = new Int32Array(256)
    for (let n = 0; n < 256; n++) {
      let c = n
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
      crcTable[n] = c
    }
  }
  let crc = -1
  for (const byte of buf) crc = (crc >>> 8) ^ crcTable[(crc ^ byte) & 0xff]
  return (crc ^ -1) >>> 0
}

// 极简 deflate：无压缩存储块（RFC 1951），无需 zlib 依赖
function zlibDeflate(input) {
  // zlib 头 0x78 0x01 + deflate stored block + adler32
  const stored = deflateStored(input)
  const adler = adler32(input)
  const out = Buffer.alloc(2 + stored.length + 4)
  out[0] = 0x78
  out[1] = 0x01
  stored.copy(out, 2)
  out.writeUInt32BE(adler, 2 + stored.length)
  return out
}

function deflateStored(input) {
  const blocks = []
  const max = 0xffff
  let remaining = input.length
  let offset = 0
  let first = true
  while (remaining > 0) {
    const len = Math.min(remaining, max)
    const header = first ? 0x01 : 0x00 // BFINAL on last? no: 0x01 only if final
    // BFINAL bit: only set when this is the last block
    const bfinal = remaining <= max ? 1 : 0
    const btype = 0
    const headerByte = (bfinal << 0) | (btype << 1)
    blocks.push(Buffer.from([headerByte]))
    const lenBuf = Buffer.alloc(4)
    lenBuf.writeUInt16LE(len, 0)
    lenBuf.writeUInt16LE(~len & 0xffff, 2)
    blocks.push(lenBuf)
    blocks.push(input.subarray(offset, offset + len))
    offset += len
    remaining -= len
    first = false
  }
  if (offset === 0) {
    // 空输入：一个 final 空 stored 块
    return Buffer.from([0x01, 0x00, 0x00, 0xff, 0xff])
  }
  return Buffer.concat(blocks)
}

function adler32(input) {
  let a = 1
  let b = 0
  const MOD = 65521
  for (const byte of input) {
    a = (a + byte) % MOD
    b = (b + a) % MOD
  }
  return ((b << 16) | a) >>> 0
}

/* ------------------------------------------------------------------ */
/* 生成文件                                                             */
/* ------------------------------------------------------------------ */
const files = [
  { name: '宁德-H03-电池包-三维源模型.x_t', role: '三维源模型', content: makePdf('宁德 H03 电池包 三维源模型') },
  { name: '宁德-H03-电池包-总成图.pdf', role: '二维图纸', content: makePdf('宁德 H03 电池包 总成图') },
  { name: '宁德-H03-电池包-安装示意图.pdf', role: '二维图纸', content: makePdf('宁德 H03 电池包 安装示意图') },
  { name: '宁德-H03-电池包-装配说明书.txt', role: '说明附件', content: Buffer.from('装配说明：\n1. 检查电池包外观无磕碰\n2. 按扭矩要求紧固安装螺栓\n3. 连接低压线束并做绝缘检测\n', 'utf8') },
  { name: '模组-M03-线束走向.step', role: '三维源模型', content: makePdf('模组 M03 线束走向') },
]

const generated = []
for (const file of files) {
  const path = join(outDir, file.name)
  writeFileSync(path, file.content)
  generated.push({ path, name: file.name, role: file.role, sizeBytes: file.content.length })
}

// 评论图片
const pngPath = join(outDir, 'comment.png')
writeFileSync(pngPath, makePng())
generated.push({ path: pngPath, name: 'comment.png', role: '评论图片', sizeBytes: makePng().length })

// 文档用文件
const docPdf = makePdf('H03 电池包装配作业指导书')
writeFileSync(join(outDir, '作业指导书-H03-电池包装配.pdf'), docPdf)
generated.push({ path: join(outDir, '作业指导书-H03-电池包装配.pdf'), name: '作业指导书-H03-电池包装配.pdf', role: '知识文档', sizeBytes: docPdf.length })

// AI 索引链路用文件：正文必须远大于 ai-rag 的切分下限（MIN_CHUNK_SIZE=120 字符），
// 否则会切出 0 个块——入库"成功"却检索不到（见 flow.mjs 阶段 11 的说明）。
// 内含唯一锚点 AI_INDEX_ANCHOR，供检索断言精确定位本文档。
const aiAnchor = 'AI_INDEX_ANCHOR_7f3c9d2b'
const aiLongText = [
  `AI 索引链路验证文档（锚点：${aiAnchor}）`,
  '一、适用范围：本文件用于验证上传文档能否进入 ai-rag 的检索索引。',
  '二、工艺要求：阳极氧化槽液温度控制在 20±2 摄氏度，pH 值维持 4.2 至 4.6。',
  '三、装配步骤：定位工装后按 25 牛米力矩拧紧，随后进行气密性检测并记录泄漏率。',
  '四、检验标准：外观无磕碰划伤，绝缘电阻不低于 500 兆欧，接地连续性小于 0.1 欧姆。',
  '五、记录要求：每批次填写装配记录表并归档，异常情况须在四小时内上报工艺工程师。',
  '六、检索验证：以锚点检索本文件时应能命中，说明切分、嵌入与索引链路均已走通。',
].join('\n')
const aiLongName = 'AI索引链路验证-工艺说明.txt'
writeFileSync(join(outDir, aiLongName), Buffer.from(aiLongText, 'utf8'))
generated.push({ path: join(outDir, aiLongName), name: aiLongName, role: '知识文档', sizeBytes: Buffer.byteLength(aiLongText, 'utf8') })


console.log(`生成 ${generated.length} 个 mock 文件到 ${outDir}`)
for (const g of generated) console.log(`  - ${g.name} (${g.sizeBytes} bytes, ${g.role})`)
