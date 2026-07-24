import { mockAssets, mockRelations } from '../data/mockAssets'
import type {
  Asset,
  AssetDraftInput,
  AssetFile,
  AssetComment,
  AssetPage,
  AssetRelation,
  AssetSearchParams,
  EquipmentInterconnection,
} from '../types/asset'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
const useMocks = import.meta.env.VITE_USE_MOCKS !== 'false'
let nextMockAssetId = 1000
const mockFavorites = new Set<number>()
const mockComments: AssetComment[] = []
let nextMockCommentId = 1

const delay = (milliseconds: number) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

function matchesSearch(asset: Asset, params: AssetSearchParams) {
  const query = params.query.trim().toLowerCase()
  const matchesQuery =
    query.length === 0 ||
    asset.assetNumber.toLowerCase().includes(query) ||
    asset.name.toLowerCase().includes(query) ||
    asset.description.toLowerCase().includes(query) ||
    asset.files.some((file) => file.name.toLowerCase().includes(query))
  const matchesType = !params.assetType || asset.assetType === params.assetType
  const matchesStatus = !params.status || asset.status === params.status
  const matchesPreviewable = !params.previewable || asset.files.some((file) => file.previewable)
  const matchesScope = asset.scopes.some(
    (scope) =>
      (!params.platformFamily || (scope.platformFamily ?? scope.platform) === params.platformFamily) &&
      (!params.platformVariant || scope.platformVariant === params.platformVariant) &&
      (!params.base || scope.base === params.base) &&
      (!params.productionLine || scope.productionLine === params.productionLine),
  )
  return matchesQuery && matchesType && matchesStatus && matchesPreviewable && matchesScope
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
    throw new Error(`请求失败：${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function searchAssets(params: AssetSearchParams): Promise<AssetPage> {
  if (useMocks) {
    await delay(180)
    const filtered = mockAssets.filter((asset) => matchesSearch(asset, params))
    const offset = (params.page - 1) * params.perPage
    return {
      data: filtered.slice(offset, offset + params.perPage),
      meta: {
        total: filtered.length,
        page: params.page,
        perPage: params.perPage,
        totalPages: Math.ceil(filtered.length / params.perPage),
      },
    }
  }

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
  if (params.previewable) query.set('previewable', 'true')
  return request<AssetPage>(`/api/v1/assets?${query.toString()}`)
}

export function getAssetFilePreviewUrl(assetId: number, file: AssetFile): string | undefined {
  if (useMocks || !file.previewable || !file.id || !file.storageKey) return undefined
  return `${apiBaseUrl}/api/v1/assets/${assetId}/files/${file.id}?preview=true`
}

export async function getAsset(id: number): Promise<Asset> {
  if (useMocks) {
    await delay(100)
    const asset = mockAssets.find((item) => item.id === id)
    if (!asset) throw new Error('未找到数模资产')
    return asset
  }
  return request<Asset>(`/api/v1/assets/${id}`)
}

export async function getAssetRelations(id: number): Promise<AssetRelation[]> {
  if (useMocks) {
    await delay(120)
    return mockRelations[id] ?? []
  }
  return request<AssetRelation[]>(`/api/v1/assets/${id}/relations`)
}

export async function saveAssetDraft(input: AssetDraftInput): Promise<Asset> {
  if (useMocks) {
    await delay(180)
    const asset: Asset = {
      ...input,
      id: nextMockAssetId++,
      status: 'DRAFT',
      updatedAt: new Date().toISOString(),
      legacy: false,
    }
    mockAssets.unshift(asset)
    return asset
  }
  return request<Asset>('/api/v1/assets/drafts', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function uploadAssetFile(file: File): Promise<AssetFile> {
  if (useMocks) {
    await delay(80)
    const format = file.name.split('.').pop()?.toUpperCase() ?? 'OTHER'
    return {
      id: 0,
      name: file.name,
      format,
      sizeBytes: file.size,
      role: '其他附件',
      previewable: ['PDF', 'PNG', 'JPG', 'JPEG', 'TIFF'].includes(format),
      primary: false,
      storageKey: '',
      contentSha256: '',
    }
  }
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${apiBaseUrl}/api/v1/uploads/files`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  })
  if (!response.ok) throw new Error(`文件上传失败：${response.status}`)
  const payload = (await response.json()) as { file: AssetFile }
  return payload.file
}

export async function submitAsset(id: number): Promise<Asset> {
  if (useMocks) {
    await delay(180)
    const index = mockAssets.findIndex((asset) => asset.id === id)
    if (index < 0) throw new Error('未找到草稿资产')
    mockAssets[index] = {
      ...mockAssets[index],
      status: 'PENDING_CURATION',
      updatedAt: new Date().toISOString(),
    }
    return mockAssets[index]
  }
  return request<Asset>(`/api/v1/assets/${id}/submit`, { method: 'POST' })
}

export async function getFavorite(id: number): Promise<boolean> {
  if (useMocks) {
    await delay(80)
    return mockFavorites.has(id)
  }
  const response = await request<{ favorited: boolean }>(`/api/v1/assets/${id}/favorite`)
  return response.favorited
}

export async function setFavorite(id: number, favorited: boolean): Promise<boolean> {
  if (useMocks) {
    await delay(100)
    if (favorited) mockFavorites.add(id)
    else mockFavorites.delete(id)
    return favorited
  }
  const response = await request<{ favorited: boolean }>(`/api/v1/assets/${id}/favorite`, {
    method: favorited ? 'POST' : 'DELETE',
  })
  return response.favorited
}

export async function getFavoriteAssets(): Promise<Asset[]> {
  if (useMocks) {
    await delay(100)
    return mockAssets.filter((asset) => mockFavorites.has(asset.id))
  }
  return request<Asset[]>('/api/v1/favorites')
}

export async function getMyUploads(status?: Asset['status']): Promise<AssetPage> {
  if (useMocks) {
    await delay(100)
    const data = mockAssets.filter((asset) => asset.ownerName === '陈工' && (!status || asset.status === status))
    return { data, meta: { total: data.length, page: 1, perPage: 20, totalPages: 1 } }
  }
  const query = new URLSearchParams({ page: '1', per_page: '20' })
  if (status) query.set('status', status)
  return request<AssetPage>(`/api/v1/uploads/mine?${query.toString()}`)
}

export async function getEquipmentInterconnections(equipmentCode?: string): Promise<EquipmentInterconnection[]> {
  if (useMocks) {
    await delay(80)
    const links: EquipmentInterconnection[] = [
      { id: 1, equipmentCode: 'EQ-ND-A-001', equipmentName: '焊接工位总成', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段', dataReference: '/line-data/EQ-ND-A-001', status: 'ACTIVE' },
      { id: 2, equipmentCode: 'EQ-ND-A-002', equipmentName: '定位工装设备', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段', dataReference: '/line-data/EQ-ND-A-002', status: 'ACTIVE' },
    ]
    return links.filter((link) => !equipmentCode || link.equipmentCode === equipmentCode)
  }
  const query = equipmentCode ? `?equipmentCode=${encodeURIComponent(equipmentCode)}` : ''
  return request<EquipmentInterconnection[]>(`/api/v1/equipment-interconnections${query}`)
}

export async function getComments(id: number): Promise<AssetComment[]> {
  if (useMocks) {
    await delay(100)
    return mockComments.filter((comment) => comment.assetId === id)
  }
  return request<AssetComment[]>(`/api/v1/assets/${id}/comments`)
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error(`无法读取图片：${file.name}`))
    reader.readAsDataURL(file)
  })
}

export async function addComment(id: number, content: string, images: File[] = []): Promise<AssetComment> {
  if (useMocks) {
    await delay(120)
    const mockImages = await Promise.all(images.map(async (image, index) => ({
      key: `mock-${nextMockCommentId}-${index}`,
      url: await readFileAsDataUrl(image),
    })))
    const comment: AssetComment = {
      id: nextMockCommentId++,
      assetId: id,
      authorId: 'demo-user',
      authorName: '陈工',
      content,
      images: mockImages,
      createdAt: new Date().toISOString(),
      deleted: false,
      likeCount: 0,
      likedByCurrentUser: false,
      canDelete: true,
    }
    mockComments.unshift(comment)
    return comment
  }
  const formData = new FormData()
  formData.append('authorName', '陈工')
  formData.append('content', content)
  images.forEach((image) => formData.append('images', image))
  const response = await fetch(`${apiBaseUrl}/api/v1/assets/${id}/comments`, {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  })
  if (!response.ok) throw new Error(`评论发布失败：${response.status}`)
  return response.json() as Promise<AssetComment>
}

export async function deleteComment(assetId: number, commentId: number): Promise<void> {
  if (useMocks) {
    await delay(100)
    const comment = mockComments.find((item) => item.assetId === assetId && item.id === commentId)
    if (comment) comment.deleted = true
    return
  }
  await request<void>(`/api/v1/assets/${assetId}/comments/${commentId}`, { method: 'DELETE' })
}

export async function setCommentLike(assetId: number, commentId: number, liked: boolean): Promise<{ liked: boolean; likeCount: number }> {
  if (useMocks) {
    await delay(80)
    const comment = mockComments.find((item) => item.assetId === assetId && item.id === commentId)
    if (!comment) throw new Error('未找到评论')
    if (comment.likedByCurrentUser !== liked) {
      comment.likeCount = Math.max(0, comment.likeCount + (liked ? 1 : -1))
      comment.likedByCurrentUser = liked
    }
    return { liked: comment.likedByCurrentUser, likeCount: comment.likeCount }
  }
  return request<{ liked: boolean; likeCount: number }>(`/api/v1/assets/${assetId}/comments/${commentId}/like`, {
    method: liked ? 'POST' : 'DELETE',
  })
}
