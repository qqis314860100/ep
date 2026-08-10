import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as governanceApi from '../api'
import type { GovernanceOperationsOverview } from '../types'
import { GovernanceOperationsPage } from './GovernanceOperationsPage'

vi.mock('../api', async importOriginal => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceOperationsOverview: vi.fn(),
  getGovernanceStandards: vi.fn(),
  getGovernanceEmployees: vi.fn(),
}))

const overview: GovernanceOperationsOverview = {
  filter: {},
  assetCount: 5,
  coveredAssetCount: 4,
  openIssueCount: 5,
  overdueTaskCount: 1,
  metrics: [
    { key: 'responsibilityCoverage', label: '责任覆盖率', value: 0.8, numerator: 4, denominator: 5, available: true, unit: '%', source: '资产' },
    { key: 'issueClosureCycle', label: '平均问题关闭周期', value: null, numerator: 0, denominator: 0, available: false, unit: '天', source: '暂无创建和解决时间' },
  ],
  issuesByType: [{ key: 'MISSING_DESCRIPTION', count: 2 }],
  overdueTasks: [{ taskId: 2, taskName: '历史专业类别标准化', ownerName: '李工', dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION' }],
  cadences: [{ key: 'DAILY_SCAN', name: '每日问题扫描', ownerRole: '内容管理员', status: 'ON_TRACK', nextDueAt: '2026-08-11T00:00:00Z', evidence: '最近成功运行 #1' }],
  generatedAt: '2026-08-10T00:00:00Z',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><App><GovernanceOperationsPage /></App></QueryClientProvider>)
}

describe('GovernanceOperationsPage', () => {
  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceOperationsOverview).mockResolvedValue(overview)
    vi.mocked(governanceApi.getGovernanceStandards).mockResolvedValue([])
    vi.mocked(governanceApi.getGovernanceEmployees).mockResolvedValue([])
  })

  it('shows operational metrics and cadence evidence', async () => {
    renderPage()
    expect(await screen.findByText('治理运营')).toBeInTheDocument()
    expect(await screen.findByText('80.0%')).toBeInTheDocument()
    expect(screen.getByText('平均问题关闭周期暂不可用')).toBeInTheDocument()
    expect(screen.getByText('每日问题扫描')).toBeInTheDocument()
  })

  it('sends selected filters back to the operational query', async () => {
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('治理运营')
    await user.click(screen.getByRole('button', { name: 'filter应用筛选' }))
    await waitFor(() => expect(governanceApi.getGovernanceOperationsOverview).toHaveBeenCalledWith({}))
  })
})
