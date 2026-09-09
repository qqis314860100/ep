import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { GovernanceRailHome } from './components/GovernanceRailHome'
import { GovernanceInventoryPage } from './inventory/GovernanceInventoryPage'

const Workspace = styled.main`width:100%; padding:4px 0 28px;`

export function GovernancePage() {
  const location = useLocation()
  const navigate = useNavigate()
  if (location.pathname === '/governance') return <Navigate to="/sys/drawing" replace />
  if (location.pathname.includes('/inventory')) return <Workspace><GovernanceInventoryPage /></Workspace>
  return <Workspace><GovernanceRailHome onOpenTask={taskId => navigate(`/sys/drawing/tasks/${taskId}`)} /></Workspace>
}
