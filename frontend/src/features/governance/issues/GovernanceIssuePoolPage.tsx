import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Drawer, Form, Input, Select, Table, Typography } from 'antd'
import type { ColumnsType, TableRowSelection } from 'antd/es/table/interface'
import { useState } from 'react'
import styled from 'styled-components'
import { FilterGrid } from '../../../components/FilterGrid'
import { createGovernanceTask, getGovernanceEmployees, getGovernanceIssues } from '../api'
import type { GovernanceField, GovernanceIssue, GovernanceIssueStatus } from '../types'
import { GovernancePage, GovernancePageHeader, GovernancePanel } from '../components/GovernanceLayout'

const SelectionHint = styled(Typography.Text)`
  align-self: center;
`

type FormValues = { name: string; ownerUserId: string; dueDate: string }
const fieldOptions = [{ value: 'DESCRIPTION', label: '功能说明' }, { value: 'SPECIALTIES', label: '专业类别' }, { value: 'OWNER', label: '责任人' }, { value: 'SCOPE', label: '适用范围' }]
const statusOptions = [{ value: 'OPEN', label: '待处理' }, { value: 'CLAIMED', label: '已认领' }, { value: 'RESOLVED', label: '已解决' }]
const formatIssueTime = (value: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'

export function GovernanceIssuePoolPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [field, setField] = useState<GovernanceField>()
  const [status, setStatus] = useState<GovernanceIssueStatus>('OPEN')
  const [assetId, setAssetId] = useState<number>()
  const [selectedIds, setSelectedIds] = useState<React.Key[]>([])
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [form] = Form.useForm<FormValues>()
  const issuesQuery = useQuery({ queryKey: ['governance-issues', field, status, assetId], queryFn: () => getGovernanceIssues({ field, status, assetId }) })
  const employeesQuery = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 300_000 })
  const createMutation = useMutation({
    mutationFn: (values: FormValues) => createGovernanceTask({ name: values.name, issueIds: selectedIds.map(Number), ownerUserId: values.ownerUserId, ownerName: employeesQuery.data?.find(item => item.id === values.ownerUserId)?.name, dueDate: values.dueDate, scope: '问题池选择' }),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['governance-issues'] }); await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] }); setDrawerOpen(false); setSelectedIds([]); form.resetFields(); void message.success('治理任务已创建') },
  })
  const columns: ColumnsType<GovernanceIssue> = [
    { title: '问题 ID', dataIndex: 'id', width: 95 }, { title: '资产 ID', dataIndex: 'assetId', width: 95 },
    { title: '目标字段', dataIndex: 'targetField', width: 120, render: value => fieldOptions.find(item => item.value === value)?.label ?? value },
    { title: '问题类型', dataIndex: 'issueType', width: 130 }, { title: '原始路径', dataIndex: 'targetPath', ellipsis: true },
    { title: '严重度', dataIndex: 'severity', width: 90 }, { title: '阻塞', dataIndex: 'blocking', width: 70, render: value => value ? '是' : '否' },
    { title: '发现时间', dataIndex: 'createdAt', width: 165, render: formatIssueTime },
    { title: '修改时间', dataIndex: 'updatedAt', width: 165, render: formatIssueTime },
  ]
  const rowSelection: TableRowSelection<GovernanceIssue> = { selectedRowKeys: selectedIds, preserveSelectedRowKeys: true, onChange: setSelectedIds, getCheckboxProps: issue => ({ 'aria-label': `选择问题 ${issue.id}`, disabled: issue.status !== 'OPEN' }) }
  return <GovernancePage>
    <GovernancePageHeader
      title="字段问题池"
      subtitle="筛选问题并按问题集合创建治理任务"
      actions={<Button type="primary" icon={<PlusOutlined aria-hidden />} disabled={!selectedIds.length} onClick={() => setDrawerOpen(true)}>创建治理任务</Button>}
    />
    <GovernancePanel>
      <FilterGrid>
        <Select aria-label="目标字段" allowClear placeholder="全部字段" options={fieldOptions} value={field} onChange={setField} />
        <Select aria-label="问题状态" allowClear placeholder="全部状态" options={statusOptions} value={status} onChange={setStatus} />
        <Input aria-label="资产 ID" placeholder="输入资产 ID" inputMode="numeric" onChange={event => setAssetId(event.target.value ? Number(event.target.value) : undefined)} />
        <SelectionHint type="secondary">已选择 {selectedIds.length} 项，翻页后保留</SelectionHint>
      </FilterGrid>
    </GovernancePanel>
    {issuesQuery.isError && <Alert type="error" showIcon message="问题池加载失败" />}
    <GovernancePanel>
      <Table rowKey="id" size="small" loading={issuesQuery.isLoading} rowSelection={rowSelection} columns={columns} dataSource={issuesQuery.data ?? []} scroll={{ x: 1150 }} pagination={{ pageSize: 10, showSizeChanger: false }} />
    </GovernancePanel>
    <Drawer title={`创建治理任务 · ${selectedIds.length} 个问题`} open={drawerOpen} onClose={() => setDrawerOpen(false)} width={420} extra={<Button type="primary" loading={createMutation.isPending} onClick={() => form.submit()}>确认创建</Button>}>
      {createMutation.error && <Alert type="error" showIcon message={createMutation.error.message} style={{ marginBottom: 16 }} />}
      <Form form={form} layout="vertical" onFinish={values => createMutation.mutate(values)}><Form.Item name="name" label="任务名称" rules={[{ required: true }]}><Input /></Form.Item><Form.Item name="ownerUserId" label="负责人" rules={[{ required: true }]}><Select options={(employeesQuery.data ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item><Form.Item name="dueDate" label="截止日期" rules={[{ required: true }]}><Input type="date" /></Form.Item></Form>
    </Drawer>
  </GovernancePage>
}
