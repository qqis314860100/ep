export type AssetType =
  | 'THREE_DIMENSIONAL_MODEL'
  | 'TWO_DIMENSIONAL_DRAWING'
  | 'MIXED_ASSET'
  | 'OTHER'

export type AssetStatus =
  | 'DRAFT'
  | 'PENDING_CURATION'
  | 'STANDARDIZED'
  | 'DISABLED'

export type RelationType =
  | 'CONTAINS'
  | 'REFERENCES'
  | 'MATCHES'
  | 'ASSOCIATED_WITH'
  | 'REPLACES'

export interface AssetScope {
  platform: string
  productLine: string
  base: string
  productionLine: string
  processSection: string
  platformFamily?: string
  platformVariant?: string
}

export interface AssetFile {
  id: number
  name: string
  format: string
  sizeBytes: number
  role: string
  previewable: boolean
  primary: boolean
  storageKey?: string
  contentSha256?: string
}

export interface Asset {
  id: number
  assetNumber: string
  name: string
  description: string
  assetType: AssetType
  status: AssetStatus
  specialties: string[]
  tags: string[]
  moduleTags: string[]
  standardEquipmentModule: boolean
  linkedModuleAssetIds: number[]
  equipmentInterconnectCode: string
  scopes: AssetScope[]
  files: AssetFile[]
  ownerName: string
  ownerDepartment: string
  updatedAt: string
  legacy: boolean
}

export interface AssetRelation {
  id: number
  sourceAssetId: number
  targetAssetId: number
  targetAssetNumber: string
  targetAssetName: string
  targetAssetType: AssetType
  targetAssetStatus: AssetStatus
  relationType: RelationType
  directionLabel: string
  primaryScope: string
  description: string
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
  version: number
}

export interface AssetSearchParams {
  query: string
  assetType?: AssetType
  status?: AssetStatus
  platformFamily?: string
  platformVariant?: string
  base?: string
  productionLine?: string
  productLine?: string
  processSection?: string
  specialty?: string
  owner?: string
  format?: string
  updatedFrom?: string
  updatedTo?: string
  missingScope?: boolean
  sort?: AssetSort
  previewable?: boolean
  page: number
  perPage: number
}

export type AssetSort = 'RELEVANCE' | 'UPDATED_AT' | 'ASSET_NUMBER' | 'NAME'

export interface PageMeta {
  total: number
  page: number
  perPage: number
  totalPages: number
}

export interface AssetPage {
  data: Asset[]
  meta: PageMeta
}

export interface AssetDraftInput {
  assetNumber: string
  name: string
  description: string
  assetType: AssetType
  specialties: string[]
  tags: string[]
  moduleTags: string[]
  standardEquipmentModule: boolean
  linkedModuleAssetIds: number[]
  equipmentInterconnectCode: string
  scopes: AssetScope[]
  files: AssetFile[]
  ownerName: string
  ownerDepartment: string
}

export interface AssetComment {
  id: number
  assetId: number
  authorId: string
  authorName: string
  content: string
  images: AssetCommentImage[]
  createdAt: string
  deleted: boolean
  likeCount: number
  likedByCurrentUser: boolean
  canDelete: boolean
}

export interface AssetCommentImage {
  key: string
  url: string
}

export interface EquipmentInterconnection {
  id: number
  equipmentCode: string
  equipmentName: string
  base: string
  productionLine: string
  processSection: string
  dataReference: string
  status: string
}
