import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { getDocument } from '../../../services/documentService'
import DocumentDetailPage from '../../../pages/main/document-detail'

vi.mock('../../../services/documentService', async () => {
  const actual = await vi.importActual<typeof import('../../../services/documentService')>('../../../services/documentService')
  return { ...actual, getDocument: vi.fn() }
})
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const document = {
  id: 101,
  documentNumber: 'DOC-WI-000001',
  title: '焊接工位作业指导书',
  summary: '焊接工位设备操作、安全检查和点检要求。',
  categoryCode: 'WORK_INSTRUCTION',
  maintainerId: 'u-101',
  maintainerName: '陈工',
  maintainerDepartment: '设备工程部',
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
      <MemoryRouter initialEntries={['/documents/101']}>
        <Routes><Route path="/documents/:id" element={<DocumentDetailPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DocumentDetailPage', () => {
  beforeEach(() => {
    vi.mocked(getDocument).mockResolvedValue(document)
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
})
