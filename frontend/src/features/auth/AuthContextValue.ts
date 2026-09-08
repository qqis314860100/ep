import { createContext } from 'react'
import type { SessionUser } from './session'

export interface AuthContextValue {
  /** 当前登录用户；null 表示未登录（仍在恢复中也显示 null，配合 ready）。 */
  user: SessionUser | null
  /** 是否已完成一次会话恢复尝试（之后才可判定未登录）。 */
  ready: boolean
  login: (userId: string, password: string) => Promise<SessionUser>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
