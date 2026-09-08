import { render, screen, waitFor } from '@testing-library/react'
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
      citations: [{ docId: '103', location: '第3页', excerpt: '摘录', inScope: true }],
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

describe('AiChatPage（T5 AI 助手工作台）', () => {
  const calls: Array<{ url: string; init?: RequestInit }> = []

  beforeEach(() => {
    calls.length = 0
    authSession.set({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['CONTENT_ADMIN'] })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      calls.push({ url, init })
      if (url.endsWith('/ai/chat')) {
        return new Response(sseBody, { status: 200, headers: { 'Content-Type': 'text/event-stream' } })
      }
      if (url.includes('/messages')) {
        return new Response(JSON.stringify(messagesPayload()), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return new Response(JSON.stringify(sessionsPayload()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }))
  })

  afterEach(() => {
    authSession.set(null)
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('加载会话列表并可发起流式问答渲染回答', async () => {
    render(<AiChatPage />)

    expect(await screen.findByText('这是什么资产？')).toBeVisible()

    const input = await screen.findByLabelText('问题输入框')
    await userEvent.type(input, '宁德基地有哪些焊接数模')
    await userEvent.click(screen.getByRole('button', { name: '发送问题' }))

    await waitFor(() => {
      expect(calls.some((call) => call.url.endsWith('/ai/chat'))).toBe(true)
    })
    expect(await screen.findByText('流式回答内容')).toBeVisible()
    expect(await screen.findByText(/引用 1：第3页/)).toBeVisible()
  })
})
