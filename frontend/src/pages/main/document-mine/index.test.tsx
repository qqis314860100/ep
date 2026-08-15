import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { getMyDocuments, getMyFavoriteDocuments } from '../../../services/documentService'
import MyDocumentsPage from './index'

vi.mock('../../../services/documentService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/documentService')>(),
  getMyFavoriteDocuments: vi.fn(),
  getMyDocuments: vi.fn(),
}))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const doc = {
  id: 201, documentNumber: 'DOC-WI-000001', title: '焊接工位作业指导书', summary: '作业要求。',
  categoryCode: 'WORK_INSTRUCTION', maintainerId: 'u-100', maintainerName: '陈工', maintainerDepartment: '设备工程部',
  scopeMode: 'GLOBAL' as const, scopes: [], status: 'PUBLISHED' as const,
  currentVersion: { id: 301, documentId: 201, versionNumber: 'V1.0', changeSummary: '首次发布', status: 'PUBLISHED' as const, files: [], createdBy: '陈工', createdAt: '2026-08-01T00:00:00Z', publishedBy: '陈工', publishedAt: '2026-08-01T00:00:00Z' },
  createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z', version: 1,
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><MyDocumentsPage /></MemoryRouter></QueryClientProvider>)
}

describe('MyDocumentsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getMyFavoriteDocuments).mockResolvedValue([doc])
    vi.mocked(getMyDocuments).mockResolvedValue([doc])
    vi.mocked(getDictionaryItems).mockResolvedValue([])
  })

  it('shows favorited documents and switches to maintained list with status filter', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('焊接工位作业指导书')).toBeInTheDocument()
    await user.click(screen.getByText('我维护的'))
    expect(getMyDocuments).toHaveBeenCalledWith(undefined)
    await user.click(screen.getByText('草稿'))
    expect(getMyDocuments).toHaveBeenCalledWith('DRAFT')
  })
})
