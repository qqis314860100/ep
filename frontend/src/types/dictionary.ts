export type DictionaryStatus = 'ENABLED' | 'DISABLED' | 'MERGED'

export interface DictionaryCategory {
  code: string
  name: string
  groupName: string
  parentCategory?: string
  description: string
  sortOrder: number
}

export interface DictionaryItem {
  id: number
  category: string
  code: string
  name: string
  parentId?: number
  status: DictionaryStatus
  sortOrder: number
  usageCount: number
  version: number
  description?: string
  forwardName?: string
  reverseName?: string
  directional: boolean
  allowDuplicate: boolean
  mergeTargetId?: number
  updatedAt: string
}

export interface SaveDictionaryItemInput {
  category: string
  code: string
  name: string
  parentId?: number
  status: DictionaryStatus
  sortOrder: number
  version: number
  description?: string
  forwardName?: string
  reverseName?: string
  directional: boolean
  allowDuplicate: boolean
}
