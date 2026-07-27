import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, DatePicker, Form, Input, InputNumber, Segmented, Select, Skeleton, Space, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { useState } from 'react'
import { createGovernancePlan } from '../api'
import type { CreateGovernancePlanInput, GovernanceEmployee, GovernanceIssue, GovernancePlan, GovernanceTaskStatus } from '../types'
import { GovernanceGanttView } from './GovernanceGanttView'

type PlanFormValues = Omit<CreateGovernancePlanInput, 'plannedStart' | 'plannedEnd'> & { dates: [dayjs.Dayjs, dayjs.Dayjs] }

export function GovernancePlanEditor({ taskId, plans, issues, employees, editable, taskStatus, loading, error }: {
  taskId: number
  plans: GovernancePlan[]
  issues: GovernanceIssue[]
  employees: GovernanceEmployee[]
  editable: boolean
  taskStatus: GovernanceTaskStatus
  loading?: boolean
  error?: string
}) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [view, setView] = useState<'table' | 'gantt'>('table')
  const [form] = Form.useForm<PlanFormValues>()
  const createMutation = useMutation({
    mutationFn: (values: PlanFormValues) => createGovernancePlan(taskId, { ...values, plannedStart: values.dates[0].format('YYYY-MM-DD'), plannedEnd: values.dates[1].format('YYYY-MM-DD') }),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['governance-plans', taskId] }); setOpen(false); form.resetFields(); void message.success('计划项已添加') },
  })
  const columns: ColumnsType<GovernancePlan> = [
    { title: '计划项', dataIndex: 'title' }, { title: '责任人', dataIndex: 'responsibleUserId', width: 110, render: id => employees.find(item => item.id === id)?.name ?? id ?? '-' },
    { title: '排期', width: 190, render: (_, item) => item.plannedStart && item.plannedEnd ? `${item.plannedStart} 至 ${item.plannedEnd}` : '-' },
    { title: '数量', width: 110, render: (_, item) => `${item.plannedQuantity} ${item.quantityUnit}` },
    { title: '依赖', dataIndex: 'dependencyIds', width: 110, render: ids => ids.length ? ids.join(', ') : '无' },
    { title: '问题', dataIndex: 'issueIds', width: 110, render: ids => ids?.length ?? 0 },
  ]
  return <div>
    <Space wrap style={{ width: '100%', justifyContent: 'space-between', marginBottom: 10 }}>
      <Space>
        <Button
          icon={open ? <DeleteOutlined aria-hidden /> : <PlusOutlined aria-hidden />}
          disabled={!editable}
          onClick={() => { setView('table'); setOpen(value => !value) }}
        >
          编辑计划
        </Button>
        {!editable && <span>任务启动后计划已锁定</span>}
      </Space>
      <Segmented
        value={view}
        options={[{ label: '表格', value: 'table' }, { label: '甘特图', value: 'gantt' }]}
        onChange={value => setView(value as 'table' | 'gantt')}
      />
    </Space>
    {open && <Form form={form} layout="inline" onFinish={values => createMutation.mutate(values)} style={{ marginBottom: 16, rowGap: 10 }} initialValues={{ plannedQuantity: 1, quantityUnit: '字段', dependencyIds: [], issueIds: [] }}>
      <Form.Item name="title" label="计划项" rules={[{ required: true }]}><Input /></Form.Item>
      <Form.Item name="responsibleUserId" label="责任人" rules={[{ required: true }]}><Select style={{ width: 130 }} options={employees.map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
      <Form.Item name="dates" label="排期" rules={[{ required: true }]}><DatePicker.RangePicker /></Form.Item>
      <Form.Item name="plannedQuantity" label="数量" rules={[{ required: true }]}><InputNumber min={1} /></Form.Item>
      <Form.Item name="quantityUnit" label="单位" rules={[{ required: true }]}><Select style={{ width: 90 }} options={['字段', '资产', '关系', '文件'].map(value => ({ value, label: value }))} /></Form.Item>
      <Form.Item name="dependencyIds" label="依赖"><Select mode="multiple" style={{ minWidth: 130 }} options={plans.map(item => ({ value: item.id, label: item.title }))} /></Form.Item>
      <Form.Item name="issueIds" label="治理问题"><Select mode="multiple" style={{ minWidth: 160 }} options={issues.map(item => ({ value: item.id, label: `#${item.id} · 资产 ${item.assetId}` }))} /></Form.Item>
      <Form.Item><Button type="primary" htmlType="submit" loading={createMutation.isPending}>添加计划项</Button></Form.Item>
    </Form>}
    {loading
      ? <Skeleton active paragraph={{ rows: 3 }} />
      : error
        ? <Alert type="error" showIcon message={error} />
        : view === 'table'
          ? <Table rowKey="id" size="small" columns={columns} dataSource={plans} pagination={false} locale={{ emptyText: '尚未编排计划项' }} />
          : <GovernanceGanttView plans={plans} employees={employees} taskStatus={taskStatus} />}
  </div>
}
