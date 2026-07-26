import { Navigate, useLocation } from 'react-router-dom'
import { Tabs } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import { GovernanceIssuePoolPage } from './issues/GovernanceIssuePoolPage'
import { GovernanceOverviewPage } from './overview/GovernanceOverviewPage'
import { GovernanceTaskDetailPage } from './tasks/GovernanceTaskDetailPage'

const Workspace = styled.main`width:100%; padding:4px 0 28px;`

export function GovernancePage() {
  const location = useLocation()
  const [taskId, setTaskId] = useState<number>()
  if (location.pathname === '/governance') return <Navigate to="/sys/drawing" replace />
  if (taskId) return <Workspace><GovernanceTaskDetailPage taskId={taskId} onBack={() => setTaskId(undefined)} /></Workspace>
  return <Workspace><Tabs defaultActiveKey="overview" items={[
    { key: 'overview', label: '治理总览', children: <GovernanceOverviewPage onOpenTask={setTaskId} /> },
    { key: 'issues', label: '字段问题池', children: <GovernanceIssuePoolPage /> },
  ]} /></Workspace>
}
