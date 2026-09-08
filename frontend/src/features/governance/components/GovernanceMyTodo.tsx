import { useQuery } from '@tanstack/react-query'
import { Typography } from 'antd'
import { useMemo } from 'react'
import styled from 'styled-components'
import { getGovernanceTasks } from '../api'
import { authSession } from '../../auth/session'
import { dueDayDiff, isTaskEscalated } from './governanceRailModel'
import { railTheme } from './railTheme'

const Panel = styled.section`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 900px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`

const Card = styled.button<{ $tone: 'primary' | 'warn' | 'danger' | 'plain' }>`
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  min-width: 0;
  padding: 14px 16px;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};
  text-align: left;
  cursor: ${props => (props.disabled ? 'default' : 'pointer')};
  transition: border-color 160ms ease, transform 160ms ease;

  &:not(:disabled):hover {
    border-color: ${props => (props.$tone === 'danger' ? railTheme.red : railTheme.brand)};
    transform: translateY(-1px);
  }

  &:disabled { opacity: 0.72; }
`

const Label = styled.span`
  color: ${railTheme.text3};
  font-size: 12px;
`

const Count = styled.span<{ $tone: 'primary' | 'warn' | 'danger' | 'plain' }>`
  color: ${props =>
    props.$tone === 'danger' ? railTheme.red
      : props.$tone === 'warn' ? railTheme.amber
        : props.$tone === 'primary' ? railTheme.brandDeep
          : railTheme.text2};
  font-size: 26px;
  font-weight: 720;
  line-height: 1.1;
`

const Hint = styled.span`
  overflow: hidden;
  color: ${railTheme.text3};
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  width: 100%;
`

const stageMeta: Record<string, { label: string; tone: 'primary' | 'warn' | 'danger' | 'plain' }> = {
  executing: { label: '整改中', tone: 'primary' },
  confirming: { label: '待确认', tone: 'warn' },
  accepting: { label: '待验收', tone: 'plain' },
  overdue: { label: '已逾期', tone: 'danger' },
}

/** R2 我的待办：登录员工只看自己名下任务（ownerUserId 过滤由后端完成）。 */
export function GovernanceMyTodo({ onOpenTask }: { onOpenTask?: (taskId: number) => void }) {
  const user = authSession.get()
  const isGovernanceEmployee = Boolean(user && user.userId.startsWith('emp-'))
  const myTasks = useQuery({
    queryKey: ['governance-my-tasks', user?.userId],
    queryFn: () => getGovernanceTasks({ ownerUserId: user?.userId ?? '' }),
    enabled: isGovernanceEmployee,
  })

  const model = useMemo(() => {
    const today = new Date()
    const tasks = myTasks.data ?? []
    const open = tasks.filter(task => task.status !== 'COMPLETED')
    const counts = {
      executing: open.filter(task => ['DRAFT', 'IN_PROGRESS', 'REWORK_REQUIRED'].includes(task.status)).length,
      confirming: open.filter(task => task.status === 'PENDING_CONFIRMATION').length,
      accepting: open.filter(task => task.status === 'PENDING_ACCEPTANCE').length,
      overdue: open.filter(task => dueDayDiff(task.dueDate, today) !== null && (dueDayDiff(task.dueDate, today) ?? 0) < 0).length,
    }
    const overdueEscalated = open.filter(task => isTaskEscalated(task, today)).length
    const focus = open.find(task => task.status === 'PENDING_ACCEPTANCE')
      ?? open.find(task => task.status === 'PENDING_CONFIRMATION')
      ?? open.find(task => task.status === 'IN_PROGRESS' || task.status === 'REWORK_REQUIRED')
      ?? open[0]
    return { counts, focus, overdueEscalated }
  }, [myTasks.data])

  if (!user || !isGovernanceEmployee) return null
  const cards: Array<{ key: keyof typeof stageMeta; count: number; hint: string }> = [
    { key: 'executing', count: model.counts.executing, hint: '我负责、尚未完成的任务' },
    { key: 'confirming', count: model.counts.confirming, hint: '已提交、等待业务确认' },
    { key: 'accepting', count: model.counts.accepting, hint: '确认通过、等待质量验收' },
    {
      key: 'overdue',
      count: model.counts.overdue,
      hint: model.overdueEscalated > 0
        ? `其中 ${model.overdueEscalated} 个已升级管理员，请尽快完成或联系管理员改派`
        : '已过截止日，需尽快跟进',
    },
  ]
  const totalOpen = model.counts.executing + model.counts.confirming + model.counts.accepting

  return (
    <Panel aria-label="我的待办">
      {cards.map(card => {
        const meta = stageMeta[card.key]
        const disabled = card.count === 0
        return (
          <Card
            key={card.key}
            $tone={meta.tone}
            disabled={disabled}
            aria-label={`${meta.label} ${card.count} 个`}
            onClick={() => { if (!disabled && model.focus) onOpenTask?.(model.focus.id) }}
          >
            <Label>{meta.label} · {user.name}</Label>
            <Count $tone={meta.tone}>{card.count}</Count>
            <Hint>{disabled ? '当前没有该状态任务' : card.hint}</Hint>
          </Card>
        )
      })}
      <div style={{ gridColumn: '1 / -1' }}>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>
          {totalOpen === 0 ? '名下的治理任务都已完成或尚未分派' : `共 ${totalOpen} 个未完成任务`}
          {model.focus ? ' · 点击任一计数进入一个待办任务' : ''}
        </Typography.Text>
      </div>
    </Panel>
  )
}
