import { ArrowLeftOutlined, CheckCircleOutlined, EditOutlined, PlayCircleOutlined, SafetyCertificateOutlined, SwapOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Collapse, Descriptions, Modal, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import styled from 'styled-components'
import { useNavigate } from 'react-router-dom'
import { getGovernanceEmployees, getGovernanceIssues, getGovernancePlans, getGovernanceTask, openGovernanceRework, reassignGovernanceTask, startGovernanceTask } from '../api'
import { currentActor } from '../../auth/session'
import { GovernanceProgressStrip } from '../shared/GovernanceProgressStrip'
import { GovernanceStatusTag } from '../shared/GovernanceStatusTag'
import type { GovernanceIssue } from '../types'
import { GovernanceMilestoneStrip } from './GovernanceMilestoneStrip'
import { GovernancePlanEditor } from './GovernancePlanEditor'
import { dueDayDiff } from '../components/governanceRailModel'

const Header = styled.div`display:flex; align-items:flex-start; justify-content:space-between; gap:16px; margin-bottom:16px;`
const Section = styled.section`padding:18px 0; border-top:1px solid #dfe5e2; h4{margin-top:0;}`
const SnapshotGrid = styled.div`display:grid; grid-template-columns:repeat(auto-fit,minmax(180px,1fr)); gap:12px 24px;`
const SnapshotItem = styled.div`display:grid; gap:2px;`

export function GovernanceTaskDetailPage({ taskId, onBack }: { taskId: number; onBack?: () => void }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [reassignOpen, setReassignOpen] = useState(false)
  const [nextOwnerUserId, setNextOwnerUserId] = useState<string>()
  const taskQuery = useQuery({ queryKey: ['governance-task', taskId], queryFn: () => getGovernanceTask(taskId) })
  const plansQuery = useQuery({ queryKey: ['governance-plans', taskId], queryFn: () => getGovernancePlans(taskId) })
  const issuesQuery = useQuery({ queryKey: ['governance-issues', 'task', taskId], queryFn: async () => (await getGovernanceIssues()).filter(issue => issue.taskId === taskId) })
  const employeesQuery = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 300_000 })
  const startMutation = useMutation({ mutationFn: () => startGovernanceTask(taskId, { version: taskQuery.data?.version ?? 0, actorUserId: currentActor().userId }), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['governance-task', taskId] }); await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] }) } })
  const reworkMutation = useMutation({ mutationFn: () => openGovernanceRework(taskId, { taskVersion: taskQuery.data?.version ?? 0, reason: '业务确认退回', actorUserId: currentActor().userId }), onSuccess: () => navigate(`/sys/drawing/tasks/${taskId}/execute`) })
  const reassignMutation = useMutation({
    mutationFn: (ownerUserId: string) => reassignGovernanceTask(taskId, { ownerUserId, expectedVersion: taskQuery.data?.version ?? 0 }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['governance-task', taskId] })
      await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] })
      setReassignOpen(false)
      setNextOwnerUserId(undefined)
      void message.success('任务已移交给新负责人')
    },
  })
  const task = taskQuery.data
  const issueColumns: ColumnsType<GovernanceIssue> = [{ title: '问题 ID', dataIndex: 'id', width: 100 }, { title: '资产 ID', dataIndex: 'assetId', width: 100 }, { title: '字段', dataIndex: 'targetField', width: 130 }, { title: '类型', dataIndex: 'issueType' }, { title: '状态', dataIndex: 'status', width: 100 }]
  if (taskQuery.isLoading) return <Typography.Text>正在加载任务详情...</Typography.Text>
  if (!task) return <Alert type="error" showIcon message="任务详情加载失败" />
  const editable = task.status === 'DRAFT' && task.editable !== false
  const legacy = task.workflowVersion === 'LEGACY_PROGRESS'
  const completed = task.status === 'COMPLETED'
  // R3 逾期升级横幅：逾期满 3 天的闭环任务=已升级（红色 + 移交改派）；其余逾期=提醒（琥珀色）。
  const rawDiff = dueDayDiff(task.dueDate, new Date())
  const overdueDays = rawDiff !== null && rawDiff < 0 ? -rawDiff : null
  const escalated = !legacy && overdueDays !== null && overdueDays >= 3
  const scopeSnapshot = task.scopeSnapshot
  const ruleSnapshot = task.ruleSnapshot ?? scopeSnapshot?.ruleSnapshot
  const assigneeOptions = (employeesQuery.data ?? [])
    .filter(employee => employee.id !== task.assigneeId)
    .map(employee => ({ value: employee.id, label: employee.name }))
  return <article>
    <Header><Space align="start"><Button aria-label="返回治理总览" icon={<ArrowLeftOutlined aria-hidden />} onClick={onBack ?? (() => navigate('/sys/drawing'))} /><div><Typography.Title level={3} style={{ margin: 0 }}>{task.name}</Typography.Title><Space><GovernanceStatusTag status={task.status} /><Typography.Text type="secondary">任务 #{task.id}</Typography.Text></Space></div></Space><Space>{legacy ? <Typography.Text type="secondary">历史任务只读</Typography.Text> : <>{!completed && <Button icon={<SwapOutlined aria-hidden />} onClick={() => { setNextOwnerUserId(undefined); setReassignOpen(true) }}>移交</Button>}{task.status === 'DRAFT' && <Button type="primary" icon={<PlayCircleOutlined aria-hidden />} loading={startMutation.isPending} onClick={() => startMutation.mutate()}>启动任务</Button>}{task.status === 'IN_PROGRESS' && <Button type="primary" icon={<EditOutlined aria-hidden />} onClick={() => navigate(`/sys/drawing/tasks/${taskId}/execute`)}>进入清洗</Button>}{task.status === 'REWORK_REQUIRED' && <Button type="primary" icon={<EditOutlined aria-hidden />} loading={reworkMutation.isPending} onClick={() => reworkMutation.mutate()}>开启返工</Button>}{task.status === 'PENDING_CONFIRMATION' && <Button type="primary" icon={<CheckCircleOutlined aria-hidden />} onClick={() => navigate(`/sys/drawing/tasks/${taskId}/confirm`)}>进入确认</Button>}{task.status === 'PENDING_ACCEPTANCE' && <Button type="primary" icon={<SafetyCertificateOutlined aria-hidden />} onClick={() => navigate(`/sys/drawing/tasks/${taskId}/accept`)}>进入验收</Button>}</>}</Space></Header>
    <Modal
      title="移交任务"
      open={reassignOpen}
      onCancel={() => setReassignOpen(false)}
      okText="确认移交"
      cancelText="取消"
      okButtonProps={{ disabled: !nextOwnerUserId || reassignMutation.isPending, loading: reassignMutation.isPending }}
      onOk={() => { if (nextOwnerUserId) reassignMutation.mutate(nextOwnerUserId) }}
      destroyOnHidden
    >
      <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
        把任务从「{task.owner || '当前负责人'}」移交给另一位员工跟进，原负责人解除跟进责任。已完成任务与历史任务不可移交。
      </Typography.Paragraph>
      {reassignMutation.error && <Alert type="error" showIcon message={reassignMutation.error.message} style={{ marginBottom: 12 }} />}
      <Typography.Text strong>新负责人</Typography.Text>
      <Select
        aria-label="选择新负责人"
        style={{ width: '100%', marginTop: 8 }}
        placeholder="选择员工"
        value={nextOwnerUserId}
        onChange={setNextOwnerUserId}
        options={assigneeOptions}
        showSearch
        optionFilterProp="label"
      />
    </Modal>
    {(startMutation.error || reworkMutation.error) && <Alert type="error" showIcon message={(startMutation.error ?? reworkMutation.error)?.message} style={{ marginBottom: 16 }} />}
    {!completed && overdueDays !== null && (
      <Alert
        type={escalated ? 'error' : 'warning'}
        showIcon
        style={{ marginBottom: 16 }}
        message={escalated ? `任务已逾期 ${overdueDays} 天，已升级提醒内容管理员` : `任务已逾期 ${overdueDays} 天`}
        description={escalated
          ? '任务超过计划完成日期多日仍未闭环，系统已通知内容管理员跟进；如需更换负责人，可直接移交改派。'
          : legacy
            ? '该任务已超过计划完成日期，请负责人尽快跟进处理。'
            : '已超过计划完成日期，请尽快推进当前环节（逾期满 3 天将自动升级提醒内容管理员）。'}
        action={escalated ? (
          <Button size="small" type="primary" onClick={() => { setNextOwnerUserId(undefined); setReassignOpen(true) }}>移交改派</Button>
        ) : undefined}
      />
    )}
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} items={[{ key: 'owner', label: '负责人', children: task.owner }, { key: 'due', label: '截止日期', children: task.dueDate }, { key: 'round', label: '治理轮次', children: task.currentRound ?? 0 }, { key: 'scope', label: '治理范围', children: task.scope }]} />
    <Section><GovernanceMilestoneStrip status={task.status} workflowVersion={task.workflowVersion} progress={task.progress} currentRound={task.currentRound} completed={task.completed} total={task.total} /></Section>
    <Section><Typography.Title level={4}>计划依赖与责任</Typography.Title><GovernancePlanEditor
      taskId={taskId}
      plans={plansQuery.data ?? []}
      issues={issuesQuery.data ?? []}
      employees={employeesQuery.data ?? []}
      editable={editable}
      taskStatus={task.status}
      loading={plansQuery.isLoading}
      error={plansQuery.error instanceof Error ? plansQuery.error.message : undefined}
    /></Section>
    <Section><Typography.Title level={4}>阶段进度</Typography.Title><GovernanceProgressStrip progress={task.progress} legacyCompleted={task.completed} legacyTotal={task.total} /></Section>
    <Section><Typography.Title level={4}>治理基线</Typography.Title>{scopeSnapshot && ruleSnapshot ? <>
      <SnapshotGrid>
        <SnapshotItem><Typography.Text type="secondary">治理对象</Typography.Text><Typography.Text>{scopeSnapshot.itemCount} 项，涉及 {scopeSnapshot.assetIds.length} 份资产</Typography.Text></SnapshotItem>
        <SnapshotItem><Typography.Text type="secondary">关联问题</Typography.Text><Typography.Text>{scopeSnapshot.claimedIssueIds.length} 项</Typography.Text></SnapshotItem>
        <SnapshotItem><Typography.Text type="secondary">数据标准</Typography.Text><Typography.Text>{ruleSnapshot.dataStandardId} · v{ruleSnapshot.dataStandardVersion}</Typography.Text></SnapshotItem>
        <SnapshotItem><Typography.Text type="secondary">质量策略</Typography.Text><Typography.Text>{ruleSnapshot.qualityPolicyId} · v{ruleSnapshot.qualityPolicyVersion}</Typography.Text></SnapshotItem>
        <SnapshotItem><Typography.Text type="secondary">固化责任</Typography.Text><Typography.Text>{scopeSnapshot.createdBy} · {scopeSnapshot.frozenAt}</Typography.Text></SnapshotItem>
      </SnapshotGrid>
      <Collapse ghost size="small" style={{ marginTop: 8 }} items={[{ key: 'technical', label: '查看技术快照', children: <Typography.Paragraph code style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>{JSON.stringify({ scopeSnapshot, ruleSnapshot }, null, 2)}</Typography.Paragraph> }]} />
    </> : <Typography.Text type="secondary">{legacy ? '历史任务未保存结构化治理基线' : '任务启动后固化治理范围、数据标准和质量策略'}</Typography.Text>}</Section>
    <Section><Typography.Title level={4}>关联治理问题</Typography.Title>{issuesQuery.isLoading
      ? <Typography.Text type="secondary">正在加载关联问题...</Typography.Text>
      : (issuesQuery.data?.length ?? 0) === 0
        ? <Typography.Text type="secondary">当前没有关联治理问题</Typography.Text>
        : <Table rowKey="id" size="small" columns={issueColumns} dataSource={issuesQuery.data} pagination={false} />}</Section>
  </article>
}
