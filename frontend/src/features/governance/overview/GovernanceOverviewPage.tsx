import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import styled from 'styled-components'
import { getGovernanceTasks } from '../api'
import { GovernanceProgressStrip } from '../shared/GovernanceProgressStrip'
import { GovernanceStatusTag } from '../shared/GovernanceStatusTag'
import type { GovernanceTask } from '../types'

const Header = styled.div`display:flex; align-items:flex-end; justify-content:space-between; gap:16px; margin-bottom:16px;`

export function GovernanceOverviewPage({ onOpenTask }: { onOpenTask?: (taskId: number) => void }) {
  const tasksQuery = useQuery({ queryKey: ['governance-tasks'], queryFn: () => getGovernanceTasks() })
  const columns: ColumnsType<GovernanceTask> = [
    { title: '任务', dataIndex: 'name', width: 190, render: (name, task) => <Button type="link" onClick={() => onOpenTask?.(task.id)} style={{ padding: 0 }}>{name}</Button> },
    { title: '阶段', dataIndex: 'status', width: 110, render: (status) => <GovernanceStatusTag status={status} /> },
    { title: '自动阶段进度', key: 'progress', width: 360, render: (_, task) => <GovernanceProgressStrip progress={task.progress} legacyCompleted={task.completed} legacyTotal={task.total} /> },
    { title: '阻塞', key: 'blocked', width: 70, render: (_, task) => task.progress?.blocked ?? 0 },
    { title: '返工', key: 'rework', width: 70, render: (_, task) => task.progress?.reworkRequired ?? 0 },
    { title: '负责人', dataIndex: 'owner', width: 110 },
    { title: '截止日期', dataIndex: 'dueDate', width: 120 },
  ]
  return <section>
    <Header><div><Typography.Title level={3} style={{ margin: 0 }}>治理总览</Typography.Title><Typography.Text type="secondary">按任务查看闭环阶段、覆盖率和异常数量</Typography.Text></div></Header>
    {tasksQuery.isError && <Alert type="error" showIcon message="治理任务加载失败" />}
    <Table rowKey="id" size="small" loading={tasksQuery.isLoading} columns={columns} dataSource={tasksQuery.data ?? []} scroll={{ x: 1050 }} pagination={{ pageSize: 12, showSizeChanger: false }} />
  </section>
}
