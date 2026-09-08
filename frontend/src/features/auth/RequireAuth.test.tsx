import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from './api'
import { AuthProvider } from './AuthContext'
import { RequireAuth } from './RequireAuth'
import { authSession } from './session'

vi.mock('./api', async (importOriginal) => ({
  ...await importOriginal<typeof import('./api')>(),
  login: vi.fn(),
  restoreSession: vi.fn(),
  logout: vi.fn(),
}))

function renderRoute(initialPath = '/protected') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<div>登录页</div>} />
          <Route path="/protected" element={<RequireAuth><div>受保护内容</div></RequireAuth>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('RequireAuth', () => {
  beforeEach(() => {
    authSession.set(null)
    localStorage.clear()
  })

  it('redirects to login when no session exists', async () => {
    vi.mocked(authApi.restoreSession).mockResolvedValue(null)
    renderRoute()

    expect(await screen.findByText('登录页')).toBeVisible()
    expect(screen.queryByText('受保护内容')).not.toBeInTheDocument()
  })

  it('keeps protected content when a session exists', async () => {
    authSession.set({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN'] })
    vi.mocked(authApi.restoreSession).mockResolvedValue({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN'] })
    renderRoute()

    expect(await screen.findByText('受保护内容')).toBeVisible()
  })
})
