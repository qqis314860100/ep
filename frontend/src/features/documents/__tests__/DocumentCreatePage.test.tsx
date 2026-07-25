import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDictionaryItems } from '../../../services/dictionaryService'
import {
  createDocumentDraft,
  publishDocument,
  uploadDocumentFile,
} from '../../../services/documentService'
import DocumentCreatePage from '../DocumentCreatePage'

vi.mock('../../../services/documentService', () => ({
  createDocumentDraft: vi.fn(),
  publishDocument: vi.fn(),
  uploadDocumentFile: vi.fn(),
}))
vi.mock('../../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const uploadedFile = {
  id: 0,
  name: 'instruction.pdf',
  format: 'PDF',
  sizeBytes: 128,
  previewable: true,
  storageKey: 'stored-key',
  contentSha256: 'abc123',
}

const draft = {
  id: 321,
  documentNumber: 'DOC-000321',
  title: '焊接作业指导书',
  summary: '焊接工位标准操作要求。',
  categoryCode: 'WORK_INSTRUCTION',
  maintainerId: 'demo-user',
  maintainerName: '陈工',
  maintainerDepartment: '设备工程部',
  status: 'DRAFT' as const,
  currentVersion: {
    id: 400,
    documentId: 321,
    versionNumber: 'V1.0',
    changeSummary: '首次发布',
    status: 'DRAFT' as const,
    files: [uploadedFile],
    createdBy: '陈工',
    createdAt: '2026-07-26T00:00:00Z',
    publishedBy: '',
  },
  createdAt: '2026-07-26T00:00:00Z',
  updatedAt: '2026-07-26T00:00:00Z',
  version: 0,
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/documents/new']}>
        <Routes>
          <Route path="/documents/new" element={<DocumentCreatePage />} />
          <Route path="/documents/:id" element={<div>详情已打开</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DocumentCreatePage', () => {
  beforeEach(() => {
    vi.mocked(getDictionaryItems).mockResolvedValue([{
      id: 263,
      category: 'DOCUMENT_CATEGORY',
      code: 'WORK_INSTRUCTION',
      name: '作业指导书',
      status: 'ENABLED',
      sortOrder: 30,
      usageCount: 0,
      version: 0,
      directional: false,
      allowDuplicate: false,
      updatedAt: '2026-07-26T00:00:00',
    }])
    vi.mocked(uploadDocumentFile).mockResolvedValue(uploadedFile)
    vi.mocked(createDocumentDraft).mockResolvedValue(draft)
    vi.mocked(publishDocument).mockResolvedValue({
      ...draft,
      status: 'PUBLISHED',
      currentVersionId: 400,
      currentVersion: { ...draft.currentVersion, status: 'PUBLISHED', publishedAt: '2026-07-26T01:00:00Z' },
    })
  })

  it('requires minimum metadata and one uploaded file', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.clear(screen.getByLabelText('维护人'))
    await user.click(screen.getByRole('button', { name: '保存草稿' }))

    expect(await screen.findByText('请输入文档标题')).toBeInTheDocument()
    expect(screen.getByText('请选择文档分类')).toBeInTheDocument()
    expect(screen.getByText('请输入文档摘要')).toBeInTheDocument()
    expect(screen.getByText('请选择维护人')).toBeInTheDocument()
    expect(screen.getByText('请至少上传一个文件')).toBeInTheDocument()
  })

  it('uploads in the form and publishes only after confirmation', async () => {
    const user = userEvent.setup()
    const view = renderPage()
    await user.type(screen.getByLabelText('文档标题'), '焊接作业指导书')
    await user.click(screen.getByLabelText('文档分类'))
    await user.click(await screen.findByText('作业指导书'))
    await user.type(screen.getByLabelText('文档摘要'), '焊接工位标准操作要求。')
    await user.type(screen.getByLabelText('所属部门'), '设备工程部')
    const fileInput = view.container.querySelector<HTMLInputElement>('input[type="file"]')
    expect(fileInput).not.toBeNull()
    await user.upload(fileInput!, new File(['%PDF-1.7'], 'instruction.pdf', { type: 'application/pdf' }))
    expect(await screen.findByText('instruction.pdf')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '发布文档' }))

    expect(await screen.findByText('确认首次发布')).toBeInTheDocument()
    expect(screen.getByText('V1.0')).toBeInTheDocument()
    expect(publishDocument).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '确认发布' }))

    await waitFor(() => expect(createDocumentDraft).toHaveBeenCalledTimes(1))
    expect(publishDocument).toHaveBeenCalledWith(321)
    expect(await screen.findByText('详情已打开')).toBeInTheDocument()
  })
})
