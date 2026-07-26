import { Progress, Space, Typography } from 'antd'
import styled from 'styled-components'
import type { GovernanceProgress } from '../types'

const Strip = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(90px, 1fr));
  gap: 12px;
  min-width: 300px;
`

const percent = (value: number, total: number) => total > 0 ? Math.round(value * 100 / total) : 0

export function GovernanceProgressStrip({ progress, legacyCompleted, legacyTotal }: {
  progress?: GovernanceProgress | null
  legacyCompleted?: number
  legacyTotal?: number
}) {
  if (!progress) {
    const completed = legacyCompleted ?? 0
    const total = legacyTotal ?? 0
    return <Space size={6}><Progress percent={percent(completed, total)} size="small" style={{ width: 110 }} /><Typography.Text type="secondary">历史进度，只读</Typography.Text></Space>
  }
  const entries = [
    ['执行', progress.submitted],
    ['确认', progress.confirmed],
    ['验收', progress.accepted],
  ] as const
  return <Strip>{entries.map(([label, value]) => <div key={label}><Typography.Text type="secondary">{label} {value}/{progress.total}</Typography.Text><Progress percent={percent(value, progress.total)} size="small" showInfo={false} /></div>)}</Strip>
}
