import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as governanceApi from '../api'
import type { GovernanceDataStandard } from '../types'
import { GovernanceStandardsPage } from './GovernanceStandardsPage'

vi.mock('../api', async (importOriginal) => ({
  ...await importOriginal<typeof import('../api')>(),
  getGovernanceStandards: vi.fn(),
  getGovernanceStandardImpactReviews: vi.fn(),
  createGovernanceStandardVersion: vi.fn(),
  createGovernanceStandard: vi.fn(),
  enableGovernanceStandard: vi.fn(),
  disableGovernanceStandard: vi.fn(),
}))

const standard: GovernanceDataStandard = {
  id: 1,
  standardCode: 'FIELD-COMPLETENESS',
  standardVersion: 1,
  name: '数模资产完整性标准',
  status: 'ENABLED',
  applicableAssetTypes: ['THREE_DIMENSIONAL_MODEL'],
  ownerUserId: 'emp-zhang',
  ownerName: '张伟',
  effectiveAt: '2026-08-01T00:00:00Z',
  changeSummary: '建立治理基线',
  affectedAssetCount: 2,
  rules: [{ targetField: 'scope', ruleType: 'REQUIRED', description: '范围必填', blocking: true, configurationJson: '{}' }],
  version: 0,
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(<QueryClientProvider client={client}><App><GovernanceStandardsPage /></App></QueryClientProvider>)
}

describe('GovernanceStandardsPage', () => {
  beforeEach(() => {
    vi.mocked(governanceApi.getGovernanceStandards).mockResolvedValue([standard])
    vi.mocked(governanceApi.getGovernanceStandardImpactReviews).mockResolvedValue([])
    vi.mocked(governanceApi.createGovernanceStandardVersion).mockResolvedValue({
      ...standard, id: 2, standardVersion: 2, status: 'DRAFT', effectiveAt: null, version: 0,
    })
  })

  it('creates a separate draft version from the selected standard', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('FIELD-COMPLETENESS · V1')
    await user.click(screen.getByRole('button', { name: '新建版本' }))
    expect(screen.getByLabelText('标准编码')).toBeDisabled()
    expect(screen.getByLabelText('业务版本')).toHaveValue('2')
    await user.type(screen.getByLabelText('变更说明'), '增加文件角色门槛')
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    await waitFor(() => expect(governanceApi.createGovernanceStandardVersion).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ standardVersion: 2, changeSummary: '增加文件角色门槛' }),
    ))
  })

  it('shows the enabled version and its frozen-version rule', async () => {
    renderPage()
    await screen.findByText('FIELD-COMPLETENESS · V1')
    expect(screen.getByText('版本规则')).toBeInTheDocument()
    expect(screen.getByText(/新任务启动时冻结当时的启用版本/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '停用' })).toBeEnabled()
  })
})
