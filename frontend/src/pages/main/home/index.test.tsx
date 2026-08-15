import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getGovernanceIssues, getGovernanceScanRuns, getGovernanceTasks } from '../../../features/governance/api'
import { getFavoriteAssets, getMyUploads, searchAssets } from '../../../services/assetService'
import HomePage from './index'

vi.mock('../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/assetService')>(),
  getFavoriteAssets: vi.fn(),
  getMyUploads: vi.fn(),
  searchAssets: vi.fn(),
}))
vi.mock('../../../features/governance/api', () => ({
  getGovernanceIssues: vi.fn(),
  getGovernanceScanRuns: vi.fn(),
  getGovernanceTasks: vi.fn(),
}))

const upload = {
  id: 11, assetNumber: 'M-2026-0001', name: '底部水冷模组', description: '', assetType: 'MODULE' as const,
  status: 'PENDING_CURATION' as const, specialties: [], tags: [], moduleTags: [], standardEquipmentModule: false,
  linkedModuleAssetIds: [], equipmentInterconnectCode: '', scopes: [], files: [], ownerName: '陈工',
  ownerDepartment: '', updatedAt: '2026-08-01T00:00:00Z', legacy: false,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><HomePage /></MemoryRouter></QueryClientProvider>)
}

describe('HomePage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getFavoriteAssets).mockResolvedValue([upload])
    vi.mocked(getMyUploads).mockResolvedValue({ data: [upload], meta: { total: 1, page: 1, perPage: 20, totalPages: 1 } })
    vi.mocked(searchAssets).mockResolvedValue({ data: [], meta: { total: 3, page: 1, perPage: 1, totalPages: 3 } })
    vi.mocked(getGovernanceIssues).mockResolvedValue([
      { id: 1, assetId: 11, targetField: 'DESCRIPTION', issueType: 'MISSING_REQUIRED_FIELD', targetPath: '/description', originalFactJson: '{}', severity: 'HIGH', blocking: true, status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' },
      { id: 2, assetId: 11, targetField: 'DESCRIPTION', issueType: 'ANOMALOUS_FILE', targetPath: '/files', originalFactJson: '{}', severity: 'MEDIUM', blocking: false, status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' },
    ])
    vi.mocked(getGovernanceTasks).mockResolvedValue([
      { id: 9, name: '字段治理任务', scope: '问题池选择', owner: '陈工', total: 2, completed: 0, dueDate: '2026-08-15', status: 'IN_PROGRESS' },
    ])
    vi.mocked(getGovernanceScanRuns).mockResolvedValue([
      { id: 1, triggerType: 'MANUAL', status: 'SUCCEEDED', startedAt: '2026-08-01T00:00:00Z', finishedAt: '2026-08-01T00:00:10Z', scannedAssetCount: 5, createdIssueCount: 2, reopenedIssueCount: 0, unchangedIssueCount: 3, errorMessage: '', retryOfRunId: null, version: 0 },
    ])
  })

  it('renders metrics, todos, quick entries, and governance overview', async () => {
    renderPage()

    expect(await screen.findByText('工作台')).toBeInTheDocument()
    expect(screen.getByText('待整理资料')).toBeInTheDocument()
    expect(screen.getByText('需补充信息')).toBeInTheDocument()
    expect(screen.getByText('异常文件')).toBeInTheDocument()
    expect(screen.getAllByText('开放治理问题').length).toBeGreaterThan(0)
    expect(screen.getByText('待办清单')).toBeInTheDocument()
    expect(screen.getByText('快捷入口')).toBeInTheDocument()
    expect(screen.getByText('治理概览')).toBeInTheDocument()
    expect(await screen.findByText('最近扫描成功')).toBeInTheDocument()
    expect(screen.getByText('文档中心')).toBeInTheDocument()
  })

  it('shows upcoming task count when a task is due within 7 days', async () => {
    renderPage()

    expect(await screen.findByText('治理任务临近到期')).toBeInTheDocument()
    expect(screen.getByText('1 个任务在未来 7 天内到期或已逾期')).toBeInTheDocument()
  })
})
