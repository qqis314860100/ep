import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { createDocumentVersion } from '../../../services/documentService'
import { NewDocumentVersionModal } from './NewDocumentVersionModal'

vi.mock('../../../services/documentService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../services/documentService')>(),
  createDocumentVersion: vi.fn(),
  uploadDocumentFile: vi.fn(),
}))

function renderModal(props: Partial<Parameters<typeof NewDocumentVersionModal>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <NewDocumentVersionModal open documentId={101} currentVersionNumber="V1.0" onClose={() => {}} onCreated={() => Promise.resolve()} {...props} />
    </QueryClientProvider>,
  )
}

describe('NewDocumentVersionModal', () => {
  it('prefills the next version number and requires change summary and files', async () => {
    const user = userEvent.setup()
    renderModal()

    expect(screen.getByLabelText('版本号')).toHaveValue('V1.1')
    await user.click(screen.getByRole('button', { name: '创建版本草稿' }))
    expect(await screen.findByText('请填写变更说明')).toBeInTheDocument()
    expect(createDocumentVersion).not.toHaveBeenCalled()
  })

  it('suggests V2.0 when the current version number is not V-style', async () => {
    renderModal({ currentVersionNumber: 'DRAFT-1' })
    expect(screen.getByLabelText('版本号')).toHaveValue('V2.0')
  })
})
