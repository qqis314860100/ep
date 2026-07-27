import { CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { Tag } from 'antd'
import styled from 'styled-components'
import type { GovernanceTaskStatus } from '../types'

type MilestoneState = 'pending' | 'active' | 'done'

const milestones = ['计划锁定', '业务确认', '质量验收', '正式应用'] as const

const Strip = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-height: 32px;
`

function milestoneStates(status: GovernanceTaskStatus): MilestoneState[] {
  if (status === 'DRAFT') return ['pending', 'pending', 'pending', 'pending']
  if (status === 'IN_PROGRESS' || status === 'REWORK_REQUIRED') return ['done', 'pending', 'pending', 'pending']
  if (status === 'PENDING_CONFIRMATION') return ['done', 'active', 'pending', 'pending']
  if (status === 'PENDING_ACCEPTANCE') return ['done', 'done', 'active', 'pending']
  return ['done', 'done', 'done', 'done']
}

export function GovernanceMilestoneStrip({ status }: { status: GovernanceTaskStatus }) {
  const states = milestoneStates(status)

  return <Strip aria-label="治理里程碑">
    {milestones.map((label, index) => {
      const state = states[index]
      return <Tag
        key={label}
        data-state={state}
        color={state === 'done' ? 'success' : state === 'active' ? 'processing' : 'default'}
        icon={state === 'done' ? <CheckCircleOutlined /> : <ClockCircleOutlined />}
      >
        {label}
      </Tag>
    })}
    {status === 'REWORK_REQUIRED' && <Tag color="error" icon={<ExclamationCircleOutlined />}>返工中</Tag>}
  </Strip>
}
