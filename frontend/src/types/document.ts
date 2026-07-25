export type DocumentStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type DocumentVersionStatus = 'DRAFT' | 'PUBLISHED' | 'HISTORICAL'

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
}
