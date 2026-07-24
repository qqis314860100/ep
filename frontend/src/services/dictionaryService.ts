import type { DictionaryCategory, DictionaryItem, SaveDictionaryItemInput } from '../types/dictionary'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

interface ApiErrorBody {
  error?: { message?: string }
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

export function getDictionaryCategories() {
  return request<DictionaryCategory[]>('/api/v1/dictionaries/categories')
}

export function getDictionaryItems() {
  return request<DictionaryItem[]>('/api/v1/dictionaries/items')
}

export function createDictionaryItem(input: SaveDictionaryItemInput) {
  return request<DictionaryItem>('/api/v1/dictionaries/items', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateDictionaryItem(id: number, input: SaveDictionaryItemInput) {
  return request<DictionaryItem>(`/api/v1/dictionaries/items/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function mergeDictionaryItem(id: number, targetId: number, version: number) {
  return request<DictionaryItem>(`/api/v1/dictionaries/items/${id}/merge`, {
    method: 'POST',
    body: JSON.stringify({ targetId, version }),
  })
}
