import type { AssetDocumentRelation, AssetDocumentRelationType } from '../types/document'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiErrorBody {
  error?: { message?: string }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ApiErrorBody
    throw new Error(body.error?.message || `关联操作失败：${response.status}`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export function createAssetDocumentRelation(input: {
  assetId: number
  documentId: number
  relationType: AssetDocumentRelationType
}): Promise<AssetDocumentRelation> {
  return request<AssetDocumentRelation>('/api/v1/asset-document-relations', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function changeAssetDocumentRelationType(
  id: number,
  input: { relationType: AssetDocumentRelationType; version: number },
): Promise<AssetDocumentRelation> {
  return request<AssetDocumentRelation>(`/api/v1/asset-document-relations/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function removeAssetDocumentRelation(id: number, version: number): Promise<void> {
  return request<void>(`/api/v1/asset-document-relations/${id}`, {
    method: 'DELETE',
    body: JSON.stringify({ version }),
  })
}
