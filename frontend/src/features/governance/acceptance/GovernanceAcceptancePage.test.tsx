import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as governanceApi from '../api'
import type { AcceptanceRound } from '../types'
import { GovernanceAcceptancePage } from './GovernanceAcceptancePage'

vi.mock('../api', async importOriginal => ({ ...await importOriginal<typeof import('../api')>(), getCurrentAcceptance: vi.fn(), saveAcceptanceSample: vi.fn(), completeAcceptance: vi.fn(), getOperationJob: vi.fn(), retryOperationJob: vi.fn(), openGovernanceRework: vi.fn() }))
const metrics = ['REQUIRED_FIELD_COMPLETENESS', 'ASSET_SCOPE_VALIDITY', 'STANDARD_DICTIONARY_HIT_RATE', 'OWNER_COVERAGE', 'SAMPLE_ACCURACY'] as const
const round: AcceptanceRound = { id: 31, taskId: 9, governanceRound: 1, policy: {}, metricResults: metrics.map((metric, index) => ({ id: index + 1, roundId: 31, metric, numerator: 2, denominator: 2, value: 1, threshold: 0.9, applicability: 'APPLICABLE', passed: true, affectedItemIds: [], version: 0 })), samples: [{ id: 1, roundId: 31, itemId: 101, passed: false, issueDescription: '功能说明与现场用途不符', reviewerUserId: 'qa-1', checkedAt: '', version: 0 }], status: 'OPEN', createdAt: '', completedAt: null, version: 2 }
function renderPage() { const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } }); return render(<QueryClientProvider client={client}><App><GovernanceAcceptancePage taskId={9} /></App></QueryClientProvider>) }

describe('GovernanceAcceptancePage', () => {
  afterEach(cleanup)
  beforeEach(() => { vi.mocked(governanceApi.getCurrentAcceptance).mockResolvedValue(round); vi.mocked(governanceApi.completeAcceptance).mockResolvedValue({ taskStatus: 'PENDING_ACCEPTANCE', affectedItemIds: [], applicationJobId: 41 }); vi.mocked(governanceApi.getOperationJob).mockResolvedValue({ jobId: 41, taskId: 9, total: 2, succeeded: 2, failed: 0, processing: 0, errors: {}, retryable: false }) })
  it('keeps failed samples visible and polls the application job', async () => {
    const user = userEvent.setup(); renderPage()
    expect(await screen.findByText('抽样准确率')).toBeVisible()
    expect(screen.getByDisplayValue('功能说明与现场用途不符')).toBeVisible()
    await user.click(screen.getByRole('button', { name: '通过并正式应用' }))
    expect(await screen.findByText('正式应用完成')).toBeVisible()
    expect(screen.getByText('2 / 2 已应用')).toBeVisible()
  })
})
