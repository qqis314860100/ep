import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getOperationLogs, getSystemUsers, updateUserRoles, updateUserScopes } from './api'
import { SystemAdminPage } from './SystemAdminPage'

vi.mock('./api', () => ({
  getSystemUsers: vi.fn(),
  getOperationLogs: vi.fn(),
  updateUserRoles: vi.fn(),
  updateUserScopes: vi.fn(),
}))

const users = [
  { id: 1, userId: 'u-chen', name: '陈工', department: '设备工程部', roles: ['UPLOADER'], scopes: [{ id: 1, base: '宁德基地', productLine: 'H03' }], updatedAt: '2026-08-01T00:00:00Z', version: 1 },
  { id: 4, userId: 'u-admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN', 'CONTENT_ADMIN'], scopes: [], updatedAt: '2026-08-01T00:00:00Z', version: 1 },
]

const logs = {
  data: [{ id: 1, actorUserId: 'u-admin', action: 'ROLE_UPDATE', targetType: 'USER', targetId: 1, detailJson: '{}', createdAt: '2026-08-01T00:00:00Z' }],
  meta: { total: 1, page: 1, perPage: 20 },
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><MemoryRouter><SystemAdminPage /></MemoryRouter></QueryClientProvider>)
}

describe('SystemAdminPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getSystemUsers).mockResolvedValue(users as never)
    vi.mocked(getOperationLogs).mockResolvedValue(logs as never)
    vi.mocked(updateUserRoles).mockResolvedValue(users[0] as never)
    vi.mocked(updateUserScopes).mockResolvedValue(users[0] as never)
  })

  it('renders the user list with roles', async () => {
    renderPage()

    expect(await screen.findByText('陈工')).toBeInTheDocument()
    expect(screen.getByText('管理员')).toBeInTheDocument()
    expect(screen.getByText('上传者/资产责任人')).toBeInTheDocument()
  })

  it('shows operation logs on the logs tab', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.click(await screen.findByText('操作记录'))

    expect(await screen.findByText('角色变更')).toBeInTheDocument()
    expect((await screen.findAllByText('u-admin')).length).toBeGreaterThan(0)
  })
})
