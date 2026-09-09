import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { GovernanceRailHome } from './GovernanceRailHome'

const inventoryPayload = {
  totals: { total: 5, pendingCuration: 2, claimed: 0, standardized: 3, duplicateSuspects: 0, anomalousFiles: 0 },
  rates: { completeness: 100, scopeCoverage: 60, ownerCoverage: 100, fileAvailability: 100 },
  items: [], meta: { total: 5, page: 1, perPage: 1 },
}

const tasksPayload = [
  { id: 1, name: 'A 拉线范围补充', scope: '旧拉线', owner: '陈工', total: 286, completed: 174, dueDate: '2026-08-15', status: 'IN_PROGRESS', progress: { total: 286, submitted: 174, confirmed: 0, accepted: 0, blocked: 0, reworkRequired: 0 } },
  { id: 2, name: '历史专业类别标准化', scope: '机械/电气', owner: '李工', total: 421, completed: 0, dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION', progress: { total: 421, submitted: 421, confirmed: 0, accepted: 0, blocked: 0, reworkRequired: 0 } },
  { id: 3, name: '失效文件引用治理', scope: '文件', owner: '王工', total: 37, completed: 37, dueDate: '2026-07-25', status: 'COMPLETED', progress: { total: 37, submitted: 37, confirmed: 37, accepted: 37, blocked: 0, reworkRequired: 0 } },
]

const issuesPayload = Array.from({ length: 5 }, (_, index) => ({
  id: 1001 + index, assetId: 101 + index, targetField: 'DESCRIPTION', issueType: 'MISSING_DESCRIPTION', targetPath: '/description', originalFactJson: '', severity: 'HIGH', blocking: true, status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
}))

function mockFetch(fail: boolean) {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (fail) return { ok: false, status: 500, json: async () => ({}) } as Response
    const url = String(input)
    const body = url.includes('/inventory') ? inventoryPayload : url.includes('/issues') ? issuesPayload : url.includes('/scans') ? [] : tasksPayload
    return { ok: true, status: 200, json: async () => body } as Response
  }))
}

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/sys/drawing']}>
        <Routes>
          <Route path="/sys/drawing" element={<GovernanceRailHome />} />
          <Route path="/sys/drawing/issues" element={<div>字段问题池页面</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('GovernanceRailHome 工作台', () => {
  beforeEach(() => mockFetch(false))
  afterEach(() => vi.unstubAllGlobals())

  it('首屏直接呈现派发与处理入口，阶段节点作为业务导航', async () => {
    renderHome()
    expect(await screen.findByText('数据治理工作台')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '派发任务' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '认领任务' })).toBeInTheDocument()
    await screen.findByText('历史专业类别标准化')
    expect(screen.getByRole('button', { name: '派发任务' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '处理首要任务' })).toBeInTheDocument()
    expect(within(screen.getByLabelText('待派发问题')).getAllByRole('button')).toHaveLength(3)
    expect(within(screen.getByLabelText('待处理任务')).getAllByRole('button')).toHaveLength(2)
    const progress = screen.getByLabelText(/治理阶段进度：/)
    for (const label of ['扫描入库', '问题池', '整改分派', '业务确认', '质量验收', '正式应用']) {
      expect(within(progress).getByText(label)).toBeInTheDocument()
    }
    expect(within(progress).getAllByRole('button')).toHaveLength(6)
  })

  it('点击问题池阶段直接跳转，不改写工作台内容', async () => {
    const user = userEvent.setup()
    renderHome()
    const progress = await screen.findByLabelText(/治理阶段进度：/)
    await user.click(within(progress).getByRole('button', { name: /问题池/ }))
    expect(await screen.findByText('字段问题池页面')).toBeInTheDocument()
  })

  it('数据失败时保留工作台结构并显示重试提示', async () => {
    mockFetch(true)
    renderHome()
    expect(await screen.findByText('部分数据未加载成功')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '派发任务' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '认领任务' })).toBeInTheDocument()
    expect(screen.getByLabelText(/治理阶段进度：/)).toBeInTheDocument()
  })
})
