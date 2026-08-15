import {
  BellOutlined,
  ClockCircleOutlined,
  ProfileOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Badge, Button, Empty, Popover, Tooltip } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getNotifications, type NotificationItem } from '../services/notifications'

const READ_IDS_KEY = 'read-notification-ids-v1'

const BellButton = styled(Button)`
  color: #c6d3dc !important;

  &:hover,
  &:focus-visible {
    color: #fff !important;
    background: rgba(255, 255, 255, 0.09) !important;
  }
`

const Panel = styled.div`
  width: 336px;
`

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #eef2f0;
`

const PanelTitle = styled.span`
  color: #23312c;
  font-size: 13px;
  font-weight: 650;
`

const MarkAllRead = styled.button`
  padding: 0;
  color: #2f7567;
  background: transparent;
  border: 0;
  cursor: pointer;
  font-size: 12px;

  &:hover {
    text-decoration: underline;
  }
`

const List = styled.div`
  max-height: 380px;
  overflow: auto;
`

const Item = styled.button<{ $unread: boolean }>`
  position: relative;
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 8px;
  width: 100%;
  padding: 10px 12px;
  text-align: left;
  background: ${({ $unread }) => ($unread ? '#f4f9f7' : 'transparent')};
  border: 0;
  border-bottom: 1px solid #f0f3f1;
  cursor: pointer;

  &:hover {
    background: #eef5f2;
  }

  &::after {
    position: absolute;
    top: 14px;
    right: 10px;
    width: 7px;
    height: 7px;
    content: '';
    background: ${({ $unread }) => ($unread ? '#b3562b' : 'transparent')};
    border-radius: 50%;
  }
`

const ItemIcon = styled.span`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: #2f7567;
  background: #e7f0ec;
  border-radius: 6px;
  font-size: 14px;
`

const ItemBody = styled.span`
  min-width: 0;
`

const ItemTitle = styled.span`
  display: block;
  overflow: hidden;
  color: #23312c;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const ItemDescription = styled.span`
  display: block;
  margin-top: 2px;
  overflow: hidden;
  color: #5c6b64;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const ItemTime = styled.span`
  display: block;
  margin-top: 3px;
  color: #93a09a;
  font-family: Consolas, 'SFMono-Regular', monospace;
  font-size: 11px;
`

const EmptyWrap = styled.div`
  padding: 28px 0;
`

function readIds(): string[] {
  try {
    const raw = window.localStorage.getItem(READ_IDS_KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

function saveReadIds(ids: string[]) {
  window.localStorage.setItem(READ_IDS_KEY, JSON.stringify(ids))
}

function formatTime(iso: string): string {
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const minutes = Math.max(0, Math.floor((Date.now() - then) / 60_000))
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

function typeIcon(type: NotificationItem['type']) {
  switch (type) {
    case 'TASK_DUE':
      return <ClockCircleOutlined />
    case 'SCAN_FAILED':
      return <WarningOutlined />
    default:
      return <ProfileOutlined />
  }
}

export function NotificationBell() {
  const navigate = useNavigate()
  const [items, setItems] = useState<NotificationItem[]>([])
  const [open, setOpen] = useState(false)
  const [read, setRead] = useState<string[]>(() => readIds())

  const refresh = async () => {
    try {
      const view = await getNotifications()
      setItems(view.items)
    } catch {
      // 后端不可用时保持静默
    }
  }

  useEffect(() => {
    void refresh()
    const timer = window.setInterval(() => void refresh(), 60_000)
    return () => window.clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const unreadCount = useMemo(() => items.filter((item) => !read.includes(item.id)).length, [items, read])

  const markRead = (id: string) => {
    setRead((current) => {
      if (current.includes(id)) return current
      const next = [...current, id]
      saveReadIds(next)
      return next
    })
  }

  const markAllRead = () => {
    setRead((current) => {
      const next = Array.from(new Set([...current, ...items.map((item) => item.id)]))
      saveReadIds(next)
      return next
    })
  }

  const openItem = (item: NotificationItem) => {
    markRead(item.id)
    setOpen(false)
    navigate(item.link)
  }

  const content = (
    <Panel>
      <PanelHeader>
        <PanelTitle>通知</PanelTitle>
        {unreadCount > 0 && (
          <MarkAllRead type="button" onClick={markAllRead}>
            全部已读
          </MarkAllRead>
        )}
      </PanelHeader>
      {items.length === 0 ? (
        <EmptyWrap>
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无通知" />
        </EmptyWrap>
      ) : (
        <List>
          {items.map((item) => (
            <Item key={item.id} type="button" $unread={!read.includes(item.id)} onClick={() => openItem(item)}>
              <ItemIcon aria-hidden="true">{typeIcon(item.type)}</ItemIcon>
              <ItemBody>
                <ItemTitle>{item.title}</ItemTitle>
                <ItemDescription>{item.description}</ItemDescription>
                <ItemTime>{formatTime(item.createdAt)}</ItemTime>
              </ItemBody>
            </Item>
          ))}
        </List>
      )}
    </Panel>
  )

  return (
    <Popover
      content={content}
      trigger="click"
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (next) void refresh()
      }}
      placement="bottomRight"
      arrow={false}
      overlayInnerStyle={{ padding: 0 }}
    >
      <Tooltip title="通知">
        <Badge count={unreadCount} size="small" offset={[-2, 2]}>
          <BellButton type="text" icon={<BellOutlined />} aria-label="通知" />
        </Badge>
      </Tooltip>
    </Popover>
  )
}
