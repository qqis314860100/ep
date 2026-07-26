import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import * as governanceApi from '../api'
import { GovernanceApiError, type GovernanceItemExecution } from '../types'
import { GovernanceExecutionPage } from './GovernanceExecutionPage'

vi.mock('../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceItems: vi.fn(),
  getGovernanceEmployees: vi.fn(),
  saveResultDraft: vi.fn(),
  saveBatchResults: vi.fn(),
  getGovernanceTask: vi.fn(),
  submitForConfirmation: vi.fn(),
}))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn().mockResolvedValue([]) }))

const items: GovernanceItemExecution[] = [
  {
    item: { id: 101, taskId: 9, planId: 1, issueId: 1001, assetId: 501, targetField: 'DESCRIPTION', actionType: 'NORMALIZE', responsibleUserId: 'owner-1', status: 'PROCESSING', assetVersion: 3, governanceRound: 1, scopeFingerprint: 'scope-a', version: 2, currentResultVersionId: 11, blockReason: null, reworkSourceItemId: null },
    currentResult: { id: 11, itemId: 101, governanceRound: 1, resultVersion: 1, field: 'DESCRIPTION', originalValueJson: '旧功能说明', proposedValue: '已保存说明', standardVersion: 7, dictionaryVersions: {}, status: 'DRAFT', reworkReason: '', actorUserId: 'owner-1', savedAt: '2026-07-26T10:00:00Z', submittedAt: null, version: 1 },
    originalFactJson: '旧功能说明',
    ruleContext: { standardVersion: 7, scope: '平台 A / 蓝本 H03', history: ['首次治理'] },
    blockReason: null,
    reworkSourceItemId: null,
  },
  ...(['SUCCESS', 'CONFLICT'] as const).map((_, index): GovernanceItemExecution => ({
    item: { id: 102 + index, taskId: 9, planId: 1, issueId: 1002 + index, assetId: 502 + index, targetField: 'DESCRIPTION', actionType: 'NORMALIZE', responsibleUserId: 'owner-1', status: 'PENDING', assetVersion: 1, governanceRound: 1, scopeFingerprint: 'scope-a', version: 1, currentResultVersionId: null, blockReason: null, reworkSourceItemId: null },
    currentResult: null,
    originalFactJson: `旧说明 ${index + 2}`,
    ruleContext: { standardVersion: 7 },
    blockReason: null,
    reworkSourceItemId: null,
  })),
]

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<MemoryRouter><QueryClientProvider client={client}><App><GovernanceExecutionPage taskId={9} /></App></QueryClientProvider></MemoryRouter>)
}

describe('GovernanceExecutionPage', () => {
  afterEach(cleanup)

  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceItems).mockResolvedValue(items)
    vi.mocked(governanceApi.getGovernanceEmployees).mockResolvedValue([{ id: 'owner-1', name: '王工', department: '内容管理', source: 'LOCAL' }])
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '治理任务', scope: 'FIELD_SUPPLEMENT', owner: '王工', assigneeId: 'owner-1', total: 3, completed: 0, dueDate: '2026-08-15', status: 'IN_PROGRESS', workflowVersion: 'CLOSED_LOOP_V1', currentRound: 1, version: 3, editable: true, progress: null, riskCount: 0 })
    vi.mocked(governanceApi.saveResultDraft).mockRejectedValue(new GovernanceApiError(409, 'governance_version_conflict', '资产已被其他用户更新', []))
    vi.mocked(governanceApi.saveBatchResults).mockResolvedValue({ results: [
      { itemId: 101, outcome: 'SUCCESS', resultVersionId: 21 },
      { itemId: 102, outcome: 'CONFLICT', message: '资产版本冲突', currentVersion: 4 },
      { itemId: 103, outcome: 'VALIDATION_FAILED', message: '拟值不能为空' },
    ] })
  })

  it('keeps the current item and reloads only the conflicted result', async () => {
    const user = userEvent.setup()
    renderPage()

    const editor = await screen.findByLabelText('拟变更功能说明')
    await user.clear(editor)
    await user.type(editor, '标准功能说明')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(governanceApi.saveResultDraft).toHaveBeenCalledWith(101, expect.objectContaining({
      proposedValue: { description: '标准功能说明' },
    }))
    expect(await screen.findByText('资产已被其他用户更新')).toBeVisible()
    expect(screen.getByRole('button', { name: '刷新当前项' })).toBeVisible()
    expect(editor).toHaveValue('标准功能说明')

    vi.mocked(governanceApi.getGovernanceItems).mockResolvedValue([{ ...items[0], currentResult: { ...items[0].currentResult!, proposedValue: '他人最新说明', version: 4 } }, ...items.slice(1)])
    await user.click(screen.getByRole('button', { name: '刷新当前项' }))
    expect(await screen.findByDisplayValue('他人最新说明')).toBeVisible()
    expect(governanceApi.getGovernanceItems).toHaveBeenCalledTimes(2)
  })

  it('shows every batch outcome instead of one aggregate toast', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByRole('checkbox', { name: '选择治理项 101' }))
    await user.click(screen.getByRole('checkbox', { name: '选择治理项 102' }))
    await user.click(screen.getByRole('checkbox', { name: '选择治理项 103' }))
    await user.click(screen.getByRole('button', { name: '批量提交' }))

    expect(await screen.findByText('成功 1')).toBeVisible()
    expect(screen.getByText('冲突 1')).toBeVisible()
    expect(screen.getByText('校验失败 1')).toBeVisible()
    expect(screen.getByText('资产版本冲突')).toBeVisible()
    expect(screen.getByText('拟值不能为空')).toBeVisible()
  })

  it('submits the task for confirmation after every item is submitted', async () => {
    const user = userEvent.setup()
    vi.mocked(governanceApi.getGovernanceItems).mockResolvedValue(items.map(entry => ({
      ...entry,
      item: { ...entry.item, status: 'SUBMITTED' },
    })))
    vi.mocked(governanceApi.submitForConfirmation).mockResolvedValue({
      id: 9, name: '治理任务', scope: 'FIELD_SUPPLEMENT', owner: '王工', assigneeId: 'owner-1', total: 3, completed: 3, dueDate: '2026-08-15', status: 'PENDING_CONFIRMATION', workflowVersion: 'CLOSED_LOOP_V1', currentRound: 1, version: 4, editable: false, progress: null, riskCount: 0,
    })
    renderPage()

    await user.click(await screen.findByRole('button', { name: '提交确认' }))

    expect(governanceApi.submitForConfirmation).toHaveBeenCalledWith(9, 3)
  })
})
