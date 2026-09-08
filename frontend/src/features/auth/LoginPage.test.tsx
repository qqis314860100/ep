import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from './api'
import { AuthProvider } from './AuthContext'
import LoginPage from './LoginPage'
import { authSession } from './session'

vi.mock('./api', async (importOriginal) => ({
  ...await importOriginal<typeof import('./api')>(),
  login: vi.fn(),
  restoreSession: vi.fn(),
  logout: vi.fn(),
}))

function renderLogin() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <App>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<div>工作台</div>} />
              <Route path="/home" element={<div>工作台</div>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </App>
    </QueryClientProvider>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    authSession.set(null)
    localStorage.clear()
    vi.mocked(authApi.restoreSession).mockResolvedValue(null)
    vi.mocked(authApi.login).mockResolvedValue({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN'] })
  })

  it('logs in with credentials and lands back on the destination', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.clear(screen.getByLabelText('工号'))
    await user.type(screen.getByLabelText('工号'), 'emp-admin')
    await user.clear(screen.getByLabelText('密码'))
    await user.type(screen.getByLabelText('密码'), 'demo123')
    await user.click(screen.getByRole('button', { name: '登 录' }))

    await waitFor(() => expect(authApi.login).toHaveBeenCalledWith('emp-admin', 'demo123'))
    expect(await screen.findByText('工作台')).toBeVisible()
  })

  it('offers demo accounts that log in directly', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: /李\s*工/ }))

    await waitFor(() => expect(authApi.login).toHaveBeenCalledWith('emp-li', 'demo123'))
  })

  it('shows an error message on wrong password', async () => {
    const user = userEvent.setup()
    vi.mocked(authApi.login).mockRejectedValue(new Error('账号或密码错误'))
    renderLogin()

    await user.clear(screen.getByLabelText('密码'))
    await user.type(screen.getByLabelText('密码'), 'wrong-pass')
    await user.click(screen.getByRole('button', { name: '登 录' }))

    expect(await screen.findByText('账号或密码错误')).toBeVisible()
  })
})
