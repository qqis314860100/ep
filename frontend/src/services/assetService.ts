import type {
  Asset,
  AssetDraftInput,
  AssetFile,
  AssetComment,
  AssetPage,
  AssetRelation,
  AssetSearchParams,
  AssetStatus,
  AssetType,
  EquipmentInterconnection,
  RelationType,
} from '../types/asset'
import type { AssetDocumentRelation, KnowledgeDocument } from '../types/document'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    credentials: 'include',
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    throw new Error(`请求失败：${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function searchAssets(params: AssetSearchParams): Promise<AssetPage> {
  const query = new URLSearchParams({
    q: params.query,
    page: String(params.page),
    per_page: String(params.perPage),
  })
  if (params.assetType) query.set('asset_type', params.assetType)
  if (params.status) query.set('status', params.status)
  if (params.platformFamily) query.set('platform_family', params.platformFamily)
  if (params.platformVariant) query.set('platform_variant', params.platformVariant)
  if (params.base) query.set('base', params.base)
  if (params.productionLine) query.set('production_line', params.productionLine)
  if (params.productLine) query.set('product_line', params.productLine)
  if (params.processSection) query.set('process_section', params.processSection)
  if (params.specialty) query.set('specialty', params.specialty)
  if (params.owner) query.set('owner', params.owner)
  if (params.format) query.set('format', params.format)
  if (params.updatedFrom) query.set('updated_from', new Date(`${params.updatedFrom}T00:00:00`).toISOString())
  if (params.updatedTo) query.set('updated_to', new Date(`${params.updatedTo}T23:59:59`).toISOString())
  if (params.missingScope) query.set('missing_scope', 'true')
  if (params.sort && params.sort !== 'RELEVANCE') query.set('sort', params.sort)
  if (params.previewable) query.set('previewable', 'true')
  return request<AssetPage>(`/api/v1/assets?${query.toString()}`)
}

export function getAssetFilePreviewUrl(assetId: number, file: AssetFile): string | undefined {
  if (!file.previewable || !file.id || !file.storageKey) return undefined
  return `${apiBaseUrl}/api/v1/assets/${assetId}/files/${file.id}?preview=true`
}

export function getAssetFileUrl(assetId: number, file: AssetFile, preview: boolean): string {
  return `${apiBaseUrl}/api/v1/assets/${assetId}/files/${file.id}?preview=${preview}`
}

export function getAssetPackageUrl(assetId: number): string | undefined {
  return `${apiBaseUrl}/api/v1/assets/${assetId}/package`
}

export async function getAsset(id: number): Promise<Asset> {
  return request<Asset>(`/api/v1/assets/${id}`)
}

export async function getAssetRelations(id: number): Promise<AssetRelation[]> {
  return request<AssetRelation[]>(`/api/v1/assets/${id}/relations`)
}

export interface RelationGraphNode {
  assetId: number
  assetNumber: string
  assetName: string
  assetType: AssetType
  status: AssetStatus
  depth: number
}

export interface RelationGraphEdge {
  id: number
  sourceAssetId: number
  targetAssetId: number
  relationType: RelationType
  directionLabel: string
  description: string
}

export interface RelationGraph {
  nodes: RelationGraphNode[]
  edges: RelationGraphEdge[]
}

export async function getRelationGraph(assetId: number, depth = 2): Promise<RelationGraph> {
  return request<RelationGraph>(`/api/v1/assets/${assetId}/relation-graph?depth=${depth}`)
}

export interface RelationInput {
  targetAssetId: number
  relationType: RelationType
  description?: string
}

export async function createAssetRelation(assetId: number, input: RelationInput): Promise<AssetRelation> {
  return request<AssetRelation>(`/api/v1/assets/${assetId}/relations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export interface RelationUpdateInput extends RelationInput {
  sourceAssetId: number
  version: number
}

export async function updateAssetRelation(assetId: number, relationId: number, input: RelationUpdateInput): Promise<AssetRelation> {
  return request<AssetRelation>(`/api/v1/assets/${assetId}/relations/${relationId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  })
}

export async function removeAssetRelation(assetId: number, relationId: number): Promise<void> {
  await request<unknown>(`/api/v1/assets/${assetId}/relations/${relationId}`, { method: 'DELETE' })
}

export interface AssetDocumentRelationResult {
  relation: AssetDocumentRelation
  document: KnowledgeDocument
}

export async function getAssetDocuments(id: number): Promise<AssetDocumentRelationResult[]> {
  return request<AssetDocumentRelationResult[]>(`/api/v1/assets/${id}/documents`)
}

export async function disableAsset(assetId: number, reason: string): Promise<Asset> {
  return request<Asset>(`/api/v1/assets/${assetId}/disable`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
}

export interface BatchDraftResult {
  assets: Asset[]
  duplicateFiles: { fileName: string; contentSha256: string }[]
}

export async function saveAssetDraftsBatch(inputs: AssetDraftInput[]): Promise<BatchDraftResult> {
  return request<BatchDraftResult>('/api/v1/assets/batch-drafts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(inputs),
  })
}

export async function saveAssetDraft(input: AssetDraftInput): Promise<Asset> {
  return request<Asset>('/api/v1/assets/drafts', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function updateAssetDraft(id: number, input: AssetDraftInput): Promise<Asset> {
  return request<Asset>(`/api/v1/assets/${id}/draft`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export async function uploadAssetFile(file: File): Promise<AssetFile> {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${apiBaseUrl}/api/v1/uploads/files`, {
    credentials: 'include',
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  })
  if (!response.ok) throw new Error(`文件上传失败：${response.status}`)
  const payload = (await response.json()) as { file: AssetFile }
  return payload.file
}

export async function submitAsset(id: number): Promise<Asset> {
  return request<Asset>(`/api/v1/assets/${id}/submit`, { method: 'POST' })
}

export async function getFavorite(id: number): Promise<boolean> {
  const response = await request<{ favorited: boolean }>(`/api/v1/assets/${id}/favorite`)
  return response.favorited
}

export async function setFavorite(id: number, favorited: boolean): Promise<boolean> {
  const response = await request<{ favorited: boolean }>(`/api/v1/assets/${id}/favorite`, {
    method: favorited ? 'POST' : 'DELETE',
  })
  return response.favorited
}

export async function getFavoriteAssets(): Promise<Asset[]> {
  return request<Asset[]>('/api/v1/favorites')
}

export async function getMyUploads(status?: Asset['status']): Promise<AssetPage> {
  const query = new URLSearchParams({ page: '1', per_page: '20' })
  if (status) query.set('status', status)
  return request<AssetPage>(`/api/v1/uploads/mine?${query.toString()}`)
}

export async function getEquipmentInterconnections(equipmentCode?: string): Promise<EquipmentInterconnection[]> {
  const query = equipmentCode ? `?equipmentCode=${encodeURIComponent(equipmentCode)}` : ''
  return request<EquipmentInterconnection[]>(`/api/v1/equipment-interconnections${query}`)
}

export async function getComments(id: number): Promise<AssetComment[]> {
  return request<AssetComment[]>(`/api/v1/assets/${id}/comments`)
}

export async function addComment(id: number, content: string, images: File[] = []): Promise<AssetComment> {
  const formData = new FormData()
  formData.append('content', content)
  images.forEach((image) => formData.append('images', image))
  const response = await fetch(`${apiBaseUrl}/api/v1/assets/${id}/comments`, {
    credentials: 'include',
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  })
  if (!response.ok) throw new Error(`评论发布失败：${response.status}`)
  return response.json() as Promise<AssetComment>
}

export async function deleteComment(assetId: number, commentId: number): Promise<void> {
  await request<void>(`/api/v1/assets/${assetId}/comments/${commentId}`, { method: 'DELETE' })
}

export async function setCommentLike(assetId: number, commentId: number, liked: boolean): Promise<{ liked: boolean; likeCount: number }> {
  return request<{ liked: boolean; likeCount: number }>(`/api/v1/assets/${assetId}/comments/${commentId}/like`, {
    method: liked ? 'POST' : 'DELETE',
  })
}
