import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as governanceApi from '../api'
import { GovernanceIssuePoolPage } from './GovernanceIssuePoolPage'

vi.mock('../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceIssues: vi.fn(),
  createGovernanceTask: vi.fn(),
  getGovernanceEmployees: vi.fn(),
}))

const issues = [
  { id: 1001, assetId: 41, targetField: 'DESCRIPTION' as const, issueType: 'MISSING', targetPath: 'description', originalFactJson: '{}', severity: 'HIGH', blocking: true, status: 'OPEN' as const, taskId: null, version: 0 },
  { id: 1002, assetId: 42, targetField: 'OWNER' as const, issueType: 'MISSING', targetPath: 'owner', originalFactJson: '{}', severity: 'MEDIUM', blocking: false, status: 'OPEN' as const, taskId: null, version: 0 },
]

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><App><GovernanceIssuePoolPage /></App></QueryClientProvider>)
}

describe('GovernanceIssuePoolPage', () => {
  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceIssues).mockResolvedValue(issues)
    vi.mocked(governanceApi.getGovernanceEmployees).mockResolvedValue([{ id: 'owner-1', name: '王工', department: '内容管理', source: 'LOCAL' }])
    vi.mocked(governanceApi.createGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', total: 2, completed: 0, dueDate: '2026-08-10', status: 'DRAFT' })
  })

  it('creates a task from selected issue ids without a manual total', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('checkbox', { name: '选择问题 1001' }))
    await user.click(screen.getByRole('checkbox', { name: '选择问题 1002' }))
    await user.click(screen.getByRole('button', { name: '创建治理任务' }))
    await user.type(screen.getByLabelText('任务名称'), '字段治理')
    await user.click(screen.getByLabelText('负责人'))
    await user.click(await screen.findByText('王工'))
    await user.type(screen.getByLabelText('截止日期'), '2026-08-10')
    await user.click(screen.getByRole('button', { name: '确认创建' }))

    expect(governanceApi.createGovernanceTask).toHaveBeenCalledWith(expect.objectContaining({ issueIds: [1001, 1002] }))
    expect(screen.queryByLabelText('计划总量')).not.toBeInTheDocument()
  })
})
