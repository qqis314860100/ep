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
}

export interface AssetSearchParams {
  query: string
  assetType?: AssetType
  status?: AssetStatus
  platformFamily?: string
  platformVariant?: string
  base?: string
  productionLine?: string
  page: number
  perPage: number
}

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
  authorName: string
  content: string
  createdAt: string
  deleted: boolean
  likeCount: number
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
