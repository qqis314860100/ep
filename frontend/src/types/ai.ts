/** AI 一期（T3 问答 / T1·T4 编目建议）前端契约类型 —— 与 ep 后端 /api/v1/ai/** 响应一致。 */

export type AiSuggestionStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'SUPERSEDED'
export type AiSuggestionTargetType = 'ASSET' | 'KNOWLEDGE_DOC'

export interface AiProposedFields {
  name: string
  description: string
  assetTypeCode: string
  tags: string[]
  summary: string
  categoryCode: string
}

export interface AiTargetScope {
  platformFamily: string
  platformVariant: string
  productLine: string
  base: string
  productionLine: string
  processSection: string
}

export interface AiSuggestion {
  id: number
  targetType: AiSuggestionTargetType
  targetId: number
  targetTitle: string
  status: AiSuggestionStatus
  source: string
  proposed: AiProposedFields
  evidence: string[]
  confidence: number | null
  scopes: AiTargetScope[]
  createdBy: string
  createdAt: string
  resolvedBy: string
  resolvedAt: string | null
}

export interface PageMeta {
  total: number
  page: number
  perPage: number
  totalPages: number
}

export interface Page<T> {
  data: T[]
  meta: PageMeta
}

export interface ChatCitation {
  docId: string
  location: string
  excerpt: string
  inScope: boolean
}

export interface ChatSessionView {
  id: number
  title: string
  createdAt: string
  updatedAt: string
}

export type ChatMessageRole = 'USER' | 'ASSISTANT'

export interface ChatMessageView {
  id: number
  role: ChatMessageRole
  content: string
  citations: ChatCitation[]
  createdAt: string
}
