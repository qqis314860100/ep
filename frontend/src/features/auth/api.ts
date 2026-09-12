import type { ApiErrorBody } from '../../types/api'
import { authSession, type SessionUser } from './session'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: 'include',
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

export interface LoginResult {
  userId: string
  name: string
  department: string
  roles: string[]
}

export async function login(userId: string, password: string): Promise<SessionUser> {
  const result = await request<LoginResult>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ userId, password }),
  })
  const user: SessionUser = { ...result }
  authSession.set(user)
  return user
}

export async function logout(): Promise<void> {
  try {
    await fetch(`${apiBaseUrl}/api/v1/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
  } finally {
    authSession.set(null)
  }
}

/** 启动时恢复会话：成功则写本地会话，401 则清空。 */
export async function restoreSession(): Promise<SessionUser | null> {
  try {
    const user = await request<SessionUser>('/api/v1/auth/me')
    authSession.set(user)
    return user
  } catch {
    authSession.set(null)
    return null
  }
}
