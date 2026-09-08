import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { GovernanceRailHome } from './GovernanceRailHome'

const inventoryPayload = {
  totals: { total: 5, pendingCuration: 2, claimed: 0, standardized: 3, duplicateSuspects: 0, anomalousFiles: 0 },
  rates: { completeness: 100, scopeCoverage: 60, ownerCoverage: 100, fileAvailability: 100 },
  items: [],
  meta: { total: 5, page: 1, perPage: 1 },
}

const tasksPayload = [
  {
    id: 1, name: 'A 拉线范围补充', scope: '旧拉线', owner: '陈工', total: 286, completed: 174,
    dueDate: '2026-08-15', status: 'IN_PROGRESS',
    progress: { total: 286, submitted: 174, confirmed: 0, accepted: 0, blocked: 0, reworkRequired: 0 },
  },
  {
    id: 2, name: '历史专业类别标准化', scope: '机械/电气', owner: '李工', total: 421, completed: 0,
    dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION',
    progress: { total: 421, submitted: 421, confirmed: 0, accepted: 0, blocked: 0, reworkRequired: 0 },
  },
  {
    id: 3, name: '失效文件引用治理', scope: '文件', owner: '王工', total: 37, completed: 37,
    dueDate: '2026-07-25', status: 'COMPLETED',
    progress: { total: 37, submitted: 37, confirmed: 37, accepted: 37, blocked: 0, reworkRequired: 0 },
  },
]

const issuesPayload = Array.from({ length: 5 }, (_, index) => ({
  id: 1001 + index, assetId: 101 + index, targetField: 'DESCRIPTION', issueType: 'MISSING_DESCRIPTION',
  targetPath: '/description', originalFactJson: '', severity: 'HIGH', blocking: true,
  status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
}))

function mockFetch(fail: boolean) {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    if (fail) return { ok: false, status: 500, json: async () => ({}) } as Response
    const url = String(input)
    let body: unknown
    if (url.includes('/inventory')) body = inventoryPayload
    else if (url.includes('/issues')) body = issuesPayload
    else if (url.includes('/scans')) body = []
    else body = tasksPayload
    return { ok: true, status: 200, json: async () => body } as Response
  }))
}

function renderHome() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/sys/drawing']}>
        <GovernanceRailHome />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** 以面板标题定位"该步待办"面板，避免与下方任务闭环明细表格文本重复。 */
function todoPanel(title: string): HTMLElement {
  const heading = screen.getByText(title)
  const panel = heading.closest('section')
  if (!panel) throw new Error(`未找到面板：${title}`)
  return panel as HTMLElement
}

describe('GovernanceRailHome（R1a 治理首页切片）', () => {
  beforeEach(() => { mockFetch(false) })
  afterEach(() => { vi.unstubAllGlobals() })

  it('渲染指标卡、六节点轨道与默认当前步骤的待办清单', async () => {
    renderHome()
    expect(await screen.findByText('数据治理 · 分派工作台')).toBeInTheDocument()
    // 指标卡
    expect(screen.getByText('待整理资产')).toBeInTheDocument()
    expect(screen.getByText('本周到期任务')).toBeInTheDocument()
    expect(screen.getByText('已逾期')).toBeInTheDocument()
    expect(screen.getByText('已标准化')).toBeInTheDocument()
    // 六节点轨道
    const rail = screen.getByLabelText(/治理轨道/)
    for (const label of ['扫描入库', '问题池', '整改分派', '业务确认', '质量验收', '正式应用']) {
      expect(within(rail).getByText(label)).toBeInTheDocument()
    }
    // 状态文字不依赖颜色：当前阶段（业务确认）文字呈现
    expect(await screen.findByText(/当前步骤 · 待业务确认 421 条/)).toBeInTheDocument()
    // 默认面板联动业务确认待办，逾期任务带文字说明
    const confirmPanel = todoPanel('业务确认 · 待确认清单')
    expect(within(confirmPanel).getByText('历史专业类别标准化')).toBeInTheDocument()
    expect(within(confirmPanel).getByText(/已逾期/)).toBeInTheDocument()
  })

  it('点击/键盘选中节点联动"该步待办"；只看逾期过滤生效', async () => {
    const user = userEvent.setup()
    renderHome()
    await screen.findByText('业务确认 · 待确认清单')

    // 问题池节点（包含文字状态"待分派"）
    const poolButton = await screen.findByRole('button', { name: /问题池/ })
    expect(poolButton).toHaveAttribute('aria-pressed', 'false')
    await user.click(poolButton)
    const poolPanel = await screen.findByText('问题池 · 待分派')
    expect(poolPanel).toBeInTheDocument()

    // 整改分派节点：Tab 聚焦 + Enter 选中（键盘可达）
    const assignButton = screen.getByRole('button', { name: /整改分派/ })
    assignButton.focus()
    await user.keyboard('{Enter}')
    const assignPanel = todoPanel('整改分派 · 整改待办')
    expect(within(assignPanel).getByText('A 拉线范围补充')).toBeInTheDocument()

    // 只看逾期：整改分派含逾期任务行，勾选后仅保留逾期
    const overdueFilter = screen.getByRole('button', { name: '只看逾期' })
    await user.click(overdueFilter)
    expect(within(assignPanel).getByText('仅显示逾期条目')).toBeInTheDocument()
    expect(within(assignPanel).getByText('A 拉线范围补充')).toBeInTheDocument()
  })

  it('取数失败时优雅降级：指标显示「数据暂不可用」并给出弱提示，不崩溃', async () => {
    mockFetch(true)
    renderHome()
    expect(await screen.findByText('部分数据未加载成功')).toBeInTheDocument()
    expect((await screen.findAllByText('数据暂不可用')).length).toBeGreaterThanOrEqual(4)
    // 轨道仍渲染六个节点
    expect(screen.getByLabelText(/治理轨道/)).toBeInTheDocument()
  })
})
