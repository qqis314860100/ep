import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as governanceApi from '../api'
import { GovernanceApiError } from '../types'
import { GovernanceTaskDetailPage } from './GovernanceTaskDetailPage'

vi.mock('../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceTask: vi.fn(),
  getGovernanceIssues: vi.fn(),
  getGovernancePlans: vi.fn(),
  getGovernanceEmployees: vi.fn(),
  startGovernanceTask: vi.fn(),
  openGovernanceRework: vi.fn(),
}))

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><App><MemoryRouter><GovernanceTaskDetailPage taskId={9} /></MemoryRouter></App></QueryClientProvider>)
}

describe('GovernanceTaskDetailPage', () => {
  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', scopeSnapshot: { issueIds: [1001] }, owner: '王工', assigneeId: 'owner-1', total: 1, completed: 0, dueDate: '2026-08-10', status: 'DRAFT', version: 2, editable: true })
    vi.mocked(governanceApi.getGovernanceIssues).mockResolvedValue([])
    vi.mocked(governanceApi.getGovernancePlans).mockResolvedValue([])
    vi.mocked(governanceApi.getGovernanceEmployees).mockResolvedValue([])
    vi.mocked(governanceApi.startGovernanceTask).mockRejectedValue(new GovernanceApiError(422, 'governance_validation_failed', '治理项不能重复分配', []))
  })

  it('shows the backend start validation without unlocking the plan', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('button', { name: '启动任务' }))

    expect(await screen.findByText('治理项不能重复分配')).toBeVisible()
    expect(screen.getByRole('button', { name: '编辑计划' })).toBeEnabled()
  })

  it('opens a new rework round before entering execution', async () => {
    const user = userEvent.setup()
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', assigneeId: 'owner-1', total: 1, completed: 0, dueDate: '2026-08-10', status: 'REWORK_REQUIRED', currentRound: 1, version: 3, editable: false })
    vi.mocked(governanceApi.openGovernanceRework).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', assigneeId: 'owner-1', total: 1, completed: 0, dueDate: '2026-08-10', status: 'IN_PROGRESS', currentRound: 2, version: 4, editable: false })
    renderPage()

    await user.click(await screen.findByRole('button', { name: '开启返工' }))

    expect(governanceApi.openGovernanceRework).toHaveBeenCalledWith(9, { taskVersion: 3, reason: '业务确认退回', actorUserId: 'demo-user' })
  })
})
