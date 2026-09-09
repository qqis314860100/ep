import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../auth/session'
import AiChatPage from './AiChatPage'

function sessionsPayload() {
  return [{ id: 1, title: '这是什么资产？', createdAt: '2026-09-09T08:00:00Z', updatedAt: '2026-09-09T08:01:00Z' }]
}

function messagesPayload() {
  return [
    { id: 2, role: 'USER', content: '宁德基地有哪些焊接数模', citations: [], createdAt: '2026-09-09T08:01:00Z' },
    {
      id: 3,
      role: 'ASSISTANT',
      content: '流式回答内容',
      citations: [{ docId: '103', location: '第3页', excerpt: '摘录含 **重点词** 的 markdown 片段', inScope: true }],
      createdAt: '2026-09-09T08:01:05Z',
    },
  ]
}

const sseBody = [
  'event: meta',
  'data: {"sessionId":"1","messageId":"3"}',
  '',
  'event: delta',
  'data: {"text":"流式回答内容"}',
  '',
  'event: citations',
  'data: {"refs":[{"docId":"103","location":"第3页","excerpt":"摘录"}]}',
  '',
  'event: done',
  'data: {"usage":"{}"}',
  '',
  '',
].join('\n')

const sseErrorBody = [
  'event: meta',
  'data: {"sessionId":"1","messageId":"3"}',
  '',
  'event: error',
  'data: {"code":"unavailable","message":"能力服务降级"}',
  '',
  '',
].join('\n')

function installFetchMock(chatBody: string, historyMessages = false) {
  const calls: Array<{ url: string; init?: RequestInit }> = []
  let chatSent = false
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    calls.push({ url, init })
    if (url.endsWith('/ai/chat')) {
      chatSent = true
      return new Response(chatBody, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
    }
    if (url.includes('/messages')) {
      const body = historyMessages || chatSent ? messagesPayload() : []
      return new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }
    return new Response(JSON.stringify(sessionsPayload()), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })
  }))
  return calls
}

describe('AiChatPage（T5 AI 助手工作台）', () => {
  let calls: Array<{ url: string; init?: RequestInit }>

  beforeEach(() => {
    authSession.set({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['CONTENT_ADMIN'] })
    calls = installFetchMock(sseBody)
  })

  afterEach(() => {
    authSession.set(null)
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('加载会话列表并可发起流式问答，渲染 markdown 回答与可展开引用卡', async () => {
    render(<AiChatPage />)

    expect(await screen.findByText('这是什么资产？')).toBeVisible()

    const input = await screen.findByLabelText('问题输入框')
    await userEvent.type(input, '宁德基地有哪些焊接数模')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => {
      expect(calls.some((call) => call.url.endsWith('/ai/chat'))).toBe(true)
    })
    expect(await screen.findByText('流式回答内容')).toBeVisible()

    const summary = await screen.findByRole('button', { name: /来源引用 1 处/ })
    expect(summary).toBeVisible()
    await userEvent.click(summary)
    expect(await screen.findByText('第3页')).toBeVisible()
    expect(await screen.findByText('重点词')).toBeVisible()
    expect(screen.getByRole('button', { name: '查看引用 1' })).toBeVisible()
  })

  it('空会话欢迎面板的示例问题可一键发起提问', async () => {
    render(<AiChatPage />)

    await userEvent.click(await screen.findByRole('button', { name: '宁德基地 A 拉线有哪些焊接数模？' }))

    await waitFor(() => {
      const chatCall = calls.find((call) => call.url.endsWith('/ai/chat'))
      expect(chatCall).toBeTruthy()
      const body = JSON.parse(String(chatCall!.init?.body))
      expect(body.question).toBe('宁德基地 A 拉线有哪些焊接数模？')
    })
    expect(await screen.findByText('流式回答内容')).toBeVisible()
  })

  it('路由返回后自动载入首个会话历史（无需再次点击）', async () => {
    installFetchMock(sseBody, true)
    render(<AiChatPage />)

    expect(await screen.findByText('这是什么资产？')).toBeVisible()
    expect(await screen.findByText('宁德基地有哪些焊接数模')).toBeVisible()
    expect(await screen.findByText('流式回答内容')).toBeVisible()
  })

  it('能力服务降级时展示明确错误并可重试同一问题', async () => {
    calls = installFetchMock(sseErrorBody)

    render(<AiChatPage />)
    const input = await screen.findByLabelText('问题输入框')
    await userEvent.type(input, '焊接飞溅原因')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('AI 服务暂不可用：能力服务降级')

    const chatCallsBefore = calls.filter((call) => call.url.endsWith('/ai/chat')).length
    await userEvent.click(within(alert).getByRole('button', { name: /重\s*试/ }))
    await waitFor(() => {
      expect(calls.filter((call) => call.url.endsWith('/ai/chat')).length).toBeGreaterThan(chatCallsBefore)
    })
  })
})
