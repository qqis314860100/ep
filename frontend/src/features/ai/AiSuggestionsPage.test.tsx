import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../auth/session'
import AiSuggestionsPage from './AiSuggestionsPage'

const suggestion = (id: number, status: string) => ({
  id,
  targetType: 'ASSET',
  targetId: 103,
  targetTitle: '输送模块布置数模',
  status,
  source: 'AI',
  proposed: {
    name: '输送模块布置数模（AI 整理）',
    description: '',
    assetTypeCode: 'THREE_DIMENSIONAL_MODEL',
    tags: ['输送', '模块'],
    summary: '',
    categoryCode: '',
  },
  evidence: ['片段一：输送模块布置说明'],
  confidence: 0.92,
  scopes: [],
  createdBy: 'ai-curation',
  createdAt: '2026-09-09T08:00:00Z',
  resolvedBy: '',
  resolvedAt: null,
})

function pagePayload() {
  return {
    data: [suggestion(1, 'PENDING'), suggestion(2, 'CONFIRMED')],
    meta: { total: 2, page: 1, perPage: 10, totalPages: 1 },
  }
}

describe('AiSuggestionsPage（T5 AI 编目建议清单）', () => {
  const calls: string[] = []

  beforeEach(() => {
    calls.length = 0
    authSession.set({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['CONTENT_ADMIN'] })
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      calls.push(url)
      if (init?.method === 'POST' && url.endsWith('/confirm')) {
        return new Response(JSON.stringify(suggestion(1, 'CONFIRMED')), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        })
      }
      return new Response(JSON.stringify(pagePayload()), {
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

  it('渲染建议清单并支持对待确认建议执行确认', async () => {
    render(<AiSuggestionsPage />)

    expect((await screen.findAllByText(/输送模块布置数模（AI 整理）/)).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('输送模块布置数模')).length).toBeGreaterThan(0)
    expect((await screen.findAllByText('92%')).length).toBeGreaterThan(0)

    const confirmButton = await screen.findByTestId('confirm-1')
    await userEvent.click(confirmButton)

    await waitFor(() => {
      expect(calls.some((url) => url.includes('/suggestions/1/confirm'))).toBe(true)
    })
  })

  it('刷新会再次请求清单', async () => {
    render(<AiSuggestionsPage />)
    expect((await screen.findAllByText(/输送模块布置数模（AI 整理）/)).length).toBeGreaterThan(0)
    const listCallsBefore = calls.filter((url) => url.includes('/suggestions') && !url.includes('/confirm')).length

    await userEvent.click(await screen.findByTestId('refresh-list'))

    await waitFor(() => {
      expect(calls.filter((url) => url.includes('/suggestions') && !url.includes('/confirm')).length)
        .toBeGreaterThan(listCallsBefore)
    })
  })
})
