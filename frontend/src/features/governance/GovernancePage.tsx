import { CalendarOutlined, CheckCircleOutlined, ClockCircleOutlined, DatabaseOutlined, ExclamationCircleOutlined, FileTextOutlined, InfoCircleOutlined, LockOutlined, PlayCircleOutlined, PlusOutlined, TeamOutlined, UnorderedListOutlined } from '@ant-design/icons'
import { Alert, App as AntdApp, Button, Drawer, Form, Input, InputNumber, Progress, Select, Space, Table, Tag, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { createGovernancePlan, createGovernanceTask, getGovernanceEmployees, getGovernancePlans, getGovernanceTasks, startGovernanceTask, updateGovernancePlan, updateGovernanceProgress, type CreateGovernancePlanInput, type GovernancePlan, type GovernanceTask, type GovernanceTaskStatus } from './api'

type GovernanceTaskFormValues = {
  name: string
  stage: string
  target: string
  scope: string
  owner: string
  total: number
  dueDate: string | { format: (pattern: string) => string }
}

type PlanDraft = Omit<CreateGovernancePlanInput, 'title'> & { title: string }

const emptyPlanDraft = (): PlanDraft => ({
  title: '',
  plannedStart: undefined,
  plannedEnd: undefined,
  actualStart: undefined,
  actualEnd: undefined,
  plannedQuantity: 1,
  quantityUnit: '个资产',
  assigneeId: undefined,
  dependencyIds: [],
})

const planStatusConfig = {
  TODO: { label: '未开始', color: 'default' },
  IN_PROGRESS: { label: '进行中', color: 'processing' },
  DONE: { label: '已完成', color: 'success' },
} as const

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-bottom: 10px;
  padding: 0 2px;
`

const Intro = styled(Alert)`
  margin-bottom: 10px;

  &.ant-alert-with-description {
    padding: 8px 12px;
  }

  .ant-alert-description {
    font-size: 11px;
    line-height: 17px;
  }
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #202824;
  font-size: 18px;
  font-weight: 600;
`

const Metrics = styled.div`
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  margin-bottom: 10px;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const Metric = styled.div`
  min-width: 0;
  padding: 8px 12px;
  border-right: 1px solid #e3e8e5;
  &:last-child { border-right: 0; }
`

const MetricLabel = styled.div`
  margin-bottom: 3px;
  color: #68746f;
  font-size: 12px;
`

const MetricValue = styled.div`
  color: #26312d;
  font-size: 20px;
  font-weight: 600;
`

const TableSurface = styled.div`
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const DrawerIntro = styled.div`
  margin-bottom: 18px;
  color: #5c6a64;
  font-size: 13px;
  line-height: 21px;
`

const FormGroup = styled.div`
  padding-top: 16px;
  border-top: 1px solid #e7ece9;

  & + & {
    margin-top: 8px;
  }
`

const FormGroupTitle = styled.div`
  margin-bottom: 12px;
  color: #20332c;
  font-size: 14px;
  font-weight: 650;
`

const TaskPreview = styled.div`
  margin-top: 4px;
  padding: 12px;
  color: #53625b;
  background: #f4f8f5;
  border: 1px solid #dcebe3;
  border-radius: 5px;
  font-size: 12px;
  line-height: 19px;
`

const OptionTitle = styled.div`
  color: #20332c;
  font-weight: 600;
`

const OptionHelp = styled.div`
  margin-top: 2px;
  color: #68746f;
  font-size: 12px;
  line-height: 18px;
`

const DetailSummary = styled.div`
  margin-bottom: 20px;
  padding: 14px;
  background: #f4f8f5;
  border: 1px solid #dcebe3;
  border-radius: 5px;
`

const WorkflowGuide = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 18px;
`

const WorkflowStep = styled.div<{ $active?: boolean }>`
  min-height: 76px;
  padding: 11px 12px;
  background: ${({ $active }) => $active ? '#eef7f3' : '#f7f9f7'};
  border: 1px solid ${({ $active }) => $active ? '#b9d9cc' : '#e0e7e2'};
  border-radius: 6px;
`

const WorkflowStepTop = styled.div`
  display: flex;
  align-items: center;
  gap: 7px;
  color: #214f43;
  font-size: 12px;
  font-weight: 650;
`

const WorkflowStepNumber = styled.span`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: #fff;
  background: #2f7567;
  border-radius: 50%;
  font-size: 11px;
`

const WorkflowStepHelp = styled.div`
  margin-top: 7px;
  padding-left: 27px;
  color: #68746f;
  font-size: 11px;
  line-height: 17px;
`

const PlanSection = styled.div`
  margin-top: 18px;
  padding: 14px;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const PlanSectionHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
`

const PlanSectionTitle = styled.div`
  color: #20332c;
  font-size: 14px;
  font-weight: 650;
`

const PlanSectionHint = styled.div`
  margin-top: 3px;
  color: #68746f;
  font-size: 12px;
  line-height: 18px;
`

const PlanForm = styled.div`
  margin-top: 12px;
  padding: 13px;
  background: #f7faf8;
  border: 1px solid #dcebe3;
  border-radius: 5px;
`

const PlanFormTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 7px;
  margin-bottom: 11px;
  color: #214f43;
  font-size: 13px;
  font-weight: 650;
`

const OwnerHint = styled.div`
  margin: -4px 0 10px;
  color: #68746f;
  font-size: 11px;
`

const PlanFormGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(220px, 2fr) minmax(150px, 1fr) minmax(150px, 1fr) minmax(150px, 1fr);
  gap: 10px;
  align-items: end;

  & + & {
    margin-top: 10px;
  }
`

const PlanField = styled.label`
  display: block;
  min-width: 0;
`

const PlanFieldLabel = styled.span`
  display: block;
  margin-bottom: 5px;
  color: #4f5e57;
  font-size: 11px;
`

const PlanFormActions = styled.div`
  display: flex;
  justify-content: flex-end;
  align-items: flex-end;
  height: 100%;
`

const TimelineSection = styled(PlanSection)`
  overflow: hidden;
`

const ProgressSummaryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 16px 0 18px;
`

const ProgressSummaryItem = styled.div`
  padding: 11px 12px;
  background: #f7f9f7;
  border: 1px solid #e0e7e2;
  border-radius: 5px;
`

const ProgressSummaryLabel = styled.div`
  color: #68746f;
  font-size: 11px;
`

const ProgressSummaryValue = styled.div`
  margin-top: 4px;
  color: #20332c;
  font-size: 18px;
  font-weight: 650;
`

const DetailTitle = styled.div`
  margin-bottom: 5px;
  color: #20332c;
  font-size: 16px;
  font-weight: 650;
`

const DetailMeta = styled.div`
  color: #68746f;
  font-size: 12px;
  line-height: 20px;
`

const DetailSectionTitle = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 18px 0 10px;
  color: #20332c;
  font-size: 14px;
  font-weight: 650;
`

const ProgressEditor = styled.div`
  display: flex;
  gap: 10px;
  align-items: flex-end;
  margin-top: 14px;
`

const Gantt = styled.div`
  overflow-x: auto;
  padding-bottom: 4px;
`

const GanttHeader = styled.div`
  display: grid;
  grid-template-columns: 180px minmax(520px, 1fr);
  min-width: 720px;
  color: #68746f;
  font-size: 11px;
`

const GanttScale = styled.div`
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(54px, 1fr);
  border-bottom: 1px solid #dce3df;
`

const GanttRow = styled.div`
  display: grid;
  grid-template-columns: 180px minmax(520px, 1fr);
  min-width: 720px;
  min-height: 54px;
  border-bottom: 1px solid #edf1ef;
`

const GanttLabel = styled.div`
  padding: 10px 10px 8px 0;
  color: #26312d;
  font-size: 12px;
  line-height: 17px;
`

const GanttTrack = styled.div`
  position: relative;
  background: repeating-linear-gradient(to right, transparent 0, transparent calc(14.285% - 1px), #edf1ef calc(14.285% - 1px), #edf1ef 14.285%);
`

const GanttBar = styled.div<{ $left: number; $width: number; $status: GovernancePlan['status'] }>`
  position: absolute;
  top: 16px;
  left: ${({ $left }) => `${$left}%`};
  width: ${({ $width }) => `${$width}%`};
  min-width: 8px;
  height: 22px;
  background: ${({ $status }) => $status === 'DONE' ? '#2f7567' : $status === 'IN_PROGRESS' ? '#8aaea4' : '#c9d8d2'};
  border-radius: 3px;
  box-shadow: inset 0 0 0 1px rgba(32, 51, 44, 0.08);
`

const Swimlane = styled.div`
  display: grid;
  gap: 10px;
`

const SwimlaneLane = styled.div`
  display: grid;
  grid-template-columns: 150px 1fr;
  min-height: 54px;
  border: 1px solid #dce3df;
  border-radius: 5px;
  overflow: hidden;
`

const SwimlaneOwner = styled.div`
  padding: 12px;
  color: #20332c;
  background: #f4f8f5;
  font-size: 12px;
  font-weight: 600;
`

const SwimlaneItems = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 8px 10px;
`

const SwimlaneItem = styled.div`
  padding: 7px 9px;
  color: #42524b;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 4px;
  font-size: 12px;
`

const dateValue = (value?: string) => value ? new Date(`${value}T00:00:00`).getTime() : Number.NaN

const formatShortDate = (value: string) => value.slice(5).replace('-', '/')

const formatLocalDate = (value: Date) => {
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const getPlanTimeline = (plans: GovernancePlan[]) => {
  const dates = plans.flatMap((plan) => [plan.plannedStart, plan.plannedEnd].filter((value): value is string => Boolean(value)))
  if (!dates.length) return null
  const start = Math.min(...dates.map(dateValue))
  const end = Math.max(...dates.map(dateValue))
  const day = 24 * 60 * 60 * 1000
  const totalDays = Math.max(1, Math.round((end - start) / day) + 1)
  const scale = Array.from({ length: totalDays }, (_, index) => formatLocalDate(new Date(start + index * day)))
  return { start, end, totalDays, scale }
}

const getPlanBar = (plan: GovernancePlan, timeline: NonNullable<ReturnType<typeof getPlanTimeline>>) => {
  const start = dateValue(plan.plannedStart) || timeline.start
  const end = dateValue(plan.plannedEnd) || start
  const day = 24 * 60 * 60 * 1000
  const left = Math.max(0, ((start - timeline.start) / day) / timeline.totalDays * 100)
  const width = Math.max(1.5, ((end - start) / day + 1) / timeline.totalDays * 100)
  return { left, width }
}

const processOptions = [
  { value: '盘点', label: '盘点', help: '找出缺失、重复、错误或待补充的资产资料' },
  { value: '定标准', label: '定标准', help: '确定字段、命名、分类和填写规则' },
  { value: '建字典', label: '建字典', help: '建立平台、基地、拉线、专业等标准值' },
  { value: '映射', label: '映射', help: '把旧值或自由文本对应到标准值' },
  { value: '清洗', label: '清洗', help: '批量修正、补齐或合并历史数据' },
  { value: '业务确认', label: '业务确认', help: '请业务专家确认清洗结果' },
  { value: '验收', label: '验收', help: '检查完成率和质量指标，形成验收结论' },
]

const targetOptions = [
  { value: '适用范围', label: '平台、蓝本、基地、拉线和工序段', shortLabel: '适用范围' },
  { value: '平台子类', label: '八大平台及平台子类', shortLabel: '平台分类' },
  { value: '专业类别', label: '机械、电气、液压、气动、工装等专业', shortLabel: '专业分类' },
  { value: '文件资产', label: '文件格式、预览文件、失效引用和重复文件', shortLabel: '文件资产' },
  { value: '模块关联', label: '模组标签、标准设备模块和数模关联', shortLabel: '模块关系' },
]

const statusConfig: Record<GovernanceTaskStatus, { label: string; color: string; icon: JSX.Element }> = {
  DRAFT: { label: '草稿', color: 'default', icon: <FileTextOutlined /> },
  IN_PROGRESS: { label: '进行中', color: 'processing', icon: <ClockCircleOutlined /> },
  PENDING_CONFIRMATION: { label: '待确认', color: 'gold', icon: <ExclamationCircleOutlined /> },
  PENDING_ACCEPTANCE: { label: '待验收', color: 'cyan', icon: <ExclamationCircleOutlined /> },
  REWORK_REQUIRED: { label: '需返工', color: 'red', icon: <ExclamationCircleOutlined /> },
  COMPLETED: { label: '已完成', color: 'green', icon: <CheckCircleOutlined /> },
}

export function GovernancePage() {
  const { message } = AntdApp.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [detailTask, setDetailTask] = useState<GovernanceTask | null>(null)
  const [detailMode, setDetailMode] = useState<'progress' | 'plan'>('progress')
  const [progressDraft, setProgressDraft] = useState(0)
  const [newPlanDraft, setNewPlanDraft] = useState<PlanDraft>(emptyPlanDraft)
  const [form] = Form.useForm<GovernanceTaskFormValues>()
  const selectedStage = Form.useWatch('stage', form)
  const selectedTarget = Form.useWatch('target', form)
  const selectedScope = Form.useWatch('scope', form)
  const selectedOwner = Form.useWatch('owner', form)
  const tasksQuery = useQuery({ queryKey: ['governance-tasks'], queryFn: () => getGovernanceTasks() })
  const employeesQuery = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 5 * 60_000 })
  const plansQuery = useQuery({
    queryKey: ['governance-plans', detailTask?.id],
    queryFn: () => getGovernancePlans(detailTask?.id ?? 0),
    enabled: detailTask !== null,
  })
  const createMutation = useMutation({
    mutationFn: createGovernanceTask,
    onSuccess: async (created) => {
      await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] })
      setDrawerOpen(false)
      form.resetFields()
      setDetailMode('plan')
      setDetailTask(created)
      setNewPlanDraft(emptyPlanDraft())
      message.success('任务草稿已创建，请先完成计划编排')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '治理任务创建失败'),
  })
  const progressMutation = useMutation({
    mutationFn: (input: { taskId: number; completed: number }) => updateGovernanceProgress(input.taskId, input.completed),
    onSuccess: async (updated) => {
      setDetailTask(updated)
      await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] })
      message.success('任务进度已更新')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '进度更新失败'),
  })
  const planMutation = useMutation({
    mutationFn: (input: { taskId: number; planId: number; status: GovernancePlan['status'] }) => updateGovernancePlan(input.taskId, input.planId, input.status),
    onSuccess: async (_, input) => {
      await queryClient.invalidateQueries({ queryKey: ['governance-plans', input.taskId] })
      message.success('计划状态已更新')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '计划更新失败'),
  })
  const createPlanMutation = useMutation({
    mutationFn: (input: { taskId: number; plan: PlanDraft }) => createGovernancePlan(input.taskId, input.plan),
    onSuccess: async (_, input) => {
      setNewPlanDraft(emptyPlanDraft())
      await queryClient.invalidateQueries({ queryKey: ['governance-plans', input.taskId] })
      message.success('计划项已添加')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '计划项添加失败'),
  })
  const startMutation = useMutation({
    mutationFn: (taskId: number) => startGovernanceTask(taskId),
    onSuccess: async (updated) => {
      setDetailTask(updated)
      setDetailMode('progress')
      await queryClient.invalidateQueries({ queryKey: ['governance-tasks'] })
      message.success('任务已开始执行，计划结构已锁定')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '任务启动失败'),
  })
  const tasks = tasksQuery.data ?? []
  const selectedStageInfo = processOptions.find((item) => item.value === selectedStage)
  const selectedTargetInfo = targetOptions.find((item) => item.value === selectedTarget)
  const selectedEmployee = employeesQuery.data?.find((employee) => employee.id === selectedOwner)
  const total = tasks.reduce((sum, task) => sum + task.total, 0)
  const completed = tasks.reduce((sum, task) => sum + task.completed, 0)
  const metrics = [
    ['治理任务', tasks.length],
    ['待整理项', total - completed],
    ['已处理项', completed],
    ['待确认任务', tasks.filter((task) => task.status === 'PENDING_CONFIRMATION').length],
    ['总体完成率', total ? `${Math.round((completed / total) * 100)}%` : '0%'],
  ]

  const columns: ColumnsType<GovernanceTask> = [
    { title: '治理任务', dataIndex: 'name', render: (value: string, record) => <div><Typography.Text strong>{value}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{record.scope}</Typography.Text></div> },
    { title: '负责人', dataIndex: 'owner', width: 100 },
    { title: '进度', width: 220, render: (_, record) => <Space direction="vertical" size={2} style={{ width: '100%' }}><Progress percent={record.total ? Math.round((record.completed / record.total) * 100) : 0} size="small" strokeColor="#2f7567" /><Typography.Text type="secondary" style={{ fontSize: 11 }}>{record.completed} / {record.total}</Typography.Text></Space> },
    { title: '计划完成', dataIndex: 'dueDate', width: 120 },
    { title: '状态', dataIndex: 'status', width: 110, render: (value: GovernanceTaskStatus) => { const config = statusConfig[value]; return <Tag color={config.color} icon={config.icon}>{config.label}</Tag> } },
    { title: '操作', width: 210, render: (_, record) => <Space size={2}><Tooltip title={record.status === 'DRAFT' ? '完成计划编排并开始执行后可查看进度' : undefined}><span><Button type="link" disabled={record.status === 'DRAFT'} icon={<ClockCircleOutlined />} onClick={() => { setDetailMode('progress'); setDetailTask(record); setProgressDraft(record.completed); setNewPlanDraft(emptyPlanDraft()) }}>查看进度</Button></span></Tooltip><Tooltip title={record.status === 'DRAFT' ? '设置计划、责任人、日期和前置关系' : '任务开始执行后计划结构已锁定'}><span><Button type="link" disabled={record.status !== 'DRAFT'} icon={record.status === 'DRAFT' ? <UnorderedListOutlined /> : <LockOutlined />} onClick={() => { setDetailMode('plan'); setDetailTask(record); setNewPlanDraft(emptyPlanDraft()) }}>{record.status === 'DRAFT' ? '编排计划' : '计划已锁定'}</Button></span></Tooltip></Space> },
  ]

  const openCreateDrawer = () => {
    form.resetFields()
    setDrawerOpen(true)
  }

  const createTask = async () => {
    const values = await form.validateFields()
    const stage = processOptions.find((item) => item.value === values.stage)
    const target = targetOptions.find((item) => item.value === values.target)
    const dueDate = typeof values.dueDate === 'string' ? values.dueDate : values.dueDate?.format('YYYY-MM-DD') ?? ''
    const scopeText = values.scope.trim()
    const scope = `${stage?.label ?? values.stage} · ${target?.shortLabel ?? values.target}${scopeText ? `：${scopeText}` : ''}`
    const name = values.name.trim() || `${target?.shortLabel ?? '资料'}${stage?.label ?? '整理'}任务`
    const owner = employeesQuery.data?.find((employee) => employee.id === values.owner)?.name ?? values.owner
    createMutation.mutate({
      name,
      scope,
      owner,
      assigneeId: values.owner,
      total: values.total,
      dueDate,
    })
  }

  const detailPlans = plansQuery.data ?? []
  const canStartTask = detailPlans.length > 0 && detailPlans.every((plan) => plan.assigneeId && plan.plannedStart && plan.plannedEnd && plan.plannedQuantity > 0)
  const draftPreview: GovernancePlan | null = newPlanDraft.title.trim() || newPlanDraft.plannedStart || newPlanDraft.plannedEnd ? {
    ...newPlanDraft,
    id: -1,
    taskId: detailTask?.id ?? 0,
    title: newPlanDraft.title.trim() || '新计划预览',
    status: 'TODO',
    completedQuantity: 0,
  } : null
  const visualPlans = draftPreview ? [...detailPlans, draftPreview] : detailPlans
  const detailTimeline = getPlanTimeline(visualPlans)
  const employeeName = (employeeId?: string) => employeesQuery.data?.find((employee) => employee.id === employeeId)?.name ?? '未分派'
  const createPlan = () => {
    if (!detailTask || !newPlanDraft.title.trim()) return
    createPlanMutation.mutate({ taskId: detailTask.id, plan: { ...newPlanDraft, title: newPlanDraft.title.trim() } })
  }
  const swimlaneGroups = visualPlans.reduce<Record<string, GovernancePlan[]>>((groups, plan) => {
    const key = plan.assigneeId ?? 'unassigned'
    groups[key] = [...(groups[key] ?? []), plan]
    return groups
  }, {})
  const planColumns: ColumnsType<GovernancePlan> = [
    { title: '计划项', dataIndex: 'title', render: (value: string) => <Typography.Text strong style={{ fontSize: 12 }}>{value}</Typography.Text> },
    { title: '责任人', dataIndex: 'assigneeId', width: 130, render: (value?: string) => <Space size={5}><TeamOutlined style={{ color: '#2f7567' }} /><span>{employeeName(value)}</span></Space> },
    { title: '计划时间', width: 180, render: (_, plan) => plan.plannedStart && plan.plannedEnd ? <Space size={5}><CalendarOutlined style={{ color: '#81928a' }} /><span>{plan.plannedStart} 至 {plan.plannedEnd}</span></Space> : <Typography.Text type="secondary">待排期</Typography.Text> },
    { title: '计划量', width: 100, render: (_, plan) => `${plan.completedQuantity} / ${plan.plannedQuantity} ${plan.quantityUnit}` },
    { title: '当前状态', dataIndex: 'status', width: 105, render: (value: GovernancePlan['status']) => <Tag color={planStatusConfig[value].color}>{planStatusConfig[value].label}</Tag> },
  ]
  const progressPlanColumns: ColumnsType<GovernancePlan> = [
    { title: '执行计划', dataIndex: 'title', render: (value: string) => <Typography.Text strong style={{ fontSize: 12 }}>{value}</Typography.Text> },
    { title: '责任人', dataIndex: 'assigneeId', width: 120, render: (value?: string) => <Space size={5}><TeamOutlined style={{ color: '#2f7567' }} /><span>{employeeName(value)}</span></Space> },
    { title: '计划时间', width: 180, render: (_, plan) => plan.plannedStart && plan.plannedEnd ? `${plan.plannedStart} 至 ${plan.plannedEnd}` : <Typography.Text type="secondary">待排期</Typography.Text> },
    { title: '进度', width: 120, render: (_, plan) => `${plan.completedQuantity} / ${plan.plannedQuantity} ${plan.quantityUnit}` },
    { title: detailTask?.status === 'IN_PROGRESS' ? '更新状态' : '状态', dataIndex: 'status', width: 112, render: (value: GovernancePlan['status'], plan) => detailTask?.status === 'IN_PROGRESS' ? <Select size="small" value={value} options={Object.entries(planStatusConfig).map(([status, item]) => ({ value: status, label: item.label }))} onChange={(next) => planMutation.mutate({ taskId: detailTask.id, planId: plan.id, status: next as GovernancePlan['status'] })} style={{ width: 92 }} /> : <Tag color={planStatusConfig[value].color}>{planStatusConfig[value].label}</Tag> },
  ]

  return (
    <>
      <Header>
        <div><Title>数据治理中心</Title><Typography.Text type="secondary">盘点、标准、映射、清洗、确认和质量验收</Typography.Text></div>
        <Space><Button icon={<DatabaseOutlined />} onClick={() => navigate('/dictionaries')}>查看标准字典</Button><Button type="primary" icon={<PlusOutlined />} onClick={openCreateDrawer}>新建治理任务</Button></Space>
      </Header>
      <Intro
        type="info"
        showIcon
        icon={<InfoCircleOutlined />}
        message="治理任务是一次可分派、可追踪、可验收的数据整理工作"
        description="新建任务先保存为草稿；完成计划、责任人和排期后再开始执行。执行开始后计划结构锁定，只更新状态和完成量。"
      />
      <Metrics>{metrics.map(([label, value]) => <Metric key={label}><MetricLabel>{label}</MetricLabel><MetricValue>{value}</MetricValue></Metric>)}</Metrics>
      <TableSurface><Table rowKey="id" size="small" columns={columns} dataSource={tasks} loading={tasksQuery.isLoading} pagination={false} locale={{ emptyText: '暂无治理任务' }} /></TableSurface>
      <Drawer
        title="新建治理任务"
        width={560}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        destroyOnHidden
        footer={
          <Space style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={createMutation.isPending} onClick={() => void createTask()}>创建治理任务</Button>
          </Space>
        }
      >
        <DrawerIntro>这是一次可分派、可追踪的资料整理工作。只要说清楚“做什么、整理哪类内容、处理哪些资料、谁负责、何时完成”，系统就能生成任务。</DrawerIntro>
        <Form form={form} layout="vertical" scrollToFirstError>
          <FormGroup>
            <FormGroupTitle>1. 这次要做什么</FormGroupTitle>
            <Form.Item name="name" label="工作名称（可选）" extra="不填写时，系统会根据下面的选择自动生成名称。">
              <Input placeholder="例如：历史模组适用范围补充" />
            </Form.Item>
            <Form.Item name="stage" label="治理动作" extra={selectedStageInfo?.help ?? '选择最接近本次工作的动作，不需要记住治理流程术语。'} rules={[{ required: true, message: '请选择要做的动作' }]}>
              <Select
                placeholder="例如：清洗历史资料"
                options={processOptions.map(({ value, label, help }) => ({ value, label, help }))}
                optionRender={(option) => <div><OptionTitle>{option.data.label}</OptionTitle><OptionHelp>{option.data.help}</OptionHelp></div>}
              />
            </Form.Item>
            <Form.Item name="target" label="整理内容" extra={selectedTargetInfo?.label ?? '选择本次要整理的资料字段或关联关系。'} rules={[{ required: true, message: '请选择整理内容' }]}>
              <Select
                placeholder="例如：平台、基地、拉线和工序段"
                options={targetOptions.map(({ value, shortLabel, label }) => ({ value, label: shortLabel, help: label }))}
                optionRender={(option) => <div><OptionTitle>{option.data.label}</OptionTitle><OptionHelp>{option.data.help}</OptionHelp></div>}
              />
            </Form.Item>
            <Form.Item name="scope" label="处理边界" extra="写清平台、基地、拉线、时间段或筛选条件，负责人才能知道从哪里开始、做到哪里结束。" rules={[{ required: true, whitespace: true, message: '请填写处理边界' }]}>
              <Input.TextArea placeholder="例如：历史模组资产；乘用车-大面水冷；A 拉线；2025 年以前入库资料" autoSize={{ minRows: 3, maxRows: 5 }} />
            </Form.Item>
          </FormGroup>

          <FormGroup>
            <FormGroupTitle>2. 指定负责人和时间</FormGroupTitle>
            <Form.Item name="owner" label="负责人" extra={selectedEmployee ? `已绑定办公软件员工身份：${selectedEmployee.department}` : '选择员工目录中的人员，避免同名或手工填写造成任务无法追踪。'} rules={[{ required: true, message: '请选择负责人' }]}>
              <Select
                showSearch
                placeholder="选择员工"
                optionFilterProp="label"
                options={(employeesQuery.data ?? []).map((employee) => ({ value: employee.id, label: employee.name, department: employee.department }))}
                optionRender={(option) => <div><OptionTitle>{option.data.label}</OptionTitle><OptionHelp>{option.data.department} · 办公软件员工目录</OptionHelp></div>}
              />
            </Form.Item>
            <Space size={12} style={{ display: 'flex' }} align="start">
              <Form.Item name="total" label="预计处理量" extra="按资产、字段或关系估算，后续可按实际进度调整。" rules={[{ required: true, type: 'number', min: 1, message: '请输入大于 0 的数量' }]} style={{ flex: 1 }}>
                <InputNumber min={1} precision={0} style={{ width: '100%' }} placeholder="例如：286" />
              </Form.Item>
              <Form.Item name="dueDate" label="计划完成日期" rules={[{ required: true, message: '请选择完成日期' }]} style={{ flex: 1 }}>
                <Input type="date" />
              </Form.Item>
            </Space>
          </FormGroup>

          <TaskPreview>
            <Typography.Text strong>创建后将生成：</Typography.Text>
            <br />动作：{selectedStageInfo?.label ?? '待选择'} · 内容：{selectedTargetInfo?.shortLabel ?? '待选择'}
            <br />范围：{selectedScope || '待填写处理边界'}
            <br /><Typography.Text type="secondary">任务创建后先保存为草稿；完成计划编排并确认无误后，再由负责人启动执行。</Typography.Text>
          </TaskPreview>
        </Form>
      </Drawer>
      <Drawer
        title={detailMode === 'plan' ? '任务计划编排' : '任务执行进度'}
        width="min(1120px, calc(100vw - 32px))"
        open={detailTask !== null}
        onClose={() => setDetailTask(null)}
        destroyOnHidden
      >
        {detailTask && (
          <>
            <DetailSummary>
              <DetailTitle>{detailTask.name}</DetailTitle>
              <DetailMeta>{detailTask.scope}</DetailMeta>
              <Space size={8} style={{ marginTop: 8 }}>
                <Tag color={statusConfig[detailTask.status].color}>{statusConfig[detailTask.status].label}</Tag>
                <Typography.Text type="secondary">任务总负责人：{detailTask.owner}</Typography.Text>
                {detailMode === 'plan' ? <Tooltip title={canStartTask ? '开始后计划结构将锁定' : '至少添加一项计划，并补齐责任人、起止日期和数量'}><span><Button size="small" type="primary" icon={<PlayCircleOutlined />} disabled={!canStartTask} loading={startMutation.isPending} onClick={() => startMutation.mutate(detailTask.id)}>开始执行</Button></span></Tooltip> : <Tooltip title={detailTask.status === 'DRAFT' ? '返回计划编排' : '执行开始后计划结构已锁定'}><span><Button size="small" disabled={detailTask.status !== 'DRAFT'} icon={detailTask.status === 'DRAFT' ? <UnorderedListOutlined /> : <LockOutlined />} onClick={() => { setDetailMode('plan'); setNewPlanDraft(emptyPlanDraft()) }}>{detailTask.status === 'DRAFT' ? '编排任务计划' : '计划已锁定'}</Button></span></Tooltip>}
              </Space>
            </DetailSummary>

            {detailMode === 'plan' && <WorkflowGuide>
              <WorkflowStep $active><WorkflowStepTop><WorkflowStepNumber>1</WorkflowStepNumber>查看计划清单</WorkflowStepTop><WorkflowStepHelp>先确认每个阶段要做什么、处理多少资料。</WorkflowStepHelp></WorkflowStep>
              <WorkflowStep><WorkflowStepTop><WorkflowStepNumber>2</WorkflowStepNumber>添加并分配</WorkflowStepTop><WorkflowStepHelp>在清单下方添加计划，并选择具体责任人。</WorkflowStepHelp></WorkflowStep>
              <WorkflowStep><WorkflowStepTop><WorkflowStepNumber>3</WorkflowStepNumber>查看时间轴</WorkflowStepTop><WorkflowStepHelp>填写起止日期后，甘特图会自动生成并实时预览。</WorkflowStepHelp></WorkflowStep>
              <WorkflowStep><WorkflowStepTop><WorkflowStepNumber>4</WorkflowStepNumber>确认编排结果</WorkflowStepTop><WorkflowStepHelp>核对责任人、日期、数量和前置关系后完成编排。</WorkflowStepHelp></WorkflowStep>
            </WorkflowGuide>}

            {detailMode === 'plan' && <Alert type="warning" showIcon message="开始执行后将锁定计划结构" description="锁定后不能直接新增计划或调整责任关系，只能更新执行状态和完成量。需要调整时应走计划变更流程并记录原因。" />}

            {detailMode === 'progress' && <>
              <Alert type="info" showIcon message="这里用于查看和更新执行结果" description="计划结构、责任人和前置关系请进入“编排任务计划”维护；当前页面只关注完成情况、时间安排和人员负荷。" />
              <ProgressSummaryGrid>
                <ProgressSummaryItem><ProgressSummaryLabel>计划项</ProgressSummaryLabel><ProgressSummaryValue>{detailPlans.length}</ProgressSummaryValue></ProgressSummaryItem>
                <ProgressSummaryItem><ProgressSummaryLabel>已完成计划</ProgressSummaryLabel><ProgressSummaryValue>{detailPlans.filter((plan) => plan.status === 'DONE').length}</ProgressSummaryValue></ProgressSummaryItem>
                <ProgressSummaryItem><ProgressSummaryLabel>任务完成率</ProgressSummaryLabel><ProgressSummaryValue>{detailTask.total ? Math.round((detailTask.completed / detailTask.total) * 100) : 0}%</ProgressSummaryValue></ProgressSummaryItem>
              </ProgressSummaryGrid>
            </>}

            <PlanSection>
              <PlanSectionHeader>
                <div><PlanSectionTitle>{detailMode === 'plan' ? '计划清单与责任分配' : '执行计划概览'}</PlanSectionTitle><PlanSectionHint>{detailMode === 'plan' ? '在这里维护工作拆分、计划责任人、日期、数量和执行顺序。' : '只读查看当前计划结构；计划调整请进入计划编排。'}</PlanSectionHint></div>
                <Tag color="blue">{detailPlans.filter((plan) => plan.status === 'DONE').length} / {detailPlans.length} 已完成</Tag>
              </PlanSectionHeader>
              <Table<GovernancePlan> rowKey="id" size="small" columns={detailMode === 'plan' ? planColumns : progressPlanColumns} dataSource={detailPlans} pagination={false} locale={{ emptyText: detailMode === 'plan' ? '还没有计划项，请在下方添加第一项' : '该任务还没有编排执行计划' }} />

              {detailMode === 'plan' && <PlanForm>
                <PlanFormTitle><PlusOutlined />新增计划项</PlanFormTitle>
                <OwnerHint>责任人从员工目录中选择，和任务总负责人可以是同一个人，也可以分别指定。</OwnerHint>
                <PlanFormGrid>
                  <PlanField><PlanFieldLabel>计划名称</PlanFieldLabel><Input value={newPlanDraft.title} onChange={(event) => setNewPlanDraft((current) => ({ ...current, title: event.target.value }))} placeholder="例如：提交业务专家确认" /></PlanField>
                  <PlanField><PlanFieldLabel>计划责任人</PlanFieldLabel><Select value={newPlanDraft.assigneeId} allowClear placeholder="选择执行人" onChange={(value) => setNewPlanDraft((current) => ({ ...current, assigneeId: value }))} options={(employeesQuery.data ?? []).map((employee) => ({ value: employee.id, label: employee.name, title: employee.department }))} optionRender={(option) => <div><OptionTitle>{option.data.label}</OptionTitle><OptionHelp>{option.data.title}</OptionHelp></div>} style={{ width: '100%' }} /></PlanField>
                  <PlanField><PlanFieldLabel>计划开始</PlanFieldLabel><Input type="date" value={newPlanDraft.plannedStart ?? ''} onChange={(event) => setNewPlanDraft((current) => ({ ...current, plannedStart: event.target.value || undefined }))} /></PlanField>
                  <PlanField><PlanFieldLabel>计划结束</PlanFieldLabel><Input type="date" value={newPlanDraft.plannedEnd ?? ''} onChange={(event) => setNewPlanDraft((current) => ({ ...current, plannedEnd: event.target.value || undefined }))} /></PlanField>
                </PlanFormGrid>
                <PlanFormGrid>
                  <PlanField><PlanFieldLabel>计划数量</PlanFieldLabel><InputNumber min={1} precision={0} value={newPlanDraft.plannedQuantity} onChange={(value) => setNewPlanDraft((current) => ({ ...current, plannedQuantity: value ?? 1 }))} style={{ width: '100%' }} placeholder="例如：286" /></PlanField>
                  <PlanField><PlanFieldLabel>数量单位</PlanFieldLabel><Select value={newPlanDraft.quantityUnit} onChange={(value) => setNewPlanDraft((current) => ({ ...current, quantityUnit: value }))} options={['个资产', '个字段', '个关系', '个文件', '项'].map((value) => ({ value, label: value }))} style={{ width: '100%' }} /></PlanField>
                  <PlanField><PlanFieldLabel>前置计划（可选）</PlanFieldLabel><Select mode="multiple" value={newPlanDraft.dependencyIds.map(String)} allowClear placeholder="没有可留空" maxTagCount="responsive" onChange={(values) => setNewPlanDraft((current) => ({ ...current, dependencyIds: values.map(Number) }))} options={detailPlans.map((plan) => ({ value: String(plan.id), label: plan.title }))} style={{ width: '100%' }} /></PlanField>
                  <PlanFormActions><Button type="primary" icon={<PlusOutlined />} loading={createPlanMutation.isPending} disabled={!newPlanDraft.title.trim()} onClick={createPlan}>添加到计划清单</Button></PlanFormActions>
                </PlanFormGrid>
              </PlanForm>}
            </PlanSection>

            <TimelineSection>
              <PlanSectionHeader><div><PlanSectionTitle>{detailMode === 'plan' ? '排期预览' : '执行时间轴'}</PlanSectionTitle><PlanSectionHint>{detailMode === 'plan' ? '用于检查计划先后关系；新计划填写日期后会以草稿立即出现。' : '横向查看计划开始、结束和当前执行状态。'}</PlanSectionHint></div><CalendarOutlined style={{ color: '#2f7567', fontSize: 18 }} /></PlanSectionHeader>
              {detailTimeline ? <Gantt>
                <GanttHeader><div>计划项 / 责任人</div><GanttScale>{detailTimeline.scale.map((date) => <div key={date}>{formatShortDate(date)}</div>)}</GanttScale></GanttHeader>
                {visualPlans.map((plan) => { const bar = getPlanBar(plan, detailTimeline); const isDraft = plan.id < 0; return <GanttRow key={plan.id}><GanttLabel><Typography.Text strong style={{ fontSize: 12 }}>{plan.title}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 11 }}>{employeeName(plan.assigneeId)} · {plan.plannedStart && plan.plannedEnd ? `${formatShortDate(plan.plannedStart)} - ${formatShortDate(plan.plannedEnd)}` : '待排期'}</Typography.Text>{isDraft ? <Tag style={{ marginTop: 5 }}>草稿预览</Tag> : detailMode === 'progress' && detailTask.status === 'IN_PROGRESS' ? <Select size="small" value={plan.status} options={Object.entries(planStatusConfig).map(([value, item]) => ({ value, label: item.label }))} onChange={(value) => planMutation.mutate({ taskId: detailTask.id, planId: plan.id, status: value as GovernancePlan['status'] })} style={{ width: 90, marginTop: 5 }} /> : <Tag color={planStatusConfig[plan.status].color} style={{ marginTop: 5 }}>{planStatusConfig[plan.status].label}</Tag>}</GanttLabel><GanttTrack><GanttBar title={`${plan.title} · ${plan.plannedStart ?? '未排期'} - ${plan.plannedEnd ?? '未排期'}`} $left={bar.left} $width={bar.width} $status={plan.status} /></GanttTrack></GanttRow> })}
              </Gantt> : <Typography.Text type="secondary">还没有可排期的计划项，请先在上方填写计划开始和结束日期。</Typography.Text>}
            </TimelineSection>

            <PlanSection>
              <PlanSectionHeader><div><PlanSectionTitle>{detailMode === 'plan' ? '责任分布预览' : '责任分布'}</PlanSectionTitle><PlanSectionHint>按负责人归集计划，快速看出每个人承担的工作以及交接关系；人员在计划编排中设置。</PlanSectionHint></div><TeamOutlined style={{ color: '#2f7567', fontSize: 18 }} /></PlanSectionHeader>
              <Swimlane>{Object.entries(swimlaneGroups).map(([employeeId, plans]) => <SwimlaneLane key={employeeId}><SwimlaneOwner>{employeeName(employeeId)}<Typography.Text type="secondary" style={{ display: 'block', marginTop: 3, fontSize: 11 }}>{plans.length} 项计划</Typography.Text></SwimlaneOwner><SwimlaneItems>{plans.map((plan) => <SwimlaneItem key={plan.id}><Typography.Text strong>{plan.title}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 11 }}>{plan.status === 'DONE' ? '已完成' : plan.plannedStart && plan.plannedEnd ? `${formatShortDate(plan.plannedStart)} - ${formatShortDate(plan.plannedEnd)}` : '待排期'}</Typography.Text></SwimlaneItem>)}</SwimlaneItems></SwimlaneLane>)}</Swimlane>
            </PlanSection>

            {detailMode === 'progress' && <>
              <DetailSectionTitle><span>任务完成进度</span><Typography.Text type="secondary">按任务总量更新</Typography.Text></DetailSectionTitle>
              <Progress percent={detailTask.total ? Math.round((detailTask.completed / detailTask.total) * 100) : 0} strokeColor="#2f7567" />
              <ProgressEditor>
                <Form.Item label="已完成数量" style={{ flex: 1, marginBottom: 0 }}>
                  <InputNumber disabled={detailTask.status !== 'IN_PROGRESS'} min={0} max={detailTask.total} precision={0} value={progressDraft} onChange={(value) => setProgressDraft(value ?? 0)} style={{ width: '100%' }} />
                </Form.Item>
                <Typography.Text type="secondary" style={{ paddingBottom: 7 }}>/ {detailTask.total}</Typography.Text>
                <Button type="primary" disabled={detailTask.status !== 'IN_PROGRESS'} loading={progressMutation.isPending} onClick={() => progressMutation.mutate({ taskId: detailTask.id, completed: progressDraft })}>更新进度</Button>
              </ProgressEditor>
              <Typography.Paragraph type="secondary" style={{ marginTop: 10, fontSize: 12 }}>
                完成数量达到预计处理量后，任务会进入“待确认”，由业务专家确认结果；确认通过后再进入验收和归档。
              </Typography.Paragraph>
            </>}
          </>
        )}
      </Drawer>
    </>
  )
}
