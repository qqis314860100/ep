#!/usr/bin/env node
/**
 * rag-contract-smoke.mjs — ep ↔ ai-rag 契约冒烟（Node 18+，无第三方依赖）
 *
 * 背景：两仓之间唯一的接口是 HTTP 契约（契约由 ep 定义、ai-rag 适配），但这条线
 * 目前没有自动化守护 —— ai-rag 的 test_capability_contract.py 把 pipeline mock 掉了，
 * ep 侧也没有对 ai-rag 的契约测试。本脚本把手工业已验过的契约固化成可重复执行的门禁。
 *
 * 默认**只读**：不发入库请求、不调 LLM。
 * 加 --with-llm 才跑 SSE 事件序一项（会真实调用 LLM，产生费用）。
 *
 * 用法：
 *   node scripts/e2e/rag-contract-smoke.mjs [--base http://localhost:8000] [--with-llm]
 *
 * 服务密钥解析顺序：--key 参数 > 环境变量 AI_CAPABILITY_API_KEY/RAG_API_KEY
 *   > ai-rag/.env 的 RAG_API_KEY（读文件，不打印）。
 * 未配置密钥时只跑“不带鉴权头应为 401”一项，其余跳过并明确提示。
 */
import { readFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const REPO_ROOT = join(__dirname, '..', '..')

const args = new Map()
for (let i = 2; i < process.argv.length; i++) {
  const a = process.argv[i]
  if (a === '--with-llm') args.set('with-llm', true)
  else if (a.startsWith('--')) args.set(a.slice(2), process.argv[++i])
}
const BASE = (args.get('base') || process.env.AI_CAPABILITY_BASE_URL || 'http://localhost:8000').replace(/\/$/, '')
const WITH_LLM = !!args.get('with-llm')
const NAMESPACE = args.get('namespace') || process.env.AI_CAPABILITY_NAMESPACE || 'ep-docs'

function resolveKey() {
  if (args.get('key')) return args.get('key')
  if (process.env.AI_CAPABILITY_API_KEY) return process.env.AI_CAPABILITY_API_KEY
  if (process.env.RAG_API_KEY) return process.env.RAG_API_KEY
  const envFile = join(REPO_ROOT, 'ai-rag', '.env')
  if (existsSync(envFile)) {
    const line = readFileSync(envFile, 'utf8').split('\n').find(l => /^RAG_API_KEY=/.test(l))
    if (line) return line.slice(line.indexOf('=') + 1).trim().replace(/^["']|["']$/g, '')
  }
  return ''
}

const KEY = resolveKey()
const H = (extra = {}) => ({ 'Content-Type': 'application/json', ...(KEY ? { 'X-Service-Key': KEY } : {}), ...extra })

let failed = 0, skipped = 0, passed = 0
const step = async (label, fn) => {
  try {
    const r = await fn()
    if (r === 'skip') { skipped++; console.log(`  SKIP  ${label}`); return }
    passed++; console.log(`  PASS  ${label}`)
  } catch (e) {
    failed++; console.log(`  FAIL  ${label}\n          ${e.message}`)
  }
}
const assert = (cond, msg) => { if (!cond) throw new Error(msg) }

async function req(path, { method = 'GET', body, headers } = {}) {
  const res = await fetch(BASE + path, { method, headers: headers || H(), body })
  const text = await res.text()
  return { status: res.status, text, json: (() => { try { return JSON.parse(text) } catch { return null } })() }
}

console.log(`\nrag 契约冒烟  base=${BASE}  namespace=${NAMESPACE}  key=${KEY ? '已配置' : '未配置'}\n`)

if (!KEY) {
  console.log('  未解析到服务密钥：只验证鉴权生效，其余项跳过。')
  console.log('  提示：可在 ai-rag/.env 设置 RAG_API_KEY，或传 --key / 环境变量 AI_CAPABILITY_API_KEY。\n')
}

// 1. 鉴权：不带鉴权头必须被拒（契约要求服务端强制鉴权）
await step('鉴权拒绝：不带 X-Service-Key 时 /rag/health 返回 401', async () => {
  const r = await req('/rag/health', { headers: { 'Content-Type': 'application/json' } })
  if (!KEY && r.status !== 401) throw new Error(`未配置密钥时服务端应 401（当前 ${r.status}）—— 该服务对 ep 未开启鉴权`)
  assert(r.status === 401, `期望 401，实际 ${r.status}`)
})

if (!KEY) {
  console.log(`\n结果：pass ${passed} / fail ${failed} / skip ${skipped}（密钥缺失，主契约项未验证）\n`)
  process.exit(failed ? 1 : 0)
}

// 2. 鉴权通过
await step('鉴权通过：带 X-Service-Key 时 /rag/health 返回 200 且 chroma 可用', async () => {
  const r = await req('/rag/health')
  assert(r.status === 200, `期望 200，实际 ${r.status}`)
  assert(r.json?.service === 'rag-service', `响应缺少 service 标识：${r.text.slice(0, 80)}`)
  assert(r.json?.chroma_status === 'ok', `chroma 不可用：${r.json?.chroma_status}`)
})

// 3. /rag/extract 端点存在，且识别 ep 契约的 fileContentBase64/fileName
await step('契约端点：/rag/extract 存在且识别 ep 的 camelCase 文件字段', async () => {
  const r = await req('/rag/extract', { method: 'POST', body: '{}' })
  assert(r.status !== 404, '/rag/extract 不存在（返回 404）—— ep 的 extractMetadata 会失败')
  assert(r.status === 400, `空 body 期望 400，实际 ${r.status}`)
  const detail = String(r.json?.detail ?? r.text)
  assert(/fileContentBase64/.test(detail) && /fileName/.test(detail),
    `错误消息未体现 ep 契约字段（fileContentBase64/fileName）：${detail.slice(0, 120)}`)
})

// 4. /rag/search 接受 namespace 与 scopes，且 namespace 真正隔离
await step('契约参数 + 隔离：/rag/search 接受 namespace/scopes 且不同 namespace 命中不同', async () => {
  const a = await req('/rag/search', { method: 'POST', body: JSON.stringify({ query: '电池', top_k: 5, namespace: 'battery', scopes: [] }) })
  assert(a.status === 200, `battery 检索期望 200，实际 ${a.status}`)
  const b = await req('/rag/search', { method: 'POST', body: JSON.stringify({ query: '电池', top_k: 5, namespace: NAMESPACE, scopes: [] }) })
  assert(b.status === 200, `${NAMESPACE} 检索期望 200，实际 ${b.status}`)
  const ids = o => JSON.stringify((o?.items || o?.results || o?.chunks || []).map(x => x.document_id || x.doc_id || x.id))
  assert(ids(a.json) !== ids(b.json),
    `battery 与 ${NAMESPACE} 命中集合相同 —— namespace 隔离可能失效`)
})

// 5. 未知 namespace 不应静默回退到默认语料（防止跨域召回）
await step('隔离兜底：未知 namespace 不静默回退到 battery 语料', async () => {
  const bogus = await req('/rag/search', { method: 'POST', body: JSON.stringify({ query: '电池', top_k: 5, namespace: 'contract-smoke-not-exist', scopes: [] }) })
  const battery = await req('/rag/search', { method: 'POST', body: JSON.stringify({ query: '电池', top_k: 5, namespace: 'battery', scopes: [] }) })
  assert(bogus.status === 200, `未知 namespace 期望 200（空结果），实际 ${bogus.status}`)
  const ids = o => JSON.stringify((o?.items || o?.results || o?.chunks || []).map(x => x.document_id || x.doc_id || x.id))
  assert(ids(bogus.json) !== ids(battery.json), '未知 namespace 返回了 battery 的结果 —— 存在静默回退')
})

// 6. SSE 事件序（ep 模式）—— 会真实调用 LLM，需显式开启
await step(`契约事件序：ep 模式 SSE 首事件为 meta（需 --with-llm，会调用 LLM）`, async () => {
  if (!WITH_LLM) return 'skip'
  const ac = new AbortController()
  const res = await fetch(`${BASE}/rag/chat/stream`, {
    method: 'POST', headers: H({ Accept: 'text/event-stream' }),
    body: JSON.stringify({ question: '契约冒烟：请用一句话回答', top_k: 3, namespace: 'battery', scopes: [], history: [] }),
    signal: ac.signal,
  })
  assert(res.status === 200, `SSE 期望 200，实际 ${res.status}`)
  const reader = res.body.getReader(), dec = new TextDecoder()
  let buf = '', first = null
  try {
    while (first === null) {
      const { value, done } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      const i = buf.indexOf('\n\n')
      if (i >= 0) {
        const m = buf.slice(0, i).match(/^data: (.*)$/m)
        if (m) { try { const o = JSON.parse(m[1]); first = o.type || o.event || Object.keys(o)[0] } catch { first = 'non-json' } }
      }
    }
  } finally { ac.abort() }   // 拿到首事件即断开，不等待完整回答
  assert(first === 'meta', `ep 模式首事件应为 meta，实际为 ${first}（契约要求 meta → delta* → citations → done）`)
})

console.log(`\n结果：pass ${passed} / fail ${failed} / skip ${skipped}${WITH_LLM ? '' : '（未开启 --with-llm，SSE 事件序未验证）'}\n`)
process.exit(failed ? 1 : 0)
