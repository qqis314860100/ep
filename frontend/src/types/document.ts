export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type DocumentVersionStatus = 'DRAFT' | 'PUBLISHED' | 'HISTORICAL'
export type DocumentScopeMode = 'GLOBAL' | 'SPECIFIED' | 'UNCLASSIFIED'
export type AssetDocumentRelationType = 'COMPANION' | 'APPLICABLE' | 'REFERENCE'

export interface DocumentScope {
  id?: number
  documentId?: number
  platformFamily: string
  platformVariant: string
  productLine: string
  baseName: string
  productionLine: string
  processSection: string
}

export interface DocumentFile {
  id: number
  name: string
  format: string
  sizeBytes: number
  previewable: boolean
  contentSha256: string
  storageKey?: string
}

export interface DocumentVersion {
  id: number
  documentId: number
  versionNumber: string
  changeSummary: string
  status: DocumentVersionStatus
  files: DocumentFile[]
  createdBy: string
  createdAt: string
  publishedBy: string
  publishedAt?: string
}

export interface KnowledgeDocument {
  id: number
  documentNumber: string
  title: string
  summary: string
  categoryCode: string
  maintainerId: string
  maintainerName: string
  maintainerDepartment: string
  scopeMode: DocumentScopeMode
  scopes: DocumentScope[]
  status: DocumentStatus
  currentVersionId?: number
  currentVersion: DocumentVersion
  createdAt: string
  updatedAt: string
  version: number
}

export interface DocumentPage {
  data: KnowledgeDocument[]
  meta: {
    total: number
    page: number
    perPage: number
    totalPages: number
  }
}

export interface DocumentSearchParams {
  query: string
  category: string
  page: number
  perPage: number
}

export interface CreateDocumentDraftInput {
  documentNumber: string
  title: string
  summary: string
  categoryCode: string
  maintainerId: string
  maintainerName: string
  maintainerDepartment: string
  versionNumber: string
  changeSummary: string
  files: DocumentFile[]
  scopeMode: Exclude<DocumentScopeMode, 'UNCLASSIFIED'>
  scopes: DocumentScope[]
}

export interface AssetDocumentRelation {
  id: number
  assetId: number
  documentId: number
  relationType: AssetDocumentRelationType
  version: number
}
