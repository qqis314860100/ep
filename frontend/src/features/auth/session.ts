export interface SessionUser {
  userId: string
  name: string
  department: string
  roles: string[]
}

export interface AuthSession {
  user: SessionUser | null
}

const SESSION_KEY = 'dsh.ep.auth'
const listeners = new Set<() => void>()

/** 登录会话的轻量客户端存储（响应 /auth/me 与登录结果，供请求层取真实身份）。 */
export const authSession = {
  get(): SessionUser | null {
    try {
      const raw = localStorage.getItem(SESSION_KEY)
      if (!raw) return null
      return JSON.parse(raw) as SessionUser
    } catch {
      return null
    }
  },
  set(user: SessionUser | null) {
    if (user) localStorage.setItem(SESSION_KEY, JSON.stringify(user))
    else localStorage.removeItem(SESSION_KEY)
    listeners.forEach(listener => listener())
  },
  subscribe(listener: () => void): () => void {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}

/** 当前真实操作人：登录会话优先；未登录回落 demo 语义（历史 e2e/只读页兼容）。 */
export function currentActor(): { userId: string; roles: string } {
  const user = authSession.get()
  if (user) {
    return { userId: user.userId, roles: user.roles.join(',') }
  }
  return { userId: 'demo-user', roles: 'CONTENT_ADMIN,SYSTEM_ADMIN' }
}
