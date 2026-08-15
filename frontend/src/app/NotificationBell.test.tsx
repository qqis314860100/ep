import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getNotifications } from '../services/notifications'
import { NotificationBell } from './NotificationBell'

vi.mock('../services/notifications', () => ({
  getNotifications: vi.fn(),
}))

function renderBell() {
  return render(
    <MemoryRouter initialEntries={['/home']}>
      <Routes>
        <Route path="/home" element={<NotificationBell />} />
        <Route path="/sys/drawing/operations" element={<div>治理操作页</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('NotificationBell', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    window.localStorage.clear()
    vi.mocked(getNotifications).mockResolvedValue({
      items: [
        {
          id: 'todo-summary', type: 'TODO_SUMMARY', title: '资料待办汇总',
          description: '待整理 2 份 · 开放问题 5 项 · 异常文件 0 项',
          link: '/sys/drawing/operations', createdAt: '2026-08-15T01:00:00Z',
        },
        {
          id: 'task-1', type: 'TASK_DUE', title: '任务到期 · 字段治理任务',
          description: '3 天后到期', link: '/sys/drawing/tasks/1', createdAt: '2026-08-15T00:30:00Z',
        },
      ],
    })
  })

  it('shows unread badge and lists notifications in the panel', async () => {
    renderBell()

    expect(await screen.findByTitle('2')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('通知'))
    expect(await screen.findByText('资料待办汇总')).toBeInTheDocument()
    expect(screen.getByText('待整理 2 份 · 开放问题 5 项 · 异常文件 0 项')).toBeInTheDocument()
    expect(screen.getByText('任务到期 · 字段治理任务')).toBeInTheDocument()
  })

  it('marks an item read and navigates to its link on click', async () => {
    renderBell()

    await userEvent.click(await screen.findByLabelText('通知'))
    await userEvent.click(await screen.findByText('资料待办汇总'))

    expect(await screen.findByText('治理操作页')).toBeInTheDocument()
    expect(JSON.parse(window.localStorage.getItem('read-notification-ids-v1') ?? '[]')).toContain('todo-summary')
  })

  it('marks all read via the panel action', async () => {
    renderBell()

    await userEvent.click(await screen.findByLabelText('通知'))
    await userEvent.click(await screen.findByText('全部已读'))

    const stored = JSON.parse(window.localStorage.getItem('read-notification-ids-v1') ?? '[]') as string[]
    expect(stored).toContain('todo-summary')
    expect(stored).toContain('task-1')
    // antd 保留缩放离场动画节点，计数为 0 时以 data-show="false" 隐藏
    expect(document.querySelector('.ant-badge-count')?.getAttribute('data-show')).toBe('false')
  })

  it('shows empty state when there are no notifications', async () => {
    vi.mocked(getNotifications).mockResolvedValue({ items: [] })
    renderBell()

    await userEvent.click(await screen.findByLabelText('通知'))
    expect(await screen.findByText('暂无通知')).toBeInTheDocument()
  })
})
