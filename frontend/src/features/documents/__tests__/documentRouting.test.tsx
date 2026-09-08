import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../../App'

vi.mock('../DocumentSearchPage', () => ({
  default: () => <h1>文档中心</h1>,
}))

describe('document routing', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/documents')
    localStorage.clear()
    // App 需要已登录会话才渲染受保护路由：/auth/me 返回登录用户；其余请求失败（组件静默降级）
    vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (!url.includes('/api/v1/auth/me')) throw new Error('测试环境仅提供会话接口')
      return new Response(JSON.stringify({ userId: 'emp-admin', name: '管理员', department: '信息化部', roles: ['SYSTEM_ADMIN'] }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    }))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('opens the document workspace from primary navigation', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    render(<App />)

    expect(await screen.findByRole('heading', { name: '文档中心' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '文档中心' })).toHaveAttribute('aria-current', 'page')
    expect(scrollTo).toHaveBeenCalledWith(0, 0)
  })
})
