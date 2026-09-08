import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as governanceApi from '../api'
import type { GovernanceResponsibilityBoard, GovernanceTask } from '../types'
import { GovernanceResponsibilityPage } from './GovernanceResponsibilityPage'

vi.mock('../api', async importOriginal => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceResponsibilityBoard: vi.fn(),
  getGovernanceTasks: vi.fn(),
}))

const board: GovernanceResponsibilityBoard = {
  month: '2026-09',
  generatedAt: '2026-09-09T02:00:00Z',
  employees: [
    { userId: 'emp-li', name: '李工', department: '标准化小组', executing: 2, confirming: 1, accepting: 1, overdue: 3, escalated: 1, completed: 1, totalAssigned: 5 },
    { userId: 'emp-wang', name: '王工', department: '资料管理组', executing: 0, confirming: 0, accepting: 0, overdue: 0, escalated: 0, completed: 2, totalAssigned: 2 },
    { userId: 'emp-chen', name: '陈工', department: '制造工程部', executing: 1, confirming: 0, accepting: 0, overdue: 1, escalated: 0, completed: 0, totalAssigned: 1 },
  ],
  totals: { userId: '', name: '合计', department: '', executing: 3, confirming: 1, accepting: 1, overdue: 4, escalated: 1, completed: 3, totalAssigned: 8 },
  monthly: {
    month: '2026-09', prevMonth: '2026-08', scanRuns: 2, scannedAssets: 120, newIssues: 6,
    closedTasks: 2, openIssues: 4, overdueTasks: 4, escalatedTasks: 1,
    prevScanRuns: 1, prevScannedAssets: 60, prevNewIssues: 3, prevClosedTasks: 1,
  },
}

const openTasks: GovernanceTask[] = [
  { id: 42, name: '设备台账清洗', scope: '问题池选择', owner: '李工', total: 1, completed: 0, dueDate: '2026-09-04', status: 'IN_PROGRESS', workflowVersion: 'CLOSED_LOOP_V1', editable: false },
  { id: 43, name: '历史专业类别标准化', scope: '历史数据', owner: '李工', total: 1, completed: 0, dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION', workflowVersion: 'LEGACY_PROGRESS', editable: false },
]

function monthKey(date: Date, offsetMonths: number): string {
  const copy = new Date(date.getFullYear(), date.getMonth() - offsetMonths, 1)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${copy.getFullYear()}-${pad(copy.getMonth() + 1)}`
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/sys/drawing/responsibility']}>
        <Routes>
          <Route path="/sys/drawing/responsibility" element={<GovernanceResponsibilityPage />} />
          <Route path="/sys/drawing/tasks/:taskId" element={<div>任务详情页</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('GovernanceResponsibilityPage（R4 责任看板）', () => {
  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceResponsibilityBoard).mockResolvedValue(board)
    vi.mocked(governanceApi.getGovernanceTasks).mockResolvedValue(openTasks)
  })

  it('renders monthly review cards and per-employee responsibility rows', async () => {
    renderPage()

    expect(await screen.findByText('组织与责任看板')).toBeInTheDocument()
    expect(screen.getByText('本月治理复盘')).toBeInTheDocument()
    expect(await screen.findByText('本月闭环完成')).toBeInTheDocument()
    expect(screen.getByText('当前逾期任务')).toBeInTheDocument()

    const liRow = screen.getByText('李工').closest('tr')
    expect(liRow).not.toBeNull()
    expect(within(liRow as HTMLElement).getByText('升级 1')).toBeInTheDocument()
    expect(screen.getByText('部门合计')).toBeInTheDocument()
    expect(screen.getByText(/口径说明：/)).toBeInTheDocument()
  })

  it('switches the review month and refetches the board', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('组织与责任看板')

    await user.click(screen.getByRole('combobox', { name: '复盘月份' }))
    const prevLabel = `${monthKey(new Date(), 1)} 复盘`
    await user.click(await screen.findByText(prevLabel))

    await waitFor(() => expect(governanceApi.getGovernanceResponsibilityBoard)
      .toHaveBeenCalledWith(monthKey(new Date(), 1)))
  })

  it('expands a person to list open tasks and opens a task detail', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('组织与责任看板')

    const nameCell = await screen.findByText('李工')
    await user.click(nameCell)

    expect(await screen.findByText('设备台账清洗')).toBeInTheDocument()
    expect(screen.getByText('历史专业类别标准化')).toBeInTheDocument()

    const openButtons = screen.getAllByRole('button', { name: '进入任务' })
    await user.click(openButtons[0])

    expect(await screen.findByText('任务详情页')).toBeInTheDocument()
  })
})
