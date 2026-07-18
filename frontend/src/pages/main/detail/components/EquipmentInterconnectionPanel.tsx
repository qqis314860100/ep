import { LinkOutlined } from '@ant-design/icons'
import { Button, Empty, Space, Tag, Typography } from 'antd'
import styled from 'styled-components'
import { useEquipmentInterconnections } from '../../../../hooks/useAssets'

const Panel = styled.section`
  padding: 18px 22px;
  background: #f8faf8;
  border: 1px solid #e4e8e3;
  border-radius: 8px;
`

interface EquipmentInterconnectionPanelProps {
  equipmentCode?: string
}

export function EquipmentInterconnectionPanel({ equipmentCode }: EquipmentInterconnectionPanelProps) {
  const query = useEquipmentInterconnections(equipmentCode)
  if (!equipmentCode) return null
  return (
    <Panel>
      <Typography.Title level={5} style={{ margin: 0 }}>设备互联数据</Typography.Title>
      {query.isLoading ? <Typography.Text type="secondary">正在读取互联数据...</Typography.Text> : query.data?.length ? query.data.map((link) => (
        <Space key={link.id} wrap size={8} style={{ marginTop: 10 }}>
          <Tag color="green">{link.equipmentCode}</Tag>
          <Typography.Text>{link.equipmentName}</Typography.Text>
          <Typography.Text type="secondary">{link.base} / {link.productionLine} / {link.processSection}</Typography.Text>
          <Button type="link" size="small" icon={<LinkOutlined />} href={link.dataReference} target="_blank" rel="noreferrer">打开拉线数据</Button>
        </Space>
      )) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无设备互联数据" />}
    </Panel>
  )
}
