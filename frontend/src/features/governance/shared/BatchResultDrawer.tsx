import { Button, Drawer, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import type { BatchItemResult } from '../types'

const outcome = {
  SUCCESS: { label: '成功', color: 'success' },
  CONFLICT: { label: '冲突', color: 'error' },
  VALIDATION_FAILED: { label: '校验失败', color: 'warning' },
} as const

export function BatchResultDrawer({ open, results, onClose, onRefresh }: { open: boolean; results: BatchItemResult[]; onClose: () => void; onRefresh: (itemId: number) => void }) {
  const count = (name: BatchItemResult['outcome']) => results.filter(item => item.outcome === name).length
  const columns: ColumnsType<BatchItemResult> = [
    { title: '治理项', dataIndex: 'itemId', width: 90 },
    { title: '结果', dataIndex: 'outcome', width: 110, render: value => <Tag color={outcome[value as keyof typeof outcome].color}>{outcome[value as keyof typeof outcome].label}</Tag> },
    { title: '原因', dataIndex: 'message', render: value => value || '-' },
    { title: '操作', key: 'action', width: 100, render: (_, row) => row.outcome === 'CONFLICT' ? <Button type="link" onClick={() => onRefresh(row.itemId)}>刷新</Button> : null },
  ]
  return <Drawer title="批量提交结果" width={620} open={open} onClose={onClose}>
    <Space wrap style={{ marginBottom: 16 }}><Typography.Text strong>成功 {count('SUCCESS')}</Typography.Text><Typography.Text type="danger" strong>冲突 {count('CONFLICT')}</Typography.Text><Typography.Text type="warning" strong>校验失败 {count('VALIDATION_FAILED')}</Typography.Text></Space>
    <Table rowKey="itemId" size="small" columns={columns} dataSource={results} pagination={false} />
  </Drawer>
}
