import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getGovernanceIssues } from '../../../features/governance/api'
import { getFavoriteAssets, getMyUploads, searchAssets } from '../../../services/assetService'
import HomePage from './index'

vi.mock('../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/assetService')>(),
  getFavoriteAssets: vi.fn(),
  getMyUploads: vi.fn(),
  searchAssets: vi.fn(),
}))
vi.mock('../../../features/governance/api', () => ({ getGovernanceIssues: vi.fn() }))

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
  })

  it('shows todo counts and quick entries', async () => {
    renderPage()

    expect(await screen.findByText('需补充信息')).toBeInTheDocument()
    expect((await screen.findAllByText('1')).length).toBeGreaterThan(0)
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('异常文件')).toBeInTheDocument()
    expect(screen.getByText('批量上传')).toBeInTheDocument()
    expect(screen.getByText('历史资料治理')).toBeInTheDocument()
    expect(screen.getAllByText('待整理资料').length).toBeGreaterThan(0)
  })
})
