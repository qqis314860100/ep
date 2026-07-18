import { Empty, Typography } from 'antd'
import styled from 'styled-components'

const Surface = styled.div`
  display: grid;
  min-height: 520px;
  place-items: center;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

interface ModulePlaceholderProps {
  title: string
}

export function ModulePlaceholder({ title }: ModulePlaceholderProps) {
  return (
    <Surface>
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={<Typography.Text type="secondary">{title}将在后续开发切片中接入</Typography.Text>}
      />
    </Surface>
  )
}
