import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  changeAssetDocumentRelationType,
  createAssetDocumentRelation,
  removeAssetDocumentRelation,
} from '../../../services/assetDocumentRelationService'

function response(status: number, body?: unknown) {
  return new Response(body === undefined ? undefined : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('assetDocumentRelationService', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('creates, changes, and removes a relation with its optimistic-lock version', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(response(201, { id: 9, assetId: 101, documentId: 201, relationType: 'COMPANION', version: 0 }))
      .mockResolvedValueOnce(response(200, { id: 9, assetId: 101, documentId: 201, relationType: 'REFERENCE', version: 1 }))
      .mockResolvedValueOnce(response(204))
    vi.stubGlobal('fetch', fetchMock)

    await createAssetDocumentRelation({ assetId: 101, documentId: 201, relationType: 'COMPANION' })
    await changeAssetDocumentRelationType(9, { relationType: 'REFERENCE', version: 0 })
    await removeAssetDocumentRelation(9, 1)

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/asset-document-relations', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ assetId: 101, documentId: 201, relationType: 'COMPANION' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/asset-document-relations/9', expect.objectContaining({
      method: 'PATCH', body: JSON.stringify({ relationType: 'REFERENCE', version: 0 }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/asset-document-relations/9', expect.objectContaining({
      method: 'DELETE', body: JSON.stringify({ version: 1 }),
    }))
  })
})
