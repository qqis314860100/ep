import { useContext } from 'react'
import { AuthContext, type AuthContextValue } from './AuthContextValue'

/** 读取当前登录会话；必须在 AuthProvider 内使用。 */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth 必须在 AuthProvider 内使用')
  return value
}
