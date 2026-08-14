import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getMyUploads } from '../../../services/assetService'
import MyUploadsPage from './index'

vi.mock('../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/assetService')>(),
  getMyUploads: vi.fn(),
}))

const asset = {
  id: 11,
  assetNumber: 'M-2026-0001',
  name: '底部水冷模组',
  description: '电池包底部水冷板',
  assetType: 'MODULE' as const,
  status: 'STANDARDIZED' as const,
  specialties: ['结构'],
  tags: [],
  moduleTags: [],
  standardEquipmentModule: false,
  linkedModuleAssetIds: [],
  equipmentInterconnectCode: '',
  scopes: [{ platform: '乘用车', platformVariant: '底部水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段' }],
  files: [],
  ownerName: '陈工',
  ownerDepartment: '设备工程部',
  updatedAt: '2026-08-01T00:00:00Z',
  legacy: false,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><MyUploadsPage /></MemoryRouter></QueryClientProvider>)
}

describe('MyUploadsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getMyUploads).mockResolvedValue({ data: [asset], meta: { total: 1, page: 1, perPage: 20, totalPages: 1 } })
  })

  it('loads uploads and filters by status through the backend', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('底部水冷模组')).toBeInTheDocument()
    expect(getMyUploads).toHaveBeenCalledWith(undefined)

    await user.click(screen.getByText('待整理'))
    expect(getMyUploads).toHaveBeenCalledWith('PENDING_CURATION')
  })
})
