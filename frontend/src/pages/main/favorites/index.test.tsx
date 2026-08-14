import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getFavoriteAssets } from '../../../services/assetService'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { matchesFavoriteFilters } from './filter'
import FavoritesPage from './index'

vi.mock('../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/assetService')>(),
  getFavoriteAssets: vi.fn(),
  setFavorite: vi.fn(),
}))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const coolingAsset = {
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
const fixtureAsset = {
  ...coolingAsset,
  id: 12,
  assetNumber: 'M-2026-0002',
  name: '定位工装',
  description: '焊接定位工装',
  scopes: [{ platform: '乘用车', platformVariant: '底部水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段' }],
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><FavoritesPage /></MemoryRouter></QueryClientProvider>)
}

describe('FavoritesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getFavoriteAssets).mockResolvedValue([coolingAsset, fixtureAsset])
    vi.mocked(getDictionaryItems).mockResolvedValue([
      { id: 1, category: 'BASE', code: 'NINGDE', name: '宁德基地', parentId: null, status: 'ENABLED', sortOrder: 10, usageCount: 3, version: 0 },
      { id: 11, category: 'PRODUCTION_LINE', code: 'NINGDE_A', name: 'A 拉线', parentId: 1, status: 'ENABLED', sortOrder: 10, usageCount: 3, version: 0 },
    ])
  })

  it('filters favorites by keyword', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('底部水冷模组')).toBeInTheDocument()
    expect(screen.getByText('定位工装')).toBeInTheDocument()

    await user.type(screen.getByPlaceholderText('搜索名称、编号或说明'), '定位')
    expect(screen.queryByText('底部水冷模组')).not.toBeInTheDocument()
    expect(screen.getByText('定位工装')).toBeInTheDocument()
  })

  it('scope filter matches base and line within the same scope', () => {
    expect(matchesFavoriteFilters(coolingAsset, { base: '宁德基地' })).toBe(true)
    expect(matchesFavoriteFilters(coolingAsset, { base: '溧阳基地' })).toBe(false)
    expect(matchesFavoriteFilters(coolingAsset, { base: '宁德基地', line: 'A 拉线' })).toBe(true)
    expect(matchesFavoriteFilters(coolingAsset, { base: '宁德基地', line: 'B 拉线' })).toBe(false)
    expect(matchesFavoriteFilters(coolingAsset, {})).toBe(true)
  })

  it('shows an empty state scoped to filters when nothing matches', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('底部水冷模组')
    await user.type(screen.getByPlaceholderText('搜索名称、编号或说明'), '不存在的资料')
    expect(screen.getByText('没有符合筛选条件的收藏')).toBeInTheDocument()
    expect(within(screen.getByText('没有符合筛选条件的收藏')).queryByText('去检索资料')).not.toBeInTheDocument()
  })
})
