import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as governanceApi from '../api'
import type { ConfirmationView, GovernanceItemExecution } from '../types'
import { GovernanceConfirmationPage } from './GovernanceConfirmationPage'

vi.mock('../api', async importOriginal => ({ ...await importOriginal<typeof import('../api')>(), getGovernanceTask: vi.fn(), getCurrentConfirmation: vi.fn(), getGovernanceItems: vi.fn(), saveConfirmationDecision: vi.fn(), completeConfirmation: vi.fn() }))

const view: ConfirmationView = { round: { id: 21, taskId: 9, governanceRound: 1, resultVersionIds: { '101': 11, '102': 12 }, status: 'PENDING', createdAt: '2026-07-26T00:00:00Z', completedAt: null, version: 0 }, items: [{ itemId: 101, assetId: 501, resultVersionId: 11, resultType: 'DESCRIPTION', responsibleUserId: 'owner-1', responsibilityScope: 'scope-a' }, { itemId: 102, assetId: 502, resultVersionId: 12, resultType: 'DESCRIPTION', responsibleUserId: 'owner-1', responsibilityScope: 'scope-a' }], decisions: [], coveredCount: 0, approvedCount: 0, coverageRate: 0, approvalRate: 0 }
const execution = (id: number): GovernanceItemExecution => ({ item: { id, taskId: 9, planId: 1, issueId: id + 900, assetId: id + 400, targetField: 'DESCRIPTION', actionType: 'NORMALIZE', responsibleUserId: 'owner-1', status: 'SUBMITTED', assetVersion: 1, governanceRound: 1, scopeFingerprint: 'scope-a', version: 1, currentResultVersionId: id - 90, blockReason: null, reworkSourceItemId: null }, currentResult: { id: id - 90, itemId: id, governanceRound: 1, resultVersion: 1, field: 'DESCRIPTION', originalValueJson: '旧说明', proposedValue: '标准说明', standardVersion: 7, dictionaryVersions: {}, status: 'SUBMITTED', reworkReason: '', actorUserId: 'worker-1', savedAt: '', submittedAt: '', version: 0 }, originalFactJson: '旧说明', ruleContext: { source: '标准 V7' }, blockReason: null, reworkSourceItemId: null })

function renderPage() { const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); return render(<QueryClientProvider client={client}><App><GovernanceConfirmationPage taskId={9} /></App></QueryClientProvider>) }

describe('GovernanceConfirmationPage', () => {
  afterEach(cleanup)
  beforeEach(() => { vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', total: 2, completed: 2, dueDate: '2026-08-10', status: 'PENDING_CONFIRMATION', workflowVersion: 'CLOSED_LOOP_V1' }); vi.mocked(governanceApi.getCurrentConfirmation).mockResolvedValue(view); vi.mocked(governanceApi.getGovernanceItems).mockResolvedValue([execution(101), execution(102)]); vi.mocked(governanceApi.saveConfirmationDecision).mockImplementation(async (_round, itemId, command) => ({ ...view, decisions: [{ id: 1, roundId: 21, itemId, resultVersionId: 11, decision: command.decision, comment: command.comment ?? '', confirmerUserId: command.confirmerUserId, decidedAt: '', version: 0 }], coveredCount: 1, approvedCount: 0, coverageRate: 0.5, approvalRate: 0 })) })

  it('keeps legacy progress tasks out of the closed-loop confirmation requests', async () => {
    vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '历史专业类别标准化', scope: '机械、电气自由文本', owner: '李工', total: 421, completed: 421, dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION', workflowVersion: 'LEGACY_PROGRESS' })

    renderPage()

    expect(await screen.findByText('历史任务仅保留汇总进度，不支持业务确认明细')).toBeVisible()
    expect(governanceApi.getCurrentConfirmation).not.toHaveBeenCalled()
    expect(governanceApi.getGovernanceItems).not.toHaveBeenCalled()
  })
  it('requires a reason for rejection and keeps partial decisions', async () => {
    const user = userEvent.setup(); renderPage()
    await user.click(await screen.findByText('退回当前项'))
    await user.click(screen.getByRole('button', { name: '保存决定' }))
    expect(await screen.findByText('请填写退回意见')).toBeVisible()
    await user.type(screen.getByLabelText('退回意见'), '负责人不符合实际责任范围')
    await user.click(screen.getByRole('button', { name: '保存决定' }))
    expect(await screen.findByText('确认覆盖率 1 / 2')).toBeVisible()
    expect(governanceApi.saveConfirmationDecision).toHaveBeenCalledWith(21, 101, expect.objectContaining({ confirmerUserId: 'owner-1' }))
  })
})
