import { CheckCircleOutlined, ClockCircleOutlined, EditOutlined, ExclamationCircleOutlined, SyncOutlined } from '@ant-design/icons'
import { Tag } from 'antd'
import type { GovernanceTaskStatus } from '../types'

const statusConfig: Record<GovernanceTaskStatus, { color: string; icon: React.ReactNode; label: string }> = {
  DRAFT: { color: 'default', icon: <EditOutlined />, label: '草稿' },
  IN_PROGRESS: { color: 'processing', icon: <SyncOutlined />, label: '执行中' },
  PENDING_CONFIRMATION: { color: 'warning', icon: <ClockCircleOutlined />, label: '待确认' },
  PENDING_ACCEPTANCE: { color: 'cyan', icon: <ClockCircleOutlined />, label: '待验收' },
  REWORK_REQUIRED: { color: 'error', icon: <ExclamationCircleOutlined />, label: '需返工' },
  COMPLETED: { color: 'success', icon: <CheckCircleOutlined />, label: '已完成' },
}

export function GovernanceStatusTag({ status }: { status: GovernanceTaskStatus }) {
  const config = statusConfig[status]
  return <Tag color={config.color} icon={config.icon}>{config.label}</Tag>
}
