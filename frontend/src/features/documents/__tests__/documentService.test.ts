import { afterEach, describe, expect, it, vi } from 'vitest'
import { getDocumentFileUrl, publishDocument, searchDocuments } from '../../../services/documentService'

const pagePayload = {
  data: [],
  meta: { total: 0, page: 2, perPage: 20, totalPages: 0 },
}

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('documentService', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('serializes only the supported search filters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, pagePayload))
    vi.stubGlobal('fetch', fetchMock)

    await searchDocuments({ query: '焊接', category: 'WORK_INSTRUCTION', page: 2, perPage: 20 })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/documents?q=%E7%84%8A%E6%8E%A5&category=WORK_INSTRUCTION&page=2&per_page=20',
      expect.objectContaining({ headers: { Accept: 'application/json' } }),
    )
  })

  it('surfaces the backend error message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(409, {
      error: { code: 'duplicate_document_number', message: '文档编号已存在' },
    })))

    await expect(publishDocument(1)).rejects.toThrow('文档编号已存在')
  })

  it('builds guarded preview and download URLs', () => {
    expect(getDocumentFileUrl(10, 20, 30, true))
      .toBe('/api/v1/documents/10/versions/20/files/30?preview=true')
    expect(getDocumentFileUrl(10, 20, 30, false))
      .toBe('/api/v1/documents/10/versions/20/files/30')
  })
})
