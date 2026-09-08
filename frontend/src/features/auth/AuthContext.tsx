import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'

import { login as apiLogin, logout as apiLogout, restoreSession } from './api'
import { AuthContext } from './AuthContextValue'
import { authSession, type SessionUser } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(() => authSession.get())
  const [ready, setReady] = useState(false)

  useEffect(() => {
    let active = true
    void restoreSession().then(restored => {
      if (!active) return
      setUser(restored)
      setReady(true)
    })
    return () => { active = false }
  }, [])

  useEffect(() => authSession.subscribe(() => setUser(authSession.get())), [])

  const login = useCallback(async (userId: string, password: string) => {
    const logged = await apiLogin(userId, password)
    setUser(logged)
    setReady(true)
    return logged
  }, [])

  const logout = useCallback(async () => {
    await apiLogout()
    setUser(null)
  }, [])

  const value = useMemo(() => ({ user, ready, login, logout }), [user, ready, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
