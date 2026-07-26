import { Checkbox, Empty, Tag, Typography } from 'antd'
import styled from 'styled-components'

import type { GovernanceItemExecution, GovernanceItemStatus } from '../types'

const statusLabels: Record<GovernanceItemStatus, string> = {
  PENDING: '待处理', PROCESSING: '处理中', BLOCKED: '已阻塞', REWORK_REQUIRED: '需返工', SUBMITTED: '已提交', CONFIRMED: '已确认', ACCEPTED: '已验收',
}
const visibleStatuses: GovernanceItemStatus[] = ['PENDING', 'PROCESSING', 'BLOCKED', 'REWORK_REQUIRED', 'SUBMITTED']
const Queue = styled.aside`width:280px; min-width:280px; border-right:1px solid #dfe5e2; overflow:auto; padding-right:12px;`
const Group = styled.section`margin-bottom:16px;`
const Item = styled.button<{ $active: boolean }>`width:100%; min-height:58px; padding:8px; border:1px solid ${p => p.$active ? '#1677ff' : '#d9d9d9'}; border-radius:6px; background:${p => p.$active ? '#e6f4ff' : '#fff'}; text-align:left; cursor:pointer; display:flex; gap:8px; align-items:flex-start; margin-top:6px;`

export function GovernanceItemQueue({ items, currentId, selectedIds, onSelect, onToggle, isSelectable }: {
  items: GovernanceItemExecution[]
  currentId?: number
  selectedIds: number[]
  onSelect: (item: GovernanceItemExecution) => void
  onToggle: (item: GovernanceItemExecution, checked: boolean) => void
  isSelectable: (item: GovernanceItemExecution) => boolean
}) {
  if (items.length === 0) return <Queue><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理项" /></Queue>
  return <Queue aria-label="治理项队列">
    {visibleStatuses.map(status => {
      const group = items.filter(entry => entry.item.status === status)
      if (!group.length) return null
      return <Group key={status}>
        <Typography.Text strong>{statusLabels[status]} ({group.length})</Typography.Text>
        {group.map(entry => <Item key={entry.item.id} type="button" $active={entry.item.id === currentId} onClick={() => onSelect(entry)}>
          <Checkbox aria-label={`选择治理项 ${entry.item.id}`} checked={selectedIds.includes(entry.item.id)} disabled={!isSelectable(entry)} onClick={event => event.stopPropagation()} onChange={event => onToggle(entry, event.target.checked)} />
          <span><Typography.Text strong>资产 #{entry.item.assetId}</Typography.Text><br /><Typography.Text type="secondary">治理项 #{entry.item.id}</Typography.Text></span>
          {entry.blockReason && <Tag color="error">阻塞</Tag>}
        </Item>)}
      </Group>
    })}
  </Queue>
}
