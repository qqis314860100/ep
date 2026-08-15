import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAsset, getRelationGraph } from '../../../services/assetService'
import RelationBrowserPage from './index'

vi.mock('../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/assetService')>(),
  getAsset: vi.fn(),
  getRelationGraph: vi.fn(),
}))

const root = {
  id: 101, assetNumber: 'DM-ND-A-0001', name: '焊接工位总成数模', description: '', assetType: 'MIXED_ASSET' as const,
  status: 'STANDARDIZED' as const, specialties: [], tags: [], moduleTags: [], standardEquipmentModule: false,
  linkedModuleAssetIds: [], equipmentInterconnectCode: '', scopes: [], files: [], ownerName: '陈工',
  ownerDepartment: '', updatedAt: '2026-08-01T00:00:00Z', legacy: false,
}
const graph = {
  nodes: [
    { assetId: 101, assetNumber: 'DM-ND-A-0001', assetName: '焊接工位总成数模', assetType: 'MIXED_ASSET', status: 'STANDARDIZED', depth: 0 },
    { assetId: 102, assetNumber: 'DM-ND-A-0002', assetName: '定位工装数模', assetType: 'THREE_DIMENSIONAL_MODEL', status: 'STANDARDIZED', depth: 1 },
    { assetId: 103, assetNumber: 'DM-ND-A-0003', assetName: '输送模块布置数模', assetType: 'MIXED_ASSET', status: 'PENDING_CURATION', depth: 1 },
    { assetId: 104, assetNumber: 'LEGACY-00000104', assetName: 'XM-PL01 设备图', assetType: 'TWO_DIMENSIONAL_DRAWING', status: 'DISABLED', depth: 2 },
  ],
  edges: [
    { id: 1, sourceAssetId: 101, targetAssetId: 102, relationType: 'REFERENCES', directionLabel: '引用', description: '焊接总成引用该定位工装。' },
    { id: 2, sourceAssetId: 101, targetAssetId: 103, relationType: 'CONTAINS', directionLabel: '包含', description: '整线总成包含输送模块。' },
    { id: 3, sourceAssetId: 102, targetAssetId: 104, relationType: 'REFERENCES', directionLabel: '引用', description: '历史图被引用' },
  ],
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/assets/101/relations']}>
        <Routes>
          <Route path="/assets/:id/relations" element={<RelationBrowserPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('RelationBrowserPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getAsset).mockResolvedValue(root)
    vi.mocked(getRelationGraph).mockResolvedValue(graph)
  })

  it('renders levels, edges, and marks disabled nodes', async () => {
    renderPage()

    expect(await screen.findByText('关系浏览 · 焊接工位总成数模')).toBeInTheDocument()
    expect(screen.getByText('定位工装数模')).toBeInTheDocument()
    expect(screen.getByText('输送模块布置数模')).toBeInTheDocument()
    expect(screen.getByText('XM-PL01 设备图')).toBeInTheDocument()
    expect(screen.getAllByText('已停用').length).toBeGreaterThan(0)
    expect(screen.getByText('焊接总成引用该定位工装。')).toBeInTheDocument()
    expect(getRelationGraph).toHaveBeenCalledWith(101, 2)
  })
})
