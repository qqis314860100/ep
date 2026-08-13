import type { OperationLog, SystemRole, SystemUser, SystemUserScope } from './types'

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

export function getSystemUsers(): Promise<SystemUser[]> {
  return request<SystemUser[]>('/api/v1/admin/users')
}

export function updateUserRoles(id: number, roles: SystemRole[], version: number): Promise<SystemUser> {
  return request<SystemUser>(`/api/v1/admin/users/${id}/roles`, {
    method: 'PATCH',
    body: JSON.stringify({ roles, version }),
  })
}

export function updateUserScopes(id: number, scopes: SystemUserScope[], version: number): Promise<SystemUser> {
  return request<SystemUser>(`/api/v1/admin/users/${id}/scopes`, {
    method: 'PATCH',
    body: JSON.stringify({ scopes, version }),
  })
}

export interface OperationLogParams {
  action?: string
  actor?: string
  page: number
  perPage: number
}

export interface OperationLogPage {
  data: OperationLog[]
  meta: { total: number; page: number; perPage: number }
}

export function getOperationLogs(params: OperationLogParams): Promise<OperationLogPage> {
  const query = new URLSearchParams({
    page: String(params.page),
    per_page: String(params.perPage),
  })
  if (params.action) query.set('action', params.action)
  if (params.actor) query.set('actor', params.actor)
  return request<OperationLogPage>(`/api/v1/admin/operation-logs?${query.toString()}`)
}
