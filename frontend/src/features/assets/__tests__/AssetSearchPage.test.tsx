import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAssetSearch } from '../../../hooks/useAssets'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { searchDocuments } from '../../../services/documentService'
import { AssetSearchPage } from '../AssetSearchPage'

vi.mock('../../../hooks/useAssets', () => ({ useAssetSearch: vi.fn() }))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))
vi.mock('../../../services/documentService', () => ({ searchDocuments: vi.fn() }))

const pageMeta = { total: 0, page: 1, perPage: 12, totalPages: 0 }
const document = {
  id: 201,
  documentNumber: 'DOC-201',
  title: '焊接工位作业指导书',
  summary: '焊接工位标准作业要求。',
  categoryCode: 'WORK_INSTRUCTION',
  maintainerId: 'u-1',
  maintainerName: '陈工',
  maintainerDepartment: '设备工程部',
  scopeMode: 'GLOBAL' as const,
  scopes: [],
  status: 'PUBLISHED' as const,
  currentVersion: { id: 301, documentId: 201, versionNumber: 'V1.0', changeSummary: '首次发布', status: 'PUBLISHED' as const, files: [], createdBy: '陈工', createdAt: '2026-08-01T00:00:00Z', publishedBy: '陈工' },
  createdAt: '2026-08-01T00:00:00Z',
  updatedAt: '2026-08-01T00:00:00Z',
  version: 1,
}
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
  files: [{ id: 1, name: 'a.png', format: 'PNG', previewable: true, primary: true }],
  ownerName: '陈工',
  ownerDepartment: '设备工程部',
  updatedAt: '2026-08-01T00:00:00Z',
  legacy: false,
}
const expectedTime = new Intl.DateTimeFormat('zh-CN', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
}).format(new Date('2026-08-01T00:00:00Z'))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><MemoryRouter><AssetSearchPage /></MemoryRouter></QueryClientProvider>)
}

describe('AssetSearchPage unified results', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getDictionaryItems).mockResolvedValue([])
    vi.mocked(useAssetSearch).mockReturnValue({ data: { data: [], meta: pageMeta }, isLoading: false, isError: false } as unknown as ReturnType<typeof useAssetSearch>)
    vi.mocked(searchDocuments).mockResolvedValue({
      data: [document],
      meta: { ...pageMeta, total: 1, totalPages: 1 },
    })
  })

  it('keeps a document result section visible when no asset matches', async () => {
    renderPage()

    expect(await screen.findByRole('region', { name: '知识文档检索结果' })).toBeInTheDocument()
    expect(await screen.findByText('焊接工位作业指导书')).toBeInTheDocument()
    expect(screen.getByText('没有符合条件的可预览资产')).toBeInTheDocument()
    expect(searchDocuments).toHaveBeenCalledWith(expect.objectContaining({
      query: '', page: 1, perPage: 8,
    }))
  })

  it('shows update time with hour and minute in gallery and compact list modes', async () => {
    vi.mocked(useAssetSearch).mockReturnValue({
      data: { data: [asset], meta: { ...pageMeta, total: 1, totalPages: 1 } },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useAssetSearch>)
    const user = userEvent.setup()
    renderPage()

    const card = await screen.findByRole('button', { name: '打开 底部水冷模组' })
    expect(within(card).getByText(expectedTime)).toBeInTheDocument()

    await user.click(screen.getByText('紧凑列表'))
    const timeCells = (await screen.findAllByRole('cell')).filter((cell) => cell.textContent === expectedTime)
    expect(timeCells.length).toBeGreaterThan(0)
  })
})
