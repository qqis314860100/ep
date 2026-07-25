import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { searchDocuments } from '../../../services/documentService'
import DocumentSearchPage from '../DocumentSearchPage'

vi.mock('../../../services/documentService', () => ({ searchDocuments: vi.fn() }))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const documentPage = {
  data: [{
    id: 101,
    documentNumber: 'DOC-WI-000001',
    title: '焊接工位作业指导书',
    summary: '焊接工位设备操作和安全检查要求。',
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
      files: [{ id: 2001, name: 'instruction.pdf', format: 'PDF', sizeBytes: 120, previewable: true, contentSha256: 'abc' }],
      createdBy: '陈工',
      createdAt: '2026-07-20T00:00:00Z',
      publishedBy: '陈工',
      publishedAt: '2026-07-21T00:00:00Z',
    },
    createdAt: '2026-07-20T00:00:00Z',
    updatedAt: '2026-07-21T00:00:00Z',
    version: 1,
  }],
  meta: { total: 1, page: 1, perPage: 20, totalPages: 1 },
}

const categories = [
  { id: 261, category: 'DOCUMENT_CATEGORY', code: 'TECHNICAL_SPECIFICATION', name: '技术规范', status: 'ENABLED' as const, sortOrder: 10, usageCount: 0, version: 0, directional: false, allowDuplicate: false, updatedAt: '2026-07-20T00:00:00' },
  { id: 263, category: 'DOCUMENT_CATEGORY', code: 'WORK_INSTRUCTION', name: '作业指导书', status: 'ENABLED' as const, sortOrder: 30, usageCount: 1, version: 0, directional: false, allowDuplicate: false, updatedAt: '2026-07-20T00:00:00' },
]

function renderPage(route = '/documents') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <DocumentSearchPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DocumentSearchPage', () => {
  beforeEach(() => {
    vi.mocked(getDictionaryItems).mockResolvedValue(categories)
    vi.mocked(searchDocuments).mockResolvedValue(documentPage)
  })

  it('restores keyword and category from the URL', async () => {
    renderPage('/documents?category=WORK_INSTRUCTION&q=焊接')

    expect(await screen.findByText('焊接工位作业指导书')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('焊接')
    expect(within(screen.getByRole('complementary', { name: '文档分类' }))
      .getByRole('button', { name: '作业指导书' })).toHaveAttribute('aria-current', 'page')
    expect(searchDocuments).toHaveBeenCalledWith({
      query: '焊接', category: 'WORK_INSTRUCTION', page: 1, perPage: 20,
    })
  })

  it('clears active filters without hiding the category rail', async () => {
    const user = userEvent.setup()
    renderPage('/documents?category=WORK_INSTRUCTION&q=焊接')
    await screen.findByText('焊接工位作业指导书')

    await user.click(screen.getByRole('button', { name: '清空筛选' }))

    expect(screen.getByRole('searchbox')).toHaveValue('')
    expect(screen.getByRole('button', { name: /全部文档/ })).toHaveAttribute('aria-current', 'page')
  })

  it('keeps filters and offers retry when search fails', async () => {
    vi.mocked(searchDocuments).mockRejectedValue(new Error('服务暂不可用'))
    renderPage('/documents?q=验收')

    expect(await screen.findByText('服务暂不可用')).toBeInTheDocument()
    expect(screen.getByRole('searchbox')).toHaveValue('验收')
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument()
  })
})
