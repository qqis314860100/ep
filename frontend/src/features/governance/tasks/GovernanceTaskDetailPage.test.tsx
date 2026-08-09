import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
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
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', assigneeId: 'owner-1', total: 1, completed: 0, dueDate: '2026-08-10', status: 'DRAFT', version: 2, editable: true })
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

  it('does not offer closed-loop confirmation for a legacy progress task', async () => {
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '历史专业类别标准化', scope: '机械、电气自由文本', owner: '李工', total: 421, completed: 421, dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION', workflowVersion: 'LEGACY_PROGRESS', editable: false })
    vi.mocked(governanceApi.getGovernancePlans).mockResolvedValue([
      { id: 1, taskId: 9, title: '历史资料整理', status: 'DONE', plannedStart: '2026-07-27', plannedEnd: '2026-07-29', plannedQuantity: 421, completedQuantity: 421, quantityUnit: '资产', dependencyIds: [] },
    ])

    renderPage()

    expect(await screen.findByText('历史任务只读')).toBeVisible()
    expect(screen.queryByRole('button', { name: '进入确认' })).not.toBeInTheDocument()
    expect(screen.getByText('历史汇总任务')).toBeVisible()
    expect(screen.queryByText('业务确认')).not.toBeInTheDocument()
    expect(screen.getByRole('img', { name: /历史资料整理.*100%/ })).toBeVisible()
  })

  it('switches from the existing table to the Gantt view without changing data', async () => {
    const user = userEvent.setup()
    vi.mocked(governanceApi.getGovernancePlans).mockResolvedValue([
      { id: 1, taskId: 9, title: '字段清洗', status: 'IN_PROGRESS', plannedStart: '2026-07-27', plannedEnd: '2026-07-29', plannedQuantity: 4, completedQuantity: 2, quantityUnit: '字段', responsibleUserId: 'owner-1', dependencyIds: [] },
    ])
    vi.mocked(governanceApi.getGovernanceEmployees).mockResolvedValue([
      { id: 'owner-1', name: '王工', department: '数据部', source: 'dev' },
    ])
    renderPage()

    expect(await screen.findByRole('columnheader', { name: '计划项' })).toBeVisible()
    await user.click(screen.getByText('甘特图'))

    expect(screen.getByRole('img', { name: /字段清洗.*50%/ })).toBeVisible()
    expect(screen.queryByRole('columnheader', { name: '计划项' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '编辑计划' }))
    expect(screen.getByRole('columnheader', { name: '计划项' })).toBeVisible()
  })

  it('defaults an active closed-loop task to the Gantt view', async () => {
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '资料征集', scope: '部门文件', owner: '王工', total: 4, completed: 2, dueDate: '2026-08-10', status: 'IN_PROGRESS', workflowVersion: 'CLOSED_LOOP_V1', editable: false })
    vi.mocked(governanceApi.getGovernancePlans).mockResolvedValue([
      { id: 1, taskId: 9, title: '文件回收', status: 'IN_PROGRESS', plannedStart: '2026-08-01', plannedEnd: '2026-08-10', plannedQuantity: 4, completedQuantity: 2, quantityUnit: '文件', dependencyIds: [] },
    ])

    renderPage()

    expect(await screen.findByRole('img', { name: /文件回收.*50%/ })).toBeVisible()
    expect(screen.queryByRole('columnheader', { name: '计划项' })).not.toBeInTheDocument()
  })

  it('shows a business baseline and keeps the raw snapshot collapsed', async () => {
    const user = userEvent.setup()
    const ruleSnapshot = { id: 31, dataStandardId: 'ASSET-STD', dataStandardVersion: 3, fieldRuleVersion: 2, dictionaryVersions: { specialty: 4 }, qualityPolicyId: 'QUALITY-A', qualityPolicyVersion: 2 }
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({
      id: 9, name: '资料征集', scope: '部门文件', owner: '王工', total: 2, completed: 0, dueDate: '2026-08-10', status: 'IN_PROGRESS', workflowVersion: 'CLOSED_LOOP_V1', editable: false,
      scopeSnapshot: { id: 21, taskId: 9, claimedIssueIds: [1001, 1002], assetIds: [501], ruleSnapshot, createdBy: 'admin-1', frozenAt: '2026-08-01T09:00:00Z', itemCount: 2 },
      ruleSnapshot,
    })

    renderPage()

    expect(await screen.findByText('ASSET-STD · v3')).toBeVisible()
    expect(screen.getByText('QUALITY-A · v2')).toBeVisible()
    expect(screen.queryByText(/"claimedIssueIds"/)).not.toBeInTheDocument()

    await user.click(screen.getByText('查看技术快照'))
    await waitFor(() => expect(screen.getByText(/"claimedIssueIds"/)).toBeVisible())
  })

  it('keeps the empty issue section compact and orders workflow before plans', async () => {
    renderPage()

    const workflow = await screen.findByText('数据资产治理闭环')
    const plansHeading = screen.getByRole('heading', { name: '计划依赖与责任' })
    const progressHeading = screen.getByRole('heading', { name: '阶段进度' })
    expect(workflow.compareDocumentPosition(plansHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(plansHeading.compareDocumentPosition(progressHeading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(screen.getByText('当前没有关联治理问题')).toBeVisible()
    expect(screen.queryByRole('columnheader', { name: '问题 ID' })).not.toBeInTheDocument()
  })

  it('shows a plan loading failure instead of an empty plan state', async () => {
    vi.mocked(governanceApi.getGovernancePlans).mockRejectedValue(new Error('计划服务不可用'))
    renderPage()

    expect(await screen.findByText('计划服务不可用')).toBeVisible()
    expect(screen.queryByText('尚未编排计划项')).not.toBeInTheDocument()
  })

  it('keeps plan editing locked after the task starts', async () => {
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', total: 1, completed: 0, dueDate: '2026-08-10', status: 'IN_PROGRESS', editable: false })
    renderPage()

    expect(await screen.findByRole('button', { name: '编辑计划' })).toBeDisabled()
    expect(screen.getByText('任务启动后计划已锁定')).toBeVisible()
  })
})
