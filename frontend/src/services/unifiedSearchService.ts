import type { AssetPage } from '../types/asset'
import type { DocumentPage } from '../types/document'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export interface UnifiedSearchParams {
  query: string
  platformFamily?: string
  platformVariant?: string
  productLine?: string
  base?: string
  productionLine?: string
  processSection?: string
  assetPage: number
  assetPerPage: number
  documentPage: number
  documentPerPage: number
}

export interface UnifiedSearchResponse {
  assets: AssetPage & { status: string; errorCode: string }
  documents: DocumentPage & { status: string; errorCode: string }
}

export async function searchUnified(params: UnifiedSearchParams): Promise<UnifiedSearchResponse> {
  const query = new URLSearchParams({
    q: params.query,
    asset_page: String(params.assetPage),
    asset_per_page: String(params.assetPerPage),
    document_page: String(params.documentPage),
    document_per_page: String(params.documentPerPage),
  })
  if (params.platformFamily) query.set('platform_family', params.platformFamily)
  if (params.platformVariant) query.set('platform_variant', params.platformVariant)
  if (params.productLine) query.set('product_line', params.productLine)
  if (params.base) query.set('base', params.base)
  if (params.productionLine) query.set('production_line', params.productionLine)
  if (params.processSection) query.set('process_section', params.processSection)
  const response = await fetch(`${apiBaseUrl}/api/v1/search?${query.toString()}`, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`统一检索失败：${response.status}`)
  return response.json() as Promise<UnifiedSearchResponse>
}
