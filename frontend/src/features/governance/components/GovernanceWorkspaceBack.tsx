import { ArrowLeftOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'

const BackButton = styled(Button)`
  height: 28px;
  margin: 0 0 5px -7px;
  padding-inline: 7px;
  color: #5b6573;
  font-size: 12px;

  &:hover {
    color: #2f7567 !important;
    background: #edf5f2 !important;
  }
`

export function GovernanceWorkspaceBack({ onBack }: { onBack?: () => void }) {
  const navigate = useNavigate()
  return (
    <BackButton type="text" icon={<ArrowLeftOutlined aria-hidden />} onClick={onBack ?? (() => navigate('/sys/drawing'))}>
      返回治理工作台
    </BackButton>
  )
}
