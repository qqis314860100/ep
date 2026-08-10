import type {
  CreateDocumentDraftInput,
  DocumentFile,
  DocumentPage,
  DocumentSearchParams,
  KnowledgeDocument,
  AssetDocumentRelation,
} from '../types/document'
import type { Asset } from '../types/asset'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiErrorBody {
  error?: {
    code?: string
    message?: string
  }
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
    throw new Error(body.error?.message || `请求失败：${response.status}`)
  }
  return response.json() as Promise<T>
}

export function searchDocuments(params: DocumentSearchParams): Promise<DocumentPage> {
  const query = new URLSearchParams({
    q: params.query,
    category: params.category,
    page: String(params.page),
    per_page: String(params.perPage),
  })
  return request<DocumentPage>(`/api/v1/documents?${query.toString()}`)
}

export function getDocument(id: number): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/v1/documents/${id}`)
}

export interface DocumentAssetRelationResult {
  relation: AssetDocumentRelation
  asset: Asset
}

export function getDocumentAssetRelations(id: number): Promise<DocumentAssetRelationResult[]> {
  return request<DocumentAssetRelationResult[]>(`/api/v1/documents/${id}/asset-relations`)
}

export function createDocumentDraft(input: CreateDocumentDraftInput): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>('/api/v1/documents/drafts', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function publishDocument(id: number): Promise<KnowledgeDocument> {
  return request<KnowledgeDocument>(`/api/v1/documents/${id}/publish`, { method: 'POST' })
}

export async function uploadDocumentFile(file: File): Promise<DocumentFile> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${apiBaseUrl}/api/v1/uploads/files`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ApiErrorBody
    throw new Error(body.error?.message || `文件上传失败：${response.status}`)
  }
  const payload = await response.json() as {
    file: DocumentFile & { role?: string; primary?: boolean }
  }
  return {
    id: payload.file.id,
    name: payload.file.name,
    format: payload.file.format,
    sizeBytes: payload.file.sizeBytes,
    previewable: payload.file.previewable,
    storageKey: payload.file.storageKey,
    contentSha256: payload.file.contentSha256,
  }
}

export function getDocumentFileUrl(
  documentId: number,
  versionId: number,
  fileId: number,
  preview: boolean,
): string {
  const path = `/api/v1/documents/${documentId}/versions/${versionId}/files/${fileId}`
  return `${apiBaseUrl}${path}${preview ? '?preview=true' : ''}`
}
