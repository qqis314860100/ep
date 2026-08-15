export interface NotificationItem {
  id: string
  type: 'TODO_SUMMARY' | 'TASK_DUE' | 'SCAN_FAILED'
  title: string
  description: string
  link: string
  createdAt: string
}

export interface NotificationView {
  items: NotificationItem[]
}

async function request<T>(path: string): Promise<T> {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}${path}`, {
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error(`请求失败：${response.status}`)
  }
  return response.json() as Promise<T>
}

export function getNotifications(): Promise<NotificationView> {
  return request<NotificationView>('/api/v1/notifications')
}
