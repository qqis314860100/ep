#!/usr/bin/env node
/**
 * flow.mjs — 从 0 到 尾的全业务 E2E 全流程脚本（API 级，Node 18+ 无第三方依赖）
 *
 * 覆盖（对应真实业务闭环）：
 *   阶段 0  健康检查 + 字典基线
 *   阶段 1  mock 文件上传（PDF / X_T / TXT / PNG → POST /uploads/files）
 *   阶段 2  资产生命周期（建草稿 → 提交 → 待整理）
 *   阶段 3  治理扫描（手动触发 → 运行成功 → 问题池）
 *   阶段 4  治理闭环（建任务 → 计划 → 启动 → 执行 → 业务确认 → 质量验收 → 正式应用 → 任务完成）
 *   阶段 4b 自有资产治理闭环（指派责任人 → 扫描 → 任务 → 确认 → 验收 → 正式应用）
 *   阶段 5  知识文档（建草稿 → 发布 → 检索）
 *   阶段 6  资产文档关联（双向）
 *   阶段 7  收藏 + 评论 + 点赞
 *   阶段 8  文件下载 / 预览 / 打包下载
 *   阶段 9  统一检索（资产 + 文档同框命中）
 *   阶段 10 前端冒烟（可选，需 --frontend）
 *
 * 用法：
 *   node flow.mjs --backend http://127.0.0.1:8080 [--frontend http://127.0.0.1:5173]
 *
 * 退出码：全部通过 = 0；任一断言失败 = 1（失败会继续跑完，最终汇总）。
 */
import { readFileSync } from 'node:fs'
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

/* ------------------------------------------------------------------ */
/* 轻量测试框架                                                         */
/* ------------------------------------------------------------------ */
let passed = 0
let failed = 0
const failures = []

async function step(name, fn) {
  try {
    await fn()
    passed++
    console.log(`  ✅ ${name}`)
  } catch (error) {
    failed++
    failures.push({ name, message: error.message })
    console.log(`  ❌ ${name}\n      ↳ ${error.message}`)
  }
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
/* 主流程                                                               */
/* ------------------------------------------------------------------ */
console.log(`\n=== E2E 全流程开始  backend=${BACKEND}${FRONTEND ? ` frontend=${FRONTEND}` : ''} ===\n`)

/* ---- 阶段 0：健康检查 + 字典基线 ---- */
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

/* ---- 阶段 1：mock 文件上传 ---- */
console.log('\n【阶段 1】mock 文件上传')
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
console.log('\n【阶段 2】资产生命周期（草稿 → 提交 → 待整理）')
let assetA
await step('创建资产草稿 POST /assets/drafts', async () => {
  const body = {
    assetNumber: 'E2E-H03-9001',
    name: 'E2E 电池包总成数模',
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
      processSection: '焊接段',
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
  assertEqual(assetA.assetNumber, 'E2E-H03-9001', '资料编号回显')
})

await step('提交资产 POST /assets/{id}/submit → 待整理', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}/submit`, { method: 'POST' })
  assertEqual(res.status, 200, '提交应返回 200')
  assertEqual(res.json.status, 'PENDING_CURATION', '提交后应进入待整理')
})

await step('查询资产详情 GET /assets/{id}', async () => {
  const res = await api(`/api/v1/assets/${assetA.id}`)
  assertEqual(res.status, 200, 'HTTP 状态')
  assertEqual(res.json.name, 'E2E 电池包总成数模', '资产名称')
  assertEqual(res.json.scopes[0].base, '宁德基地', '适用范围命中')
})

/* ---- 阶段 3：治理扫描 ---- */
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
console.log('\n【阶段 4】治理闭环（任务 → 计划 → 启动 → 执行 → 确认 → 验收 → 正式应用）')
let taskId
let taskVersion = 0
await step('问题池存在开放问题（种子 1001/1002）', async () => {
  const res = await api('/api/v1/governance/issues?status=OPEN')
  assertEqual(res.status, 200, 'HTTP 状态')
  const ids = new Set(res.json.map((issue) => issue.id))
  assert(ids.has(1001) && ids.has(1002), `问题池应含开放问题 1001/1002，实际含 [${[...ids].join(', ')}]`)
})

await step('创建治理任务 POST /governance/tasks', async () => {
  const res = await api('/api/v1/governance/tasks', {
    method: 'POST',
    body: { name: 'E2E 字段治理闭环', issueIds: [1001, 1002], ownerUserId: 'emp-chen', ownerName: '陈工', dueDate: '2026-09-30' },
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
      assigneeId: 'emp-chen', responsibleUserId: 'emp-chen',
      dependencyIds: [], issueIds: [1001, 1002],
    },
  })
  assertEqual(res.status, 201, '创建计划应返回 201')
  assert(res.json.id, '计划应有 id')
})

await step('启动任务 POST /governance/tasks/{id}/start（计划锁定）', async () => {
  const res = await api(`/api/v1/governance/tasks/${taskId}/start`, {
    method: 'POST',
    body: { version: taskVersion, actorUserId: 'emp-admin' },
  })
  assertEqual(res.status, 200, '启动应返回 200')
  assertEqual(res.json.status, 'IN_PROGRESS', '启动后应进行中')
  taskVersion = res.json.version
})

const GOV_HEADERS = { 'X-User-Id': 'emp-li', 'X-User-Roles': 'CONTENT_ADMIN,SYSTEM_ADMIN' }
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
      body: { itemVersion: item.item.version, assetVersion: item.item.assetVersion, proposedValue: proposed, actorUserId: 'emp-chen' },
      headers: GOV_HEADERS,
    })
    assertEqual(draftRes.status, 200, `保存 ${field} 治理结果草稿`)
    const submitRes = await api(`/api/v1/governance/items/${item.item.id}/submit`, {
      method: 'POST',
      body: { resultVersionId: draftRes.json.id, resultVersion: draftRes.json.version, actorUserId: 'emp-chen' },
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
      body: { decision: 'APPROVED', comment: '', decisionVersion: 0, confirmerUserId: 'emp-li' },
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
    return j.processing === 0 && j.failed === 0 ? j : null
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

await step('治理问题已解决（1001/1002 → RESOLVED）', async () => {
  const res = await api('/api/v1/governance/issues?status=RESOLVED')
  const ids = new Set(res.json.map((issue) => issue.id))
  assert(ids.has(1001) && ids.has(1002), '问题 1001/1002 应已解决')
})

/* ---- 阶段 4b：自有资产治理闭环（责任人指派 → 扫描 → 闭环） ---- */
console.log('\n【阶段 4b】自有资产治理闭环（责任人指派 → 扫描 → 闭环）')
const RESP_ADMIN_HEADERS = { 'X-User-Roles': 'CONTENT_ADMIN,SYSTEM_ADMIN' }
await step('指派资产责任人 PUT /governance/asset-responsibilities/{assetId}', async () => {
  const res = await api(`/api/v1/governance/asset-responsibilities/${assetA.id}`, {
    method: 'PUT',
    body: { responsibleUserId: 'emp-chen', responsibilityScope: '设备工程部' },
    headers: RESP_ADMIN_HEADERS,
  })
  assertEqual(res.status, 200, '指派应返回 200')
  assertEqual(res.json.responsibleUserId, 'emp-chen', '责任人应为 emp-chen')
  assertEqual(res.json.responsibilityScope, '设备工程部', '责任范围')
})
await step('读取资产责任人 GET /governance/asset-responsibilities/{assetId}', async () => {
  const res = await api(`/api/v1/governance/asset-responsibilities/${assetA.id}`, { headers: RESP_ADMIN_HEADERS })
  assertEqual(res.status, 200, '读取应返回 200')
  assertEqual(res.json.responsibleUserId, 'emp-chen', '当前有效责任人')
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
    body: { name: 'E2E 自有资产范围治理', issueIds: [ownIssueId], ownerUserId: 'emp-chen', ownerName: '陈工', dueDate: '2026-09-30' },
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
      assigneeId: 'emp-chen', responsibleUserId: 'emp-chen',
      dependencyIds: [], issueIds: [ownIssueId],
    },
  })
  assertEqual(res.status, 201, '新增计划应返回 201')
})
await step('自有资产任务启动 POST /governance/tasks/{id}/start', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/start`, {
    method: 'POST', body: { version: ownTaskVersion, actorUserId: 'emp-admin' },
  })
  assertEqual(res.status, 200, '启动应返回 200')
  assertEqual(res.json.status, 'IN_PROGRESS', '进行中')
  ownTaskVersion = res.json.version
})

// 自有资产执行：责任人 emp-chen 亲自执行（X-User-Id=emp-chen），验证责任人指派生效
const OWN_HEADERS = { 'X-User-Id': 'emp-chen', 'X-User-Roles': 'CONTENT_ADMIN' }
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
    scopes: [{ platformFamily: '乘用车', platformVariant: '大面水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段' }],
  }
  const draftRes = await api(`/api/v1/governance/items/${ownItemId}/result-draft`, {
    method: 'PUT',
    body: { itemVersion: 0, assetVersion: ownAssetVersion, proposedValue: proposed, actorUserId: 'emp-chen' },
    headers: OWN_HEADERS,
  })
  assertEqual(draftRes.status, 200, '保存范围治理结果草稿')
  const submitRes = await api(`/api/v1/governance/items/${ownItemId}/submit`, {
    method: 'POST',
    body: { resultVersionId: draftRes.json.id, resultVersion: draftRes.json.version, actorUserId: 'emp-chen' },
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
await step('自有资产逐项确认通过（责任人 emp-chen 审批）', async () => {
  const res = await api(`/api/v1/governance/tasks/${ownTaskId}/confirmation-rounds/current`, { headers: OWN_HEADERS })
  for (const item of res.json.items) {
    const decisionRes = await api(`/api/v1/governance/confirmation-rounds/${ownRoundId}/items/${item.itemId}/decision`, {
      method: 'PUT',
      body: { decision: 'APPROVED', comment: '', decisionVersion: 0, confirmerUserId: 'emp-chen' },
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
    return j.processing === 0 && j.failed === 0 ? j : null
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

/* ---- 阶段 10：前端冒烟 ---- */
if (FRONTEND) {
  console.log('\n【阶段 10】前端冒烟')
  await step(`前端首页可访问 GET ${FRONTEND}/`, async () => {
    const res = await fetch(FRONTEND)
    assertEqual(res.status, 200, '前端应返回 200')
  })
} else {
  console.log('\n【阶段 10】跳过前端冒烟（未传 --frontend）')
}

/* ------------------------------------------------------------------ */
/* 汇总                                                                 */
/* ------------------------------------------------------------------ */
console.log(`\n=== E2E 汇总：通过 ${passed} / 失败 ${failed} ===`)
if (failed > 0) {
  console.log('\n失败明细：')
  failures.forEach((failure, index) => console.log(`  ${index + 1}. ${failure.name}\n      ↳ ${failure.message}`))
  process.exit(1)
}
console.log('全流程通过 ✅')
