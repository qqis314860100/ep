import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../../auth/session'
import { GovernanceMyTodo } from './GovernanceMyTodo'

const task = (id: number, owner: string, status: string, dueDate: string) => ({
  id, name: `治理任务 ${id}`, scope: '字段补充', owner, assigneeId: `emp-${owner === '陈工' ? 'chen' : 'li'}`,
  total: 4, completed: 2, dueDate, status,
  progress: { total: 4, submitted: 2, confirmed: 0, accepted: 0, blocked: 0, reworkRequired: 0 },
})

const myPayload = [
  task(1, '陈工', 'IN_PROGRESS', '2099-01-01'),
  task(2, '陈工', 'PENDING_CONFIRMATION', '2099-01-01'),
  task(3, '陈工', 'PENDING_ACCEPTANCE', '2020-01-01'), // 已逾期 + 待验收
]

function mockFetch() {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (!url.includes('/tasks?ownerUserId=')) {
      return { ok: true, status: 200, json: async () => [] } as Response
    }
    const owner = new URLSearchParams(url.split('?')[1]).get('ownerUserId')
    const body = owner === 'emp-chen' ? myPayload : []
    return { ok: true, status: 200, json: async () => body } as Response
  }))
}

function renderTodo() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <GovernanceMyTodo />
    </QueryClientProvider>,
  )
}

describe('GovernanceMyTodo（R2 我的待办）', () => {
  beforeEach(() => {
    authSession.set({ userId: 'emp-chen', name: '陈工', department: '设备工程部', roles: ['UPLOADER'] })
    mockFetch()
  })

  afterEach(() => {
    authSession.set(null)
    localStorage.clear()
    vi.unstubAllGlobals()
  })

  it('按登录员工统计名下任务分组计数', async () => {
    renderTodo()

    expect(await screen.findByLabelText('整改中 1 个', {}, { timeout: 3000 })).toBeVisible()
    expect(await screen.findByLabelText('待确认 1 个')).toBeVisible()
    expect(await screen.findByLabelText('待验收 1 个')).toBeVisible()
    expect(await screen.findByLabelText('已逾期 1 个')).toBeVisible()

    expect(screen.getByText('待确认 · 陈工')).toBeVisible()
    expect(screen.getByText('已逾期 · 陈工')).toBeVisible()
  })

  it('非治理员工会话不渲染待办面板', async () => {
    authSession.set({ userId: 'admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN'] })
    renderTodo()

    await waitFor(() => expect(screen.queryByLabelText('我的待办')).not.toBeInTheDocument())
  })

  it('点击计数可进入待办任务', async () => {
    const onOpenTask = vi.fn()
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    render(
      <QueryClientProvider client={client}>
        <GovernanceMyTodo onOpenTask={onOpenTask} />
      </QueryClientProvider>,
    )

    const card = await screen.findByLabelText('待验收 1 个', {}, { timeout: 3000 })
    card.click()

    await waitFor(() => expect(onOpenTask).toHaveBeenCalledWith(3))
  })
})
