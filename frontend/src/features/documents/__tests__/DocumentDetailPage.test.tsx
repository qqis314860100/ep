import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App as AntdApp } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { getDocument, getDocumentAssetRelations } from '../../../services/documentService'
import { searchAssets } from '../../../services/assetService'
import { createAssetDocumentRelation } from '../../../services/assetDocumentRelationService'
import DocumentDetailPage from '../../../pages/main/document-detail'

vi.mock('../../../services/documentService', async () => {
  const actual = await vi.importActual<typeof import('../../../services/documentService')>('../../../services/documentService')
  return { ...actual, getDocument: vi.fn(), getDocumentAssetRelations: vi.fn() }
})
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))
vi.mock('../../../services/assetService', () => ({ searchAssets: vi.fn() }))
vi.mock('../../../services/assetDocumentRelationService', () => ({
  createAssetDocumentRelation: vi.fn(),
  changeAssetDocumentRelationType: vi.fn(),
  removeAssetDocumentRelation: vi.fn(),
}))

const document = {
  id: 101,
  documentNumber: 'DOC-WI-000001',
  title: '焊接工位作业指导书',
  summary: '焊接工位设备操作、安全检查和点检要求。',
  categoryCode: 'WORK_INSTRUCTION',
  maintainerId: 'u-101',
  maintainerName: '陈工',
  maintainerDepartment: '设备工程部',
  scopeMode: 'GLOBAL' as const,
  scopes: [],
  status: 'PUBLISHED' as const,
  currentVersionId: 1001,
  currentVersion: {
    id: 1001,
    documentId: 101,
    versionNumber: 'V1.0',
    changeSummary: '首次发布',
    status: 'PUBLISHED' as const,
    files: [{ id: 2001, name: 'welding-instruction.pdf', format: 'PDF', sizeBytes: 1200, previewable: true, contentSha256: 'a'.repeat(64) }],
    createdBy: '陈工',
    createdAt: '2026-07-20T00:00:00Z',
    publishedBy: '陈工',
    publishedAt: '2026-07-21T00:00:00Z',
  },
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-21T00:00:00Z',
  version: 1,
}

function renderDetail() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AntdApp>
        <MemoryRouter initialEntries={['/documents/101']}>
          <Routes><Route path="/documents/:id" element={<DocumentDetailPage />} /></Routes>
        </MemoryRouter>
      </AntdApp>
    </QueryClientProvider>,
  )
}

describe('DocumentDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getDocument).mockResolvedValue(document)
    vi.mocked(getDocumentAssetRelations).mockResolvedValue([])
    vi.mocked(searchAssets).mockResolvedValue({
      data: [{
        id: 401,
        assetNumber: 'DM-000401',
        name: '焊接工位总成数模',
        description: '焊接工位总成模型',
        assetType: 'MIXED_ASSET',
        status: 'STANDARDIZED',
        specialties: [], tags: [], moduleTags: [], standardEquipmentModule: true, linkedModuleAssetIds: [],
        equipmentInterconnectCode: '',
        scopes: [{ platform: '乘用车', productLine: 'H03', base: '基地一', productionLine: 'A线', processSection: '焊接段' }],
        files: [], ownerName: '陈工', ownerDepartment: '设备工程部', updatedAt: '2026-07-21T00:00:00Z', legacy: false,
      }],
      meta: { total: 1, page: 1, perPage: 20, totalPages: 1 },
    })
    vi.mocked(createAssetDocumentRelation).mockResolvedValue({ id: 1, assetId: 401, documentId: 101, relationType: 'COMPANION', version: 0 })
    vi.mocked(getDictionaryItems).mockResolvedValue([{
      id: 263,
      category: 'DOCUMENT_CATEGORY',
      code: 'WORK_INSTRUCTION',
      name: '作业指导书',
      status: 'ENABLED',
      sortOrder: 30,
      usageCount: 1,
      version: 0,
      directional: false,
      allowDuplicate: false,
      updatedAt: '2026-07-26T00:00:00',
    }])
  })

  it('shows files, preview, and document information together', async () => {
    renderDetail()

    expect(await screen.findByRole('heading', { name: '焊接工位作业指导书' })).toBeInTheDocument()
    expect(screen.getByText('当前版本 V1.0')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /welding-instruction.pdf/ })).toBeInTheDocument()
    expect(screen.getByTitle('文档预览')).toHaveAttribute('src', expect.stringContaining('preview=true'))
    expect(screen.getByText('作业指导书')).toBeInTheDocument()
  })

  it('keeps download available when preview cannot load', async () => {
    renderDetail()
    const preview = await screen.findByTitle('文档预览')

    fireEvent.error(preview)

    expect(screen.getByText('预览加载失败')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '下载文件' })).toBeInTheDocument()
  })

  it('searches for an asset before creating a bidirectional relation', async () => {
    const user = userEvent.setup()
    renderDetail()
    await screen.findByRole('heading', { name: '焊接工位作业指导书' })

    await user.click(screen.getByRole('button', { name: '关联资产' }))
    expect(await screen.findByRole('dialog', { name: '关联资产' })).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: /焊接工位总成数模/ }))
    await user.click(screen.getByRole('button', { name: '保存关联' }))

    expect(vi.mocked(createAssetDocumentRelation).mock.calls[0]?.[0])
      .toEqual({ assetId: 401, documentId: 101, relationType: 'COMPANION' })
  })
})
