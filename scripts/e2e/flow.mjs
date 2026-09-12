#!/usr/bin/env node
/**
 * flow.mjs — 从 0 到 尾的全业务 E2E 全流程脚本（API 级，Node 18+ 无第三方依赖）
 *
 * 覆盖（对应真实业务闭环）：
 *   阶段 0  健康检查 + 字典基线
 *   阶段 1  测试 fixture 文件上传（PDF / X_T / TXT / PNG → POST /uploads/files）
 *   阶段 2  资产生命周期（建草稿 → 提交 → 待整理）
 *   阶段 3  治理扫描（手动触发 → 运行成功 → 问题池）
 *   阶段 4  治理闭环（建任务 → 计划 → 启动 → 执行 → 业务确认 → 质量验收 → 正式应用 → 任务完成）
 *   阶段 4b 自有资产治理闭环（指派责任人 → 扫描 → 任务 → 确认 → 验收 → 正式应用）
 *   阶段 5  知识文档（建草稿 → 发布 → 检索）
 *   阶段 6  资产文档关联（双向）
 *   阶段 7  收藏 + 评论 + 点赞
 *   阶段 8  文件下载 / 预览 / 打包下载
 *   阶段 9  统一检索（资产 + 文档同框命中）
 *   阶段 10 AI 索引链路（长文上传 → 入库 → 可检索；ai-rag 不可达时跳过并告警）
 *   阶段 11 前端冒烟（可选，需 --frontend）
 *   阶段 12 治理授权闸门（匿名读写治理数据必须被拒；D-006 回归）
 *
 * 用法：
 *   node flow.mjs --backend http://127.0.0.1:8080 [--frontend http://127.0.0.1:5173]
 *                    [--rag http://localhost:8000] [--allow-no-rag]
 *
 * 阶段 10（AI 索引链路）需要 ai-rag 的 rag 服务在跑（默认 http://localhost:8000，
 * 服务密钥取 RAG_API_KEY / AI_CAPABILITY_API_KEY）。**ai-rag 不可达时该阶段默认判失败**
 * —— 跳过会复现它要防的"上传内容没进索引却全绿"的静默失效。确需跳过请显式加
 * --allow-no-rag（或设 E2E_ALLOW_NO_RAG=1）。
 *
 * 退出码：全部通过 = 0；任一断言失败 = 1（失败会继续跑完，最终汇总）。
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const MOCK_DIR = join(__dirname, '.mock-files')

/* ------------------------------------------------------------------ */
/* 命令行参数                                                           */
/* ------------------------------------------------------------------ */
const args = new Map()
for (let i = 2; i < process.argv.length; i++) {
  const arg = process.argv[i]
  if (arg.startsWith('--')) args.set(arg.slice(2), process.argv[i + 1])
}
const BACKEND = (args.get('backend') || 'http://127.0.0.1:8080').replace(/\/$/, '')
const FRONTEND = args.get('frontend')
// AI 能力服务（ai-rag）：阶段 10 直接查它的检索接口，以断言上传内容真的进了索引。
// 与 ep 后端用的是同一个服务（ep 的 ai.capability.base-url），此处独立配置以便直连断言。
const RAG = (args.get('rag') || process.env.AI_CAPABILITY_BASE_URL || 'http://localhost:8000').replace(/\/$/, '')
const RAG_KEY = process.env.RAG_API_KEY || process.env.AI_CAPABILITY_API_KEY || ''
const RAG_NAMESPACE = process.env.AI_CAPABILITY_NAMESPACE || 'ep-docs'
const RUN_TOKEN = process.env.E2E_RUN_ID || new Date().toISOString().replace(/\D/g, '').slice(0, 14)
const ASSET_NUMBER = `E2E-H03-${RUN_TOKEN}`
const FIELD_ASSET_NUMBER = `E2E-FIELD-${RUN_TOKEN}`
const RESULT_JSON = args.get('result-json') || process.env.E2E_RESULT_JSON
  || join(__dirname, '.logs', `e2e-result-${RUN_TOKEN}.json`)

/* ------------------------------------------------------------------ */
/* 轻量测试框架                                                         */
/* ------------------------------------------------------------------ */
let passed = 0
let failed = 0
const failures = []
const cases = []
let currentModule = '基础检查'

async function step(name, fn) {
  const result = {
    id: `E2E-${String(cases.length + 1).padStart(3, '0')}`,
    module: currentModule,
    name,
    type: name.includes('前端') ? '浏览器' : 'API',
    startedAt: new Date().toISOString(),
  }
  try {
    await fn()
    passed++
    result.status = 'PASS'
    result.actual = '接口返回与断言一致'
    console.log(`  ✅ ${name}`)
  } catch (error) {
    failed++
    const message = error instanceof Error ? error.message : String(error)
    failures.push({ name, message })
    result.status = 'FAIL'
    result.actual = message
    console.log(`  ❌ ${name}\n      ↳ ${message}`)
  }
  result.finishedAt = new Date().toISOString()
  cases.push(result)
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

function assertEqual(actual, expected, message) {
  if (actual !== expected) {
    throw new Error(`${message}（期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}）`)
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/* ------------------------------------------------------------------ */
/* API 封装（JSON + multipart）                                         */
/* ------------------------------------------------------------------ */
async function api(path, { method = 'GET', body, headers = {}, form } = {}) {
  const init = { method, headers: { Accept: 'application/json', ...headers } }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  if (form) init.body = form // FormData 时不要手动设置 Content-Type
  if (sessionCookie) init.headers.Cookie = sessionCookie // S1：写操作需真实会话
  const response = await fetch(`${BACKEND}${path}`, init)
  const text = await response.text()
  let json = null
  if (text) {
    try { json = JSON.parse(text) } catch { /* 非 JSON 响应（如文件下载） */ }
  }
  if (!response.ok) {
    const message = json?.error?.message || json?.message || response.statusText
    throw new Error(`${method} ${path} → ${response.status}: ${message}`)
  }
  return { status: response.status, ok: response.ok, json, text, headers: response.headers }
}

/**
 * 探测型请求：可显式选择是否携带会话，且**不因 4xx 抛错**——4xx 正是授权用例要断言的结果。
 *
 * <p>与 api() 的分工：api() 恒带会话、非 2xx 即抛；probe() 用于「验证某身份被拒绝」。
 * 不这样做就只能用 try/catch 包住 api()，把正常断言写成异常路径。
 */
async function probe(path, { method = 'GET', body, session = false } = {}) {
  const init = { method, headers: { Accept: 'application/json' } }
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json'
    init.body = JSON.stringify(body)
  }
  if (session && sessionCookie) init.headers.Cookie = sessionCookie
  const response = await fetch(`${BACKEND}${path}`, init)
  const text = await response.text()
  let json = null
  if (text) {
    try { json = JSON.parse(text) } catch { /* 非 JSON 响应 */ }
  }
  return { status: response.status, json, text }
}

const CONTENT_TYPES = {
  pdf: 'application/pdf',
  png: 'image/png',
  txt: 'text/plain; charset=utf-8',
  x_t: 'application/octet-stream',
  step: 'application/octet-stream',
}

async function uploadFile(fileName) {
  const bytes = readFileSync(join(MOCK_DIR, fileName))
  const ext = fileName.split('.').pop().toLowerCase()
  const contentType = CONTENT_TYPES[ext] || 'application/octet-stream'
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: contentType }), fileName)
  const res = await api('/api/v1/uploads/files', { method: 'POST', form })
  assertEqual(res.status, 201, `上传 ${fileName} 应返回 201`)
  const file = res.json?.file
  assert(file?.storageKey, `${fileName} 应返回 storageKey`)
  assert(file?.contentSha256, `${fileName} 应返回 contentSha256`)
  assertEqual(file?.name, fileName, `文件 name 回显`)
  return file
}

function poll(fn, { timeoutMs = 15000, intervalMs = 500, label = '轮询' } = {}) {
  const start = Date.now()
  return new Promise((resolve, reject) => {
    const tick = async () => {
      try {
        const done = await fn()
        if (done) return resolve(done)
      } catch (error) {
        if (Date.now() - start >= timeoutMs) return reject(new Error(`${label} 出错：${error.message}`))
        return setTimeout(tick, intervalMs)
      }
      if (Date.now() - start >= timeoutMs) return reject(new Error(`${label} 超时`))
      setTimeout(tick, intervalMs)
    }
    tick()
  })
}

/* ------------------------------------------------------------------ */
/* 会话（S1 匿名写收紧）：写操作需真实登录会话                          */
/* ------------------------------------------------------------------ */
let sessionCookie = ''

/**
 * 以真实库内账号登录并携带会话 Cookie。
 *
 * 账号来自真实表 sys_user（默认引导管理员 admin，见 docs/local-development.md），
 * 可用 E2E_USER_ID / E2E_PASSWORD 覆盖；已不存在任何硬编码演示账号。
 * 用系统管理员（SYSTEM_ADMIN + CONTENT_ADMIN）驱动全流程，
 * 可覆盖需要治理/系统管理角色的所有端点；接口 body 中的 actorUserId 仍按原业务语义填写。
 */
const E2E_USER_ID = process.env.E2E_USER_ID || 'admin'
const E2E_PASSWORD = process.env.E2E_PASSWORD

async function loginAs(userId = E2E_USER_ID, password = E2E_PASSWORD) {
  if (!password) throw new Error('缺少 E2E_PASSWORD，请在仓库根目录 .env.local 中配置真实测试账号密码')
  const response = await fetch(`${BACKEND}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId, password }),
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`登录 ${userId} 失败（${response.status}）：${text}`)
  }
  const setCookie = response.headers.get('set-cookie')
  sessionCookie = (setCookie ? setCookie.split(';')[0] : '').trim()
  if (!sessionCookie) throw new Error(`登录 ${userId} 未返回会话 Cookie`)
}

/* ------------------------------------------------------------------ */
/* 主流程                                                               */
/* ------------------------------------------------------------------ */
console.log(`\n=== E2E 全流程开始  backend=${BACKEND}${FRONTEND ? ` frontend=${FRONTEND}` : ''} ===\n`)
await loginAs()
console.log(`  会话：${E2E_USER_ID} 已登录（S1 写操作需真实会话）`)

/* ---- 阶段 0：健康检查 + 字典基线 ---- */
currentModule = '基础检查'
console.log('【阶段 0】健康检查 + 字典基线')
const health = await api('/actuator/health')
await step('后端健康检查 /actuator/health → UP', () => {
  assertEqual(health.json?.status, 'UP', '健康状态')
  assertEqual(health.status, 200, 'HTTP 状态')
})

const categories = await api('/api/v1/dictionaries/categories')
await step('字典分类存在（ASSET_TYPE / SPECIALTY / DOCUMENT_CATEGORY）', () => {
  assertEqual(categories.status, 200, 'HTTP 状态')
  const codes = new Set(categories.json.map((item) => item.code))
  for (const code of ['ASSET_TYPE', 'SPECIALTY', 'DOCUMENT_CATEGORY']) {
    assert(codes.has(code), `缺少字典分类 ${code}`)
  }
})

const dictItems = await api('/api/v1/dictionaries/items')
const SPECIALTY_ITEM = dictItems.json.find((item) => item.category === 'SPECIALTY' && item.name === '机械')
const MECHANICAL_ID = SPECIALTY_ITEM?.id
await step('字典项可用（SPECIALTY=机械 / ASSET_TYPE=MIXED_ASSET / DOC_CATEGORY=WORK_INSTRUCTION）', () => {
  assert(SPECIALTY_ITEM, '缺少 SPECIALTY 字典项「机械」')
  assert(dictItems.json.some((item) => item.category === 'ASSET_TYPE' && item.code === 'MIXED_ASSET'), '缺少 ASSET_TYPE=MIXED_ASSET')
  assert(dictItems.json.some((item) => item.category === 'DOCUMENT_CATEGORY' && item.code === 'WORK_INSTRUCTION'), '缺少 DOCUMENT_CATEGORY=WORK_INSTRUCTION')
})

let governanceStandard
await step('确保 E2E 数据标准已通过真实 API 启用', async () => {
  const code = 'E2E-FIELD-COMPLETENESS'
  const standards = await api('/api/v1/governance/standards')
  governanceStandard = standards.json.find((item) => item.standardCode === code && item.status === 'ENABLED')
  if (governanceStandard) return

  const versions = standards.json.filter((item) => item.standardCode === code)
  const nextVersion = Math.max(0, ...versions.map((item) => item.standardVersion)) + 1
  const created = await api('/api/v1/governance/standards', {
    method: 'POST',
    body: {
      standardCode: code,
      standardVersion: nextVersion,
      name: 'E2E 资产字段完整性标准',
      applicableAssetTypes: ['MIXED_ASSET'],
      ownerUserId: E2E_USER_ID,
      ownerName: '系统管理员',
      changeSummary: '真实数据库 E2E 治理基线',
      rules: [
        { targetField: 'description', ruleType: 'REQUIRED', description: '功能说明必填', blocking: true, configurationJson: '{}' },
        { targetField: 'specialties', ruleType: 'REQUIRED', description: '专业类别必填', blocking: true, configurationJson: '{}' },
        { targetField: 'scope', ruleType: 'REQUIRED', description: '完整适用范围必填', blocking: true, configurationJson: '{}' },
      ],
    },
  })
  const enabled = await api(`/api/v1/governance/standards/${created.json.id}/enable`, {
    method: 'POST',
    body: { version: created.json.version },
  })
  governanceStandard = enabled.json.standard
  assertEqual(governanceStandard.status, 'ENABLED', 'E2E 数据标准状态')
})

/* ---- 阶段 1：测试文件上传（文件内容来自本地 E2E fixture） ---- */
currentModule = '文件上传'
console.log('\n【阶段 1】测试文件上传（本地 fixture → 真实上传接口）')
const assetFileNames = [
  '宁德-H03-电池包-三维源模型.x_t',
  '宁德-H03-电池包-总成图.pdf',
  '宁德-H03-电池包-安装示意图.pdf',
  '宁德-H03-电池包-装配说明书.txt',
]
const assetFiles = []
for (const name of assetFileNames) {
  await step(`上传资产文件 ${name}`, async () => {
    const file = await uploadFile(name)
    assetFiles.push(file)
    assertEqual(file.sizeBytes > 0, true, 'sizeBytes > 0')
    assertEqual(file.role.length > 0, true, '应推导出文件角色')
  })
}

let docFile
await step('上传文档文件 作业指导书-H03-电池包装配.pdf', async () => {
  const file = await uploadFile('作业指导书-H03-电池包装配.pdf')
  docFile = file
  assertEqual(file.previewable, true, 'PDF 应可预览')
})

/* ---- 阶段 2：资产生命周期 ---- */
currentModule = '资产生命周期'
console.log('\n【阶段 2】资产生命周期（草稿 → 提交 → 待整理）')
let assetA
await step('创建资产草稿 POST /assets/drafts', async () => {
  const body = {
    assetNumber: ASSET_NUMBER,
    name: `E2E 电池包总成数模 ${RUN_TOKEN}`,
    description: '用于端到端自动化测试的电池包总成数模资产。',
    assetType: 'MIXED_ASSET',
    specialties: ['机械', '工装'],
    tags: [],
    moduleTags: [],
    standardEquipmentModule: false,
    linkedModuleAssetIds: [],
    equipmentInterconnectCode: '',
    scopes: [{
      // 完整但不属于治理有效适用范围，扫描会标记 INVALID_SCOPE，供自有资产治理闭环修复
      platform: '乘用车',
      productLine: 'P02',
      base: '宁德基地',
      productionLine: 'A 拉线',
      processSection: '',
      platformFamily: '乘用车',
      platformVariant: '底部水冷',
    }],
    files: assetFiles.map((file, index) => ({ ...file, primary: index === 0 })),
    ownerName: '陈工',
    ownerDepartment: '设备工程部',
  }
  const res = await api('/api/v1/assets/drafts', { method: 'POST', body })
  assertEqual(res.status, 201, '创建草稿应返回 201')
  assetA = res.json
  assertEqual(assetA.status, 'DRAFT', '草稿状态应为 DRAFT')
  assertEqual(assetA.files.length, assetFiles.length, '草稿应含全部上传文件')
  assertEqual(assetA.assetNumber, ASSET_NUMBER, '资料编号回显')
})

await step('提交资产 POST /assets/{id}/submit → 待整理', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/submit`, { method: 'POST' })
  assertEqual(res.status, 200, '提交应返回 200')
  assertEqual(res.json.status, 'PENDING_CURATION', '提交后应进入待整理')
})

await step('查询资产详情 GET /assets/{id}', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.name, `E2E 电池包总成数模 ${RUN_TOKEN}`, '资产名称')
  assertEqual(res.json.scopes[0].base, '宁德基地', '适用范围命中')
})

let fieldAsset
await step('创建字段治理草稿（缺说明与专业）', async () => {
  const res = await api('/api/v1/assets/drafts', {
    method: 'POST',
    body: {
      assetNumber: FIELD_ASSET_NUMBER,
      name: `E2E 字段治理资产 ${RUN_TOKEN}`,
      description: '',
      assetType: 'MIXED_ASSET',
      specialties: [],
      tags: [],
      moduleTags: [],
      standardEquipmentModule: false,
      linkedModuleAssetIds: [],
      equipmentInterconnectCode: '',
      scopes: [{
        platform: '乘用车', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线',
        processSection: '焊接段', platformFamily: '乘用车', platformVariant: '底部水冷',
      }],
      files: [assetFiles[0]].map((file) => ({ ...file, primary: true })),
      ownerName: '系统管理员',
      ownerDepartment: '信息化部',
    },
  })
  assertEqual(res.status, 201, '创建字段治理草稿应返回 201')
  fieldAsset = res.json
  assertEqual(fieldAsset.status, 'DRAFT', '字段治理资产应保持草稿')
})
await step('为字段治理资产指派真实责任人 user1', async () => {
  const res = await api(`/api/v1/governance/asset-responsibilities/${fieldAsset.id}`, {
    method: 'PUT',
    body: { responsibleUserId: 'user1', responsibilityScope: '测试部' },
  })
  assertEqual(res.status, 200, '责任人指派应返回 200')
  assertEqual(res.json.responsibleUserId, 'user1', '字段治理责任人应为 user1')
})

/* ---- 阶段 3：治理扫描 ---- */
currentModule = '治理扫描'
console.log('\n【阶段 3】治理扫描（手动触发 → 问题池）')
let scanRun
await step('触发手动扫描 POST /governance/scans', async () => {
  const res = await api('/api/v1/governance/scans', { method: 'POST' })
  assertEqual(res.status, 202, '扫描应返回 202 Accepted')
  scanRun = res.json
  assert(scanRun.id, '应返回扫描运行 id')
})
await step('扫描运行成功（轮询至 SUCCEEDED）', async () => {
  const result = await poll(async () => {
    const res = await api(`/api/v1/governance/scans/${scanRun.id}`)
    return res.json?.status === 'SUCCEEDED' || res.json?.status === 'FAILED' ? res.json : null
  }, { label: '扫描运行状态' })
  assertEqual(result.status, 'SUCCEEDED', '扫描运行状态')
})

/* ---- 阶段 4：治理闭环（正式流程） ---- */
currentModule = '治理闭环'
console.log('\n【阶段 4】治理闭环（任务 → 计划 → 启动 → 执行 → 确认 → 验收 → 正式应用）')
let taskId
let taskVersion = 0
let fieldIssueIds = []
await step('问题池存在本次字段治理问题', async () => {
  const res = await api(`/api/v1/governance/issues?assetId=${fieldAsset.id}&status=OPEN`)
  assertEqual(res.status, 200, 'HTTP 状态')
  const fieldIssues = res.json.filter((issue) => ['DESCRIPTION', 'SPECIALTIES'].includes(issue.targetField))
  fieldIssueIds = fieldIssues.map((issue) => issue.id)
  assertEqual(fieldIssueIds.length, 2, '应产生 DESCRIPTION 与 SPECIALTIES 两个问题')
})

await step('创建治理任务 POST /governance/tasks', async () => {
  const res = await api('/api/v1/governance/tasks', {
    method: 'POST',
    body: { name: `E2E 字段治理闭环 ${RUN_TOKEN}`, issueIds: fieldIssueIds, ownerUserId: 'user1', ownerName: '普通用户', dueDate: '2026-09-30' },
  })
  assertEqual(res.status, 201, '创建任务应返回 201')
  taskId = res.json.id
  taskVersion = res.json.version
  assertEqual(res.json.status, 'DRAFT', '初始应为草稿')
})

await step('新增计划项 POST /governance/tasks/{id}/plans', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/plans`, {
    method: 'POST',
    body: {
      title: 'E2E 字段治理计划', plannedStart: '2026-09-01', plannedEnd: '2026-09-15',
      assigneeId: 'user1', responsibleUserId: 'user1',
      dependencyIds: [], issueIds: fieldIssueIds,
    },
  })
  assertEqual(res.status, 201, '创建计划应返回 201')
  assert(res.json.id, '计划应有 id')
})

await step('启动任务 POST /governance/tasks/{id}/start（计划锁定）', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/start`, {
    method: 'POST',
    body: { version: taskVersion, actorUserId: E2E_USER_ID },
  })
  assertEqual(res.status, 200, '启动应返回 200')
  assertEqual(res.json.status, 'IN_PROGRESS', '启动后应进行中')
  taskVersion = res.json.version
})

const GOV_HEADERS = {}
let items = []
await step('读取治理项 GET /governance/tasks/{id}/items', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/items`, { headers: GOV_HEADERS })
  assertEqual(res.status, 200, '读取治理项应返回 200')
  items = res.json
  assertEqual(items.length, 2, '应有 2 个治理项')
  const fields = items.map((item) => item.item.targetField).sort()
  assertEqual(fields.join(','), 'DESCRIPTION,SPECIALTIES', '治理项字段覆盖')
})

await step('执行治理：保存草稿 + 提交治理结果（DESCRIPTION / SPECIALTIES）', async () => {
  for (const item of items) {
    const field = item.item.targetField
    const proposed = field === 'DESCRIPTION'
      ? { description: 'E2E 补充：电池包总成功能说明' }
      : { specialtyItemIds: [MECHANICAL_ID] }
    const draftRes = await api(`/api/v1/governance/items/${item.item.id}/result-draft`, {
      method: 'PUT',
      body: { itemVersion: item.item.version, assetVersion: item.item.assetVersion, proposedValue: proposed, actorUserId: E2E_USER_ID },
      headers: GOV_HEADERS,
    })
    assertEqual(draftRes.status, 200, `保存 ${field} 治理结果草稿`)
    const submitRes = await api(`/api/v1/governance/items/${item.item.id}/submit`, {
      method: 'POST',
      body: { resultVersionId: draftRes.json.id, resultVersion: draftRes.json.version, actorUserId: E2E_USER_ID },
      headers: GOV_HEADERS,
    })
    assertEqual(submitRes.status, 200, `提交 ${field} 治理结果`)
  }
})

await step('提交业务确认 POST /governance/tasks/{id}/submit-for-confirmation', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/submit-for-confirmation`, {
    method: 'POST',
    body: { version: taskVersion },
  })
  assertEqual(res.status, 200, '提交确认应返回 200')
  assertEqual(res.json.status, 'PENDING_CONFIRMATION', '进入待业务确认')
  taskVersion = res.json.version
})

let roundId = 0
let roundVersion = 0
await step('读取业务确认轮次 GET .../confirmation-rounds/current', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/confirmation-rounds/current`, { headers: GOV_HEADERS })
  assertEqual(res.status, 200, '读取确认轮次应返回 200')
  roundId = res.json.round.id
  roundVersion = res.json.round.version
  assert(res.json.items.length >= 1, '确认轮次应有确认项')
})

await step('逐项业务确认通过 PUT .../decision (APPROVED)', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/confirmation-rounds/current`, { headers: GOV_HEADERS })
  for (const item of res.json.items) {
    const decisionRes = await api(`/api/v1/governance/confirmation-rounds/${roundId}/items/${item.itemId}/decision`, {
      method: 'PUT',
      body: { decision: 'APPROVED', comment: '', decisionVersion: 0, confirmerUserId: 'user1' },
      headers: GOV_HEADERS,
    })
    assertEqual(decisionRes.status, 200, `确认项 ${item.itemId} 审批通过`)
  }
})

await step('完成业务确认轮次 POST .../confirmation-rounds/{roundId}/complete', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/confirmation-rounds/${roundId}/complete`, {
    method: 'POST',
    body: { roundVersion },
    headers: GOV_HEADERS,
  })
  assertEqual(res.status, 200, '完成确认轮次应返回 200')
  assertEqual(res.json.taskStatus, 'PENDING_ACCEPTANCE', '进入待质量验收')
})

let acceptanceRoundId = 0
let acceptanceRoundVersion = 0
let sampleItemId = 0
let sampleVersion = 0
await step('读取质量验收轮次 GET .../acceptance-rounds/current（自动固定抽样）', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/acceptance-rounds/current`, { headers: GOV_HEADERS })
  assertEqual(res.status, 200, '读取验收轮次应返回 200')
  acceptanceRoundId = res.json.id
  acceptanceRoundVersion = res.json.version
  assert(res.json.samples.length >= 1, '验收轮次应有固定抽样样本')
  sampleItemId = res.json.samples[0].itemId
  sampleVersion = res.json.samples[0].version
})

await step('抽样验收通过 PUT .../acceptance-rounds/{roundId}/samples/{itemId}', async () => {
  const res = await api(`/api/v1/governance/acceptance-rounds/${acceptanceRoundId}/samples/${sampleItemId}`, {
    method: 'PUT',
    body: { passed: true, issueDescription: '', reviewerUserId: 'qa-1', sampleVersion },
    headers: GOV_HEADERS,
  })
  assertEqual(res.status, 200, '抽样验收应返回 200')
})

let applicationJobId = 0
await step('完成质量验收 POST .../acceptance-rounds/{roundId}/complete（生成正式应用作业）', async () => {
  // 保存抽样会推进轮次版本，重新读取当前轮次拿到最新版本（幂等只读）
  const current = await api(`/api/v1/governance/tasks/${taskId}/acceptance-rounds/current`, { headers: GOV_HEADERS })
  const freshVersion = current.json.version
  const res = await api(`/api/v1/governance/tasks/${taskId}/acceptance-rounds/${acceptanceRoundId}/complete`, {
    method: 'POST',
    body: { roundVersion: freshVersion, operatorUserId: 'qa-1' },
    headers: GOV_HEADERS,
  })
  assertEqual(res.status, 200, '完成验收应返回 200')
  applicationJobId = res.json.applicationJobId
  assert(applicationJobId > 0, '应生成正式应用作业 id')
})

await step('正式应用作业执行成功（轮询至完成）', async () => {
  const job = await poll(async () => {
    const res = await api(`/api/v1/governance/jobs/${applicationJobId}`)
    const j = res.json
    return j.succeeded === j.total && j.processing === 0 && j.failed === 0 ? j : null
  }, { label: '正式应用作业', timeoutMs: 20000 })
  assertEqual(job.succeeded, job.total, '成功项 = 总数')
  assert(job.succeeded >= 1, '至少 1 项正式应用成功')
  assertEqual(job.retryable, false, '作业不应可重试')
})

await step('治理任务完成 GET /governance/tasks/{id} → COMPLETED', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.status, 'COMPLETED', '治理任务应已完成')
})

await step('本次字段治理问题已解决', async () => {
  const res = await api(`/api/v1/governance/issues?assetId=${fieldAsset.id}&status=RESOLVED`)
  const ids = new Set(res.json.map((issue) => issue.id))
  assert(fieldIssueIds.every((id) => ids.has(id)), '本次字段问题应全部解决')
})

/* ---- 阶段 4b：自有资产治理闭环（责任人指派 → 扫描 → 闭环） ---- */
currentModule = '自有资产治理'
console.log('\n【阶段 4b】自有资产治理闭环（责任人指派 → 扫描 → 闭环）')
const RESP_ADMIN_HEADERS = { 'X-User-Roles': 'CONTENT_ADMIN,SYSTEM_ADMIN' }
await step('指派资产责任人 PUT /governance/asset-responsibilities/{assetId}', async () => {
  const res = await api(`/api/v1/governance/asset-responsibilities/${assetA.id}`, {
    method: 'PUT',
    body: { responsibleUserId: 'user2', responsibilityScope: '测试部' },
    headers: RESP_ADMIN_HEADERS,
  })
  assertEqual(res.status, 200, '指派应返回 200')
  assertEqual(res.json.responsibleUserId, 'user2', '责任人应为 user2')
  assertEqual(res.json.responsibilityScope, '测试部', '责任范围')
})
await step('读取资产责任人 GET /governance/asset-responsibilities/{assetId}', async () => {
  const res = await api(`/api/v1/governance/asset-responsibilities/${assetA.id}`, { headers: RESP_ADMIN_HEADERS })
  assertEqual(res.status, 200, '读取应返回 200')
  assertEqual(res.json.responsibleUserId, 'user2', '当前有效责任人')
})

let ownIssueId = 0
await step('自有资产扫描产生 SCOPE 问题', async () => {
  const issuesRes = await api(`/api/v1/governance/issues?assetId=${assetA.id}&status=OPEN`)
  const scopeIssue = issuesRes.json.find((issue) => issue.targetField === 'SCOPE')
  assert(scopeIssue, '应存在 SCOPE 开放问题')
  ownIssueId = scopeIssue.id
})

let ownTaskId = 0
let ownTaskVersion = 0
await step('为自有资产创建治理任务 POST /governance/tasks', async () => {
  const res = await api('/api/v1/governance/tasks', {
    method: 'POST',
    body: { name: `E2E 自有资产范围治理 ${RUN_TOKEN}`, issueIds: [ownIssueId], ownerUserId: 'user2', ownerName: '上传用户', dueDate: '2026-09-30' },
  })
  assertEqual(res.status, 201, '创建任务应返回 201')
  ownTaskId = res.json.id
  ownTaskVersion = res.json.version
  assertEqual(res.json.status, 'DRAFT', '初始为草稿')
})
await step('自有资产任务新增计划 POST /governance/tasks/{id}/plans', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/plans`, {
    method: 'POST',
    body: {
      title: 'E2E 范围修正计划', plannedStart: '2026-09-01', plannedEnd: '2026-09-15',
      assigneeId: 'user2', responsibleUserId: 'user2',
      dependencyIds: [], issueIds: [ownIssueId],
    },
  })
  assertEqual(res.status, 201, '新增计划应返回 201')
})
await step('自有资产任务启动 POST /governance/tasks/{id}/start', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/start`, {
    method: 'POST', body: { version: ownTaskVersion, actorUserId: E2E_USER_ID },
  })
  assertEqual(res.status, 200, '启动应返回 200')
  assertEqual(res.json.status, 'IN_PROGRESS', '进行中')
  ownTaskVersion = res.json.version
})

const OWN_HEADERS = {}
let ownItemId = 0
let ownAssetVersion = 0
await step('读取自有资产治理项 GET .../items', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/items`, { headers: OWN_HEADERS })
  assertEqual(res.status, 200, '读取治理项应返回 200')
  assertEqual(res.json.length, 1, '应有 1 个治理项')
  ownItemId = res.json[0].item.id
  ownAssetVersion = res.json[0].item.assetVersion
})
await step('修正适用范围：保存草稿 + 提交', async () => {
  const proposed = {
    scopes: [{ platformFamily: '乘用车', platformVariant: '底部水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段' }],
  }
  const draftRes = await api(`/api/v1/governance/items/${ownItemId}/result-draft`, {
    method: 'PUT',
    body: { itemVersion: 0, assetVersion: ownAssetVersion, proposedValue: proposed, actorUserId: E2E_USER_ID },
    headers: OWN_HEADERS,
  })
  assertEqual(draftRes.status, 200, '保存范围治理结果草稿')
  const submitRes = await api(`/api/v1/governance/items/${ownItemId}/submit`, {
    method: 'POST',
    body: { resultVersionId: draftRes.json.id, resultVersion: draftRes.json.version, actorUserId: E2E_USER_ID },
    headers: OWN_HEADERS,
  })
  assertEqual(submitRes.status, 200, '提交范围治理结果')
})
await step('自有资产提交业务确认 POST .../submit-for-confirmation', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/submit-for-confirmation`, {
    method: 'POST', body: { version: ownTaskVersion },
  })
  assertEqual(res.status, 200, '提交确认应返回 200')
  assertEqual(res.json.status, 'PENDING_CONFIRMATION', '进入待业务确认')
  ownTaskVersion = res.json.version
})
let ownRoundId = 0
let ownRoundVersion = 0
await step('自有资产读取确认轮次（以责任人为确认人）', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/confirmation-rounds/current`, { headers: OWN_HEADERS })
  assertEqual(res.status, 200, '读取确认轮次应返回 200')
  ownRoundId = res.json.round.id
  ownRoundVersion = res.json.round.version
  assert(res.json.items.length >= 1, '确认轮次应有确认项')
})
await step('自有资产逐项确认通过', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/confirmation-rounds/current`, { headers: OWN_HEADERS })
  for (const item of res.json.items) {
    const decisionRes = await api(`/api/v1/governance/confirmation-rounds/${ownRoundId}/items/${item.itemId}/decision`, {
      method: 'PUT',
      body: { decision: 'APPROVED', comment: '', decisionVersion: 0, confirmerUserId: 'user2' },
      headers: OWN_HEADERS,
    })
    assertEqual(decisionRes.status, 200, `确认项 ${item.itemId} 审批通过`)
  }
})
await step('自有资产完成确认轮次 POST .../confirmation-rounds/{roundId}/complete', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/confirmation-rounds/${ownRoundId}/complete`, {
    method: 'POST', body: { roundVersion: ownRoundVersion }, headers: OWN_HEADERS,
  })
  assertEqual(res.status, 200, '完成确认轮次应返回 200')
  assertEqual(res.json.taskStatus, 'PENDING_ACCEPTANCE', '进入待质量验收')
})
let ownAcceptRoundId = 0
let ownSampleId = 0
let ownSampleVersion = 0
await step('自有资产读取验收轮次 GET .../acceptance-rounds/current', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/acceptance-rounds/current`, { headers: RESP_ADMIN_HEADERS })
  assertEqual(res.status, 200, '读取验收轮次应返回 200')
  ownAcceptRoundId = res.json.id
  assert(res.json.samples.length >= 1, '验收轮次应有固定抽样样本')
  ownSampleId = res.json.samples[0].itemId
  ownSampleVersion = res.json.samples[0].version
})
await step('自有资产抽样验收通过 PUT .../samples/{itemId}', async () => {
  const res = await api(`/api/v1/governance/acceptance-rounds/${ownAcceptRoundId}/samples/${ownSampleId}`, {
    method: 'PUT',
    body: { passed: true, issueDescription: '', reviewerUserId: 'qa-1', sampleVersion: ownSampleVersion },
    headers: RESP_ADMIN_HEADERS,
  })
  assertEqual(res.status, 200, '抽样验收应返回 200')
})
let ownJobId = 0
await step('自有资产完成验收 POST .../acceptance-rounds/{roundId}/complete（正式应用）', async () => {
  const current = await api(`/api/v1/governance/tasks/${ownTaskId}/acceptance-rounds/current`, { headers: RESP_ADMIN_HEADERS })
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/acceptance-rounds/${ownAcceptRoundId}/complete`, {
    method: 'POST', body: { roundVersion: current.json.version, operatorUserId: 'qa-1' }, headers: RESP_ADMIN_HEADERS,
  })
  assertEqual(res.status, 200, '完成验收应返回 200')
  ownJobId = res.json.applicationJobId
  assert(ownJobId > 0, '应生成正式应用作业 id')
})
await step('自有资产正式应用作业完成', async () => {
  const job = await poll(async () => {
    const res = await api(`/api/v1/governance/jobs/${ownJobId}`)
    const j = res.json
    return j.succeeded === j.total && j.processing === 0 && j.failed === 0 ? j : null
  }, { label: '自有资产正式应用作业', timeoutMs: 20000 })
  assertEqual(job.succeeded, job.total, '成功项 = 总数')
  assert(job.succeeded >= 1, '至少 1 项正式应用成功')
  assertEqual(job.retryable, false, '作业不应可重试')
})
await step('自有资产治理任务完成 GET /governance/tasks/{id} → COMPLETED', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.status, 'COMPLETED', '治理任务应已完成')
})
await step('自有资产 SCOPE 问题已解决', async () => {
  const res = await api(`/api/v1/governance/issues?assetId=${assetA.id}&status=RESOLVED`)
  assert(res.json.some((issue) => issue.id === ownIssueId), 'SCOPE 问题应已解决')
})

/* ---- 阶段 5：知识文档 ---- */
currentModule = '知识文档'
console.log('\n【阶段 5】知识文档（草稿 → 发布 → 检索）')
let documentId
await step('创建文档草稿 POST /documents/drafts', async () => {
  const res = await api('/api/v1/documents/drafts', {
    method: 'POST',
    body: {
      documentNumber: '',
      title: 'E2E 电池包装配作业指导书',
      summary: '端到端自动化测试用的电池包装配作业指导。',
      categoryCode: 'WORK_INSTRUCTION',
      maintainerId: 'demo-user',
      maintainerName: '陈工',
      maintainerDepartment: '设备工程部',
      versionNumber: 'V1.0',
      changeSummary: '首次发布',
      files: [{ id: 0, name: docFile.name, format: docFile.format, sizeBytes: docFile.sizeBytes, previewable: docFile.previewable, storageKey: docFile.storageKey, contentSha256: docFile.contentSha256 }],
      scopeMode: 'GLOBAL',
      scopes: [],
    },
  })
  assertEqual(res.status, 201, '创建文档草稿应返回 201')
  documentId = res.json.id
  assertEqual(res.json.status, 'DRAFT', '文档初始为草稿')
})

await step('发布文档 POST /documents/{id}/publish', async () => {
  const res = await api(`/api/v1/documents/${documentId}/publish`, { method: 'POST' })
  assertEqual(res.status, 200, '发布应返回 200')
  assertEqual(res.json.status, 'PUBLISHED', '发布后应 PUBLISHED')
  assertEqual(res.json.currentVersion.files.length, 1, '当前有效版本应含 1 个文件')
})

await step('检索文档 GET /documents?q=', async () => {
  const res = await api(`/api/v1/documents?q=${encodeURIComponent('电池包装配作业指导书')}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assert(res.json.data.some((doc) => doc.id === documentId), '检索结果应命中发布文档')
})

/* ---- 阶段 6：资产文档关联 ---- */
currentModule = '资产文档关联'
console.log('\n【阶段 6】资产文档关联（双向）')
let relationId = 0
await step('建立关联 POST /asset-document-relations (APPLICABLE)', async () => {
  const res = await api('/api/v1/asset-document-relations', {
    method: 'POST',
    body: { assetId: assetA.id, documentId, relationType: 'APPLICABLE' },
  })
  assertEqual(res.status, 201, '建立关联应返回 201')
  relationId = res.json.id
  assertEqual(res.json.relationType, 'APPLICABLE', '关联类型')
})

await step('资产侧查询关联 GET /assets/{id}/documents', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/documents`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assert(res.json.some((item) => item.relation.id === relationId), '资产侧应看到该关联')
})

await step('文档侧查询关联 GET /documents/{id}/asset-relations', async () => {
  const res = await api(`/api/v1/documents/${documentId}/asset-relations`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assert(res.json.some((item) => item.relation.id === relationId), '文档侧应看到该关联')
})

/* ---- 阶段 7：收藏 / 评论 / 点赞 ---- */
currentModule = '协作'
console.log('\n【阶段 7】收藏 / 评论 / 点赞')
const COLLAB_HEADERS = { 'X-User-Id': 'e2e-user' }
await step('收藏资产 POST /assets/{id}/favorite', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/favorite`, { method: 'POST', headers: COLLAB_HEADERS })
  assertEqual(res.status, 200, '收藏应返回 200')
  assertEqual(res.json.favorited, true, '应标记已收藏')
})
await step('我的收藏 GET /favorites', async () => {
  const res = await api('/api/v1/favorites', { headers: COLLAB_HEADERS })
  assertEqual(res.status, 200, 'HTTP 状态')
  assert(res.json.some((asset) => asset.id === assetA.id), '收藏列表应含该资产')
})
let commentId = 0
await step('发布评论 POST /assets/{id}/comments', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/comments`, {
    method: 'POST',
    body: { authorName: '陈工', content: 'E2E 自动化测试评论', imageKeys: [] },
    headers: COLLAB_HEADERS,
  })
  assertEqual(res.status, 200, '发布评论应返回 200')
  commentId = res.json.id
  assertEqual(res.json.content, 'E2E 自动化测试评论', '评论内容回显')
})
await step('评论点赞 POST /assets/{id}/comments/{cid}/like', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/comments/${commentId}/like`, { method: 'POST', headers: COLLAB_HEADERS })
  assertEqual(res.status, 200, '点赞应返回 200')
  assertEqual(res.json.liked, true, '应标记已点赞')
})
await step('查询评论（含点赞状态）GET /assets/{id}/comments', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/comments`, { headers: COLLAB_HEADERS })
  assertEqual(res.status, 200, 'HTTP 状态')
  const comment = res.json.find((item) => item.id === commentId)
  assert(comment, '评论应存在')
  assertEqual(comment.likedByCurrentUser, true, '当前用户已点赞')
})

/* ---- 阶段 8：文件下载 / 预览 / 打包 ---- */
currentModule = '文件访问'
console.log('\n【阶段 8】文件下载 / 预览 / 打包下载')
// 上传响应中的 file.id 恒为 0，资产落库后才分配真实文件 id，故从资产详情取
const pdfFileId = assetA.files.find((file) => file.format === 'PDF')?.id
await step('预览 PDF 文件 GET /assets/{id}/files/{fileId}?preview=true', async () => {
  assert(pdfFileId > 0, '资产详情应含已分配 id 的 PDF 文件')
  const res = await api(`/api/v1/assets/${assetA.id}/files/${pdfFileId}?preview=true`)
  assertEqual(res.status, 200, '预览应返回 200')
  assertEqual(res.headers.get('content-type')?.includes('pdf'), true, 'Content-Type 应为 PDF')
  assertEqual(res.text.startsWith('%PDF'), true, '文件内容应为真实 PDF')
})
await step('打包下载 GET /assets/{id}/package → ZIP', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/package`)
  assertEqual(res.status, 200, '打包应返回 200')
  const zipMagic = res.text.slice(0, 4)
  assertEqual(zipMagic, 'PK\x03\x04', '应返回 ZIP 文件（PK 魔数）')
  assert(res.headers.get('content-disposition')?.includes('.zip'), '响应头应含 .zip 文件名')
})

/* ---- 阶段 9：统一检索 ---- */
currentModule = '统一检索'
console.log('\n【阶段 9】统一检索（资产 + 文档同框命中）')
await step('统一检索命中资产 GET /search?q=', async () => {
  const res = await api(`/api/v1/search?q=${encodeURIComponent('E2E 电池包总成数模')}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.assets.status, 'SUCCESS', '资产区状态')
  assert(res.json.assets.data.some((asset) => asset.id === assetA.id), '资产结果应命中')
})
await step('统一检索命中文档 GET /search?q=', async () => {
  const res = await api(`/api/v1/search?q=${encodeURIComponent('电池包装配作业指导书')}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.documents.status, 'SUCCESS', '文档区状态')
  assert(res.json.documents.data.some((doc) => doc.id === documentId), '文档结果应命中')
})

/* ---- 阶段 10：AI 索引链路 ---- */
// 目的：断言「上传 → 落库 → ai-rag 入库 → 可检索」真的走通。
//
// 为什么需要这一阶段：2026-09-12 实测发现，全流程 65 条全绿的同时，ai-rag 侧每一次
// ingest 都返回 200 但 chunk_count=0、ep-docs 语料量毫无变化 —— 因为**没有任何断言
// 看着这条链路**。一个"上传后搜索不到"的完全失效状态可以完美地通过整个 E2E。
//
// 两个要点：① 正文必须远超 ai-rag 的切分下限（MIN_CHUNK_SIZE=120 字符），否则切出 0 个块，
// 入库"成功"却检索不到；② 用唯一锚点在 ai-rag 里检索回来，证明内容确实进了索引，
// 而不只是接口被调用了。
const AI_ANCHOR = 'AI_INDEX_ANCHOR_7f3c9d2b'
const AI_FILE_NAME = 'AI索引链路验证-工艺说明.txt'

const ragReachable = await (async () => {
  try {
    const r = await fetch(`${RAG}/rag/health`, { headers: RAG_KEY ? { 'X-Service-Key': RAG_KEY } : {} })
    return r.status === 200
  } catch {
    return false
  }
})()

const ALLOW_NO_RAG = args.has('allow-no-rag') || process.env.E2E_ALLOW_NO_RAG === '1'

const ragProbe = await (async () => {
  try {
    const r = await fetch(`${RAG}/rag/health`, { headers: RAG_KEY ? { 'X-Service-Key': RAG_KEY } : {} })
    return { reachable: true, status: r.status }
  } catch {
    return { reachable: false, status: 0 }
  }
})()

if (!ragProbe.reachable || ragProbe.status !== 200) {
  currentModule = 'AI 索引链路'
  console.log('\n【阶段 10】AI 索引链路（长文上传 → 入库 → 可检索）')
  if (ALLOW_NO_RAG) {
    console.log(`  ⏭  已按 --allow-no-rag 显式跳过（ai-rag 不可用：${RAG}）—— 本次未验证 AI 索引链路`)
  } else {
    // 默认判失败而不是跳过：跳过会复现本阶段要防的那种"静默全绿"。
    // 注意区分「连不上」与「连上了但没密钥」——后者是本次运行缺配置，不是服务故障。
    await step('ai-rag 可达且鉴权通过（AI 索引链路的前提）', async () => {
      const why = ragProbe.reachable
        ? `ai-rag 可达但鉴权失败（HTTP ${ragProbe.status}，${RAG}）。本次运行没有拿到能力服务密钥：`
          + `请把 RAG_API_KEY（或 AI_CAPABILITY_API_KEY）加入 .env.local，或运行前先 export。`
          + `注意后端自己也用同一个密钥——缺它时 ep 侧编目会以 401 失败，而 E2E 仍会全绿。`
        : `ai-rag 连接失败（${RAG}）—— 服务未启动或地址不对。`
      assert(false, `${why} AI 索引链路未经验证。确需在没有 ai-rag 的环境运行，请加 --allow-no-rag 显式跳过。`)
    })
  }
} else {
  currentModule = 'AI 索引链路'
  console.log('\n【阶段 10】AI 索引链路（上传长文 → 入库 → 可检索）')
  let aiFile
  let aiDocumentId

  await step(`上传长文文档 ${AI_FILE_NAME}`, async () => {
    aiFile = await uploadFile(AI_FILE_NAME)
    assert(aiFile.sizeBytes > 200, `正文应足够长以切出检索块，实际仅 ${aiFile.sizeBytes} 字节`)
  })

  await step('创建长文文档草稿 POST /documents/drafts', async () => {
    const res = await api('/api/v1/documents/drafts', {
      method: 'POST',
      body: {
        documentNumber: '',
        title: 'E2E AI 索引链路验证文档',
        summary: '用于验证上传内容确实进入 AI 检索索引。',
        categoryCode: 'WORK_INSTRUCTION',
        maintainerId: 'demo-user',
        maintainerName: '陈工',
        maintainerDepartment: '设备工程部',
        versionNumber: 'V1.0',
        changeSummary: '首次发布',
        files: [{ id: 0, name: aiFile.name, format: aiFile.format, sizeBytes: aiFile.sizeBytes,
          previewable: aiFile.previewable, storageKey: aiFile.storageKey, contentSha256: aiFile.contentSha256 }],
        scopeMode: 'GLOBAL',
        scopes: [],
      },
    })
    assertEqual(res.status, 201, '创建长文文档草稿应返回 201')
    aiDocumentId = res.json.id
    assert(aiDocumentId > 0, '应返回文档 id')
  })

  await step('发布长文文档（触发异步编目）POST /documents/{id}/publish', async () => {
    const res = await api(`/api/v1/documents/${aiDocumentId}/publish`, { method: 'POST' })
    assertEqual(res.status, 200, '发布应返回 200')
    assertEqual(res.json.status, 'PUBLISHED', '发布后应 PUBLISHED')
  })

  await step('ai-rag 按唯一锚点可检索到该文档', async () => {
    const expectedDocumentId = `${RAG_NAMESPACE}:KNOWLEDGE_DOC:${aiDocumentId}`
    const items = await poll(async () => {
      const r = await fetch(`${RAG}/rag/search`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...(RAG_KEY ? { 'X-Service-Key': RAG_KEY } : {}) },
        body: JSON.stringify({ query: AI_ANCHOR, top_k: 8, namespace: RAG_NAMESPACE, scopes: [] }),
      })
      if (r.status !== 200) return null
      const body = await r.json()
      const hits = body.items || body.results || body.chunks || []
      return hits.some((x) => (x.document_id || x.doc_id || x.id) === expectedDocumentId) ? hits : null
    }, { timeoutMs: 40000, intervalMs: 2000, label: '等待异步编目入库' })
    assert(items && items.length > 0,
      `锚点检索未命中 ${expectedDocumentId} —— 上传内容没有进入 AI 索引（这正是本阶段要防的静默失败）`)
  })
}

/* ---- 阶段 11：前端冒烟 ---- */
if (FRONTEND) {
  currentModule = '前端冒烟'
  console.log('\n【阶段 11】前端冒烟')
  await step(`前端首页可访问 GET ${FRONTEND}/`, async () => {
    const res = await fetch(FRONTEND)
    assertEqual(res.status, 200, '前端应返回 200')
  })
} else {
  console.log('\n【阶段 11】跳过前端冒烟（未传 --frontend）')
}

/* ---- 阶段 12：治理授权闸门（D-006 / D-002 回归） ---- */
currentModule = '治理授权闸门'
console.log('\n【阶段 12】治理授权闸门（匿名不得读写治理数据）')

// 治理读端点：登录即可（治理台首页对普通员工开放，前端按角色只展示本人任务），但不允许匿名。
const GOVERNANCE_READS = [
  '/api/v1/governance/tasks',
  '/api/v1/governance/tasks/employees',
  '/api/v1/governance/issues',
  '/api/v1/governance/inventory?page=1&per_page=1',
  '/api/v1/governance/scans',
  '/api/v1/governance/standards',
  '/api/v1/governance/mappings',
  '/api/v1/governance/operations/overview',
  '/api/v1/governance/responsibility/board',
  '/api/v1/governance/tasks/1/history',
  '/api/v1/governance/tasks/1/report',
]

// 治理配置侧写端点：治理管理员专属。
// D-006 的原始缺口：这些此前**任何登录用户**都能调用（含触发全量扫描、启停数据标准、改派任务）。
const GOVERNANCE_WRITES = [
  ['POST', '/api/v1/governance/scans', undefined],
  ['POST', '/api/v1/governance/scans/1/retry', undefined],
  ['POST', '/api/v1/governance/mappings', {}],
  ['POST', '/api/v1/governance/mappings/1/versions', {}],
  ['POST', '/api/v1/governance/mappings/1/confirm', {}],
  ['POST', '/api/v1/governance/mappings/1/disable', {}],
  ['POST', '/api/v1/governance/standards', {}],
  ['POST', '/api/v1/governance/standards/1/versions', {}],
  ['POST', '/api/v1/governance/standards/1/enable', { version: 0 }],
  ['POST', '/api/v1/governance/standards/1/disable', { version: 0 }],
  ['POST', '/api/v1/governance/tasks', {}],
  ['POST', '/api/v1/governance/tasks/1/reassign', {}],
  ['POST', '/api/v1/governance/tasks/1/plans', {}],
  ['POST', '/api/v1/governance/tasks/1/start', {}],
  ['PATCH', '/api/v1/governance/tasks/1/status', {}],
  ['POST', '/api/v1/governance/jobs/1/retry', undefined],
  ['PUT', '/api/v1/governance/asset-responsibilities/1', {}],
]

function describeLeaks(leaks) {
  return leaks.length === 0 ? '' : `\n      ${leaks.join('\n      ')}`
}

await step(`匿名读治理数据被拒（${GOVERNANCE_READS.length} 个端点全部 403 governance_forbidden）`, async () => {
  const leaks = []
  for (const path of GOVERNANCE_READS) {
    const res = await probe(path)
    if (res.status !== 403 || res.json?.error?.code !== 'governance_forbidden') {
      leaks.push(`${path} → ${res.status} ${res.json?.error?.code ?? res.text.slice(0, 60)}`)
    }
  }
  assertEqual(leaks.length, 0, `以下治理读端点在匿名下未返回 403：${describeLeaks(leaks)}`)
})

await step(`匿名写治理配置被拒（${GOVERNANCE_WRITES.length} 个端点全部 401 auth_failed）`, async () => {
  const leaks = []
  for (const [method, path, body] of GOVERNANCE_WRITES) {
    const res = await probe(path, { method, body })
    if (res.status !== 401 || res.json?.error?.code !== 'auth_failed') {
      leaks.push(`${method} ${path} → ${res.status} ${res.json?.error?.code ?? res.text.slice(0, 60)}`)
    }
  }
  assertEqual(leaks.length, 0, `以下治理写端点在匿名下未返回 401：${describeLeaks(leaks)}`)
})

await step('管理员会话读写治理数据不受影响（闸门没有误伤正常权限）', async () => {
  const tasks = await api('/api/v1/governance/tasks')
  assertEqual(tasks.status, 200, '管理员读治理任务')
  const scans = await api('/api/v1/governance/scans')
  assertEqual(scans.status, 200, '管理员读扫描轮次')
  const board = await api('/api/v1/governance/responsibility/board')
  assertEqual(board.status, 200, '管理员读责任看板')
})

// 「登录但非管理员 → 403」这一形态需要真实非管理员账号才能端到端验证。
// 本机库目前只有引导管理员 admin，故默认跳过；未配置时**不拿「匿名被拒」冒充「角色被拒」**。
// 该形态由后端 GovernanceAuthorizationServiceTest 与各 controller 的授权用例覆盖。
const STAFF_USER_ID = process.env.E2E_STAFF_USER_ID
const STAFF_PASSWORD = process.env.E2E_STAFF_PASSWORD
if (STAFF_USER_ID && STAFF_PASSWORD) {
  await step(`已登录的非管理员 ${STAFF_USER_ID} 写治理配置被拒（403 governance_forbidden）`, async () => {
    const adminCookie = sessionCookie
    try {
      await loginAs(STAFF_USER_ID, STAFF_PASSWORD)
      const leaks = []
      for (const [method, path, body] of GOVERNANCE_WRITES) {
        const res = await probe(path, { method, body, session: true })
        if (res.status !== 403 || res.json?.error?.code !== 'governance_forbidden') {
          leaks.push(`${method} ${path} → ${res.status} ${res.json?.error?.code ?? res.text.slice(0, 60)}`)
        }
      }
      assertEqual(leaks.length, 0,
        `以下治理写端点对普通员工未返回 403（D-006 的核心断言）：${describeLeaks(leaks)}`)
    } finally {
      sessionCookie = adminCookie
    }
  })
} else {
  console.log('      跳过「非管理员被拒」端到端验证：未配置 E2E_STAFF_USER_ID / E2E_STAFF_PASSWORD；'
    + '该形态由后端测试覆盖')
}

/* ------------------------------------------------------------------ */
/* 汇总                                                                 */
/* ------------------------------------------------------------------ */
const resultPayload = {
  runId: RUN_TOKEN,
  backend: BACKEND,
  frontend: FRONTEND || null,
  database: 'tianshu',
  databaseHost: '127.0.0.1',
  databasePort: 3306,
  userId: E2E_USER_ID,
  startedAt: cases[0]?.startedAt || new Date().toISOString(),
  finishedAt: new Date().toISOString(),
  passed,
  failed,
  total: cases.length,
  cases,
}
mkdirSync(dirname(RESULT_JSON), { recursive: true })
writeFileSync(RESULT_JSON, `${JSON.stringify(resultPayload, null, 2)}\n`, 'utf8')
console.log(`结构化结果：${RESULT_JSON}`)
console.log(`\n=== E2E 汇总：通过 ${passed} / 失败 ${failed} ===`)
if (failed > 0) {
  console.log('\n失败明细：')
  failures.forEach((failure, index) => console.log(`  ${index + 1}. ${failure.name}\n      ↳ ${failure.message}`))
  process.exitCode = 1
} else {
  console.log('全流程通过 ✅')
}
