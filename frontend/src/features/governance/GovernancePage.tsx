import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Tabs } from 'antd'
import styled from 'styled-components'
import { GovernanceOverviewPage } from './overview/GovernanceOverviewPage'

const Workspace = styled.main`width:100%; padding:4px 0 28px;`

export function GovernancePage() {
  const location = useLocation()
  const navigate = useNavigate()
  const tab = location.pathname.includes('/operations') ? 'operations' : location.pathname.includes('/scans') ? 'scans' : location.pathname.includes('/mappings') ? 'mappings' : location.pathname.includes('/standards') ? 'standards' : location.pathname.includes('/issues') ? 'issues' : 'overview'
  if (location.pathname === '/governance') return <Navigate to="/sys/drawing" replace />
  return <Workspace><Tabs activeKey={tab} onChange={key => {
    if (key === 'issues') navigate('/sys/drawing/issues')
    if (key === 'standards') navigate('/sys/drawing/standards')
    if (key === 'mappings') navigate('/sys/drawing/mappings')
    if (key === 'scans') navigate('/sys/drawing/scans')
    if (key === 'operations') navigate('/sys/drawing/operations')
  }} items={[
    { key: 'overview', label: '治理总览', children: <GovernanceOverviewPage onOpenTask={taskId => navigate(`/sys/drawing/tasks/${taskId}`)} /> },
    { key: 'standards', label: '标准中心' },
    { key: 'mappings', label: '映射规则' },
    { key: 'scans', label: '自动扫描' },
    { key: 'operations', label: '治理运营' },
    { key: 'issues', label: '字段问题池' },
  ]} /></Workspace>
}
