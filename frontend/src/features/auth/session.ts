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

/** 当前真实操作人：只来自登录会话；未登录时返回空身份，不伪造演示账号。 */
export function currentActor(): { userId: string; roles: string } {
  const user = authSession.get()
  if (user) {
    return { userId: user.userId, roles: user.roles.join(',') }
  }
  return { userId: '', roles: '' }
}
