import {
  ArrowRightOutlined,
  AppstoreOutlined,
  CheckCircleFilled,
  ClockCircleOutlined,
  ExclamationCircleFilled,
  FileDoneOutlined,
  PlusOutlined,
  ReloadOutlined,
  RightOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Dropdown, Empty, Skeleton } from 'antd'
import { useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { authSession } from '../../auth/session'
import { getGovernanceIssues, getGovernanceScanRuns, getGovernanceTasks, getInventory } from '../api'
import type { GovernanceIssue, GovernanceTask, GovernanceTaskStatus } from '../types'
import type { GovernanceStatCardData } from './StatCards'
import { StatCards } from './StatCards'
import {
  buildGovernanceRailModel,
  classifyGovernanceTaskStage,
  dueDayDiff,
  emptyGovernanceRailCounts,
  isTaskEscalated,
  summarizeGovernanceDue,
  summarizeGovernanceIssuesForRail,
  summarizeGovernanceTasksForRail,
} from './governanceRailModel'
import type { GovernanceRailStageKey } from './governanceRailModel'
import { railTheme } from './railTheme'

const Page = styled.section`
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 18px;
  padding-bottom: 28px;
`

const Hero = styled.header`
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  min-height: 96px;
  padding: 22px 24px;
  overflow: hidden;
  background: #fff;
  border: 1px solid ${railTheme.line};
  border-radius: 8px;

  &::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 4px;
    background: ${railTheme.brand};
  }

  @media (max-width: 720px) {
    align-items: flex-start;
    flex-direction: column;
  }
`

const HeroCopy = styled.div`
  min-width: 0;

  h1 {
    margin: 0;
    color: ${railTheme.text};
    font-size: 24px;
    font-weight: 700;
    line-height: 1.25;
  }

  p {
    margin: 7px 0 0;
    color: ${railTheme.text2};
    font-size: 13px;
    line-height: 1.6;
  }
`

const Identity = styled.div`
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;
  padding: 8px 10px 8px 8px;
  background: #f7f9f8;
  border: 1px solid #e6ebe8;
  border-radius: 8px;
`

const HeroActions = styled.div`
  display: flex;
  flex: none;
  align-items: center;
  gap: 10px;

  @media (max-width: 560px) {
    width: 100%;
    align-items: stretch;
    flex-direction: column-reverse;
  }
`

const Avatar = styled.span`
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  background: ${railTheme.brand};
  border-radius: 6px;
`

const IdentityText = styled.span`
  display: flex;
  flex-direction: column;

  strong { color: ${railTheme.text}; font-size: 13px; font-weight: 650; }
  small { color: ${railTheme.text3}; font-size: 11px; }
`

const WorkbenchHeading = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  h2 { margin: 0; color: ${railTheme.text}; font-size: 18px; font-weight: 700; }
  p { margin: 4px 0 0; color: ${railTheme.text3}; font-size: 12px; }
`

const WorkGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;

  @media (max-width: 980px) { grid-template-columns: 1fr; }
`

const WorkCard = styled.article<{ $tone: 'dispatch' | 'task' }>`
  display: flex;
  min-width: 0;
  min-height: 292px;
  flex-direction: column;
  background: #fff;
  border: 1px solid ${props => props.$tone === 'dispatch' ? '#e4e8e6' : '#e2e7ed'};
  border-radius: 8px;
  box-shadow: 0 8px 24px -20px rgba(22, 34, 29, 0.35);
`

const WorkCardHeader = styled.header`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 18px 14px;
  border-bottom: 1px solid #eef1ef;
`

const WorkCardTitle = styled.div`
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 11px;

  .icon {
    display: grid;
    width: 38px;
    height: 38px;
    flex: none;
    place-items: center;
    color: ${railTheme.brand};
    font-size: 17px;
    background: ${railTheme.brandWeak};
    border-radius: 7px;
  }

  h3 { margin: 0; color: ${railTheme.text}; font-size: 16px; font-weight: 680; }
  p { margin: 3px 0 0; color: ${railTheme.text3}; font-size: 12px; }
`

const CountBadge = styled.span<{ $alert?: boolean }>`
  display: inline-flex;
  flex: none;
  align-items: baseline;
  gap: 3px;
  color: ${props => props.$alert ? railTheme.red : railTheme.text};
  font-variant-numeric: tabular-nums;

  strong { font-size: 25px; font-weight: 720; line-height: 1; }
  small { color: ${railTheme.text3}; font-size: 11px; }
`

const Queue = styled.div`
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 7px 10px 4px;
`

const QueueRow = styled.button`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 16px;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 58px;
  padding: 10px 8px;
  color: inherit;
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-bottom: 1px solid #f0f2f1;
  cursor: pointer;

  &:last-child { border-bottom: 0; }
  &:hover { background: #f7faf8; }
  &:focus-visible { outline: 2px solid ${railTheme.brand}; outline-offset: -2px; }
`

const RowMain = styled.span`
  min-width: 0;
  strong {
    display: block;
    overflow: hidden;
    color: ${railTheme.text};
    font-size: 13px;
    font-weight: 600;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  small {
    display: block;
    margin-top: 3px;
    overflow: hidden;
    color: ${railTheme.text3};
    font-size: 11.5px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`

const RowMeta = styled.span<{ $danger?: boolean }>`
  color: ${props => props.$danger ? railTheme.red : railTheme.text2};
  font-size: 11.5px;
  font-weight: ${props => props.$danger ? 600 : 400};
  white-space: nowrap;
`

const CardFooter = styled.footer`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 18px 14px;
  border-top: 1px solid #eef1ef;

  span { color: ${railTheme.text3}; font-size: 11.5px; }
`

const ProgressPanel = styled.section`
  padding: 17px 18px 18px;
  background: #fff;
  border: 1px solid ${railTheme.line};
  border-radius: 8px;
`

const SectionHeader = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 15px;

  h2 { margin: 0; color: ${railTheme.text}; font-size: 15px; font-weight: 680; }
  p { margin: 3px 0 0; color: ${railTheme.text3}; font-size: 11.5px; }
`

const ProgressTrack = styled.ol`
  display: grid;
  grid-template-columns: repeat(6, minmax(100px, 1fr));
  gap: 0;
  min-width: 660px;
  margin: 0;
  padding: 0;
  list-style: none;
`

const ProgressScroll = styled.div`
  overflow-x: auto;
  scrollbar-width: thin;
`

const Stage = styled.li<{ $state: 'done' | 'current' | 'overdue' | 'wait' }>`
  position: relative;
  min-width: 0;
  padding-right: 13px;

  &::after {
    content: '';
    position: absolute;
    top: 13px;
    left: 32px;
    right: 4px;
    height: 1px;
    background: ${props => props.$state === 'done' ? '#9ac8b8' : '#e3e7e5'};
  }
`

const StageButton = styled.button`
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  gap: 9px;
  align-items: start;
  width: 100%;
  min-width: 0;
  margin: -5px 0;
  padding: 5px 4px 5px 0;
  color: inherit;
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background 150ms ease, transform 150ms ease;

  &:hover {
    background: #f4f8f6;
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 2px solid ${railTheme.brand};
    outline-offset: 2px;
  }
`

const StageDot = styled.span<{ $state: 'done' | 'current' | 'overdue' | 'wait' }>`
  position: relative;
  z-index: 1;
  display: grid;
  width: 28px;
  height: 28px;
  place-items: center;
  color: ${props => props.$state === 'done' ? '#fff' : props.$state === 'overdue' ? railTheme.red : props.$state === 'current' ? railTheme.brand : railTheme.text3};
  font-size: 11px;
  font-weight: 700;
  background: ${props => props.$state === 'done' ? railTheme.green : props.$state === 'overdue' ? railTheme.redWeak : props.$state === 'current' ? railTheme.brandWeak : '#f1f3f2'};
  border: 1px solid ${props => props.$state === 'overdue' ? '#efb9b9' : props.$state === 'current' ? '#aad0c5' : 'transparent'};
  border-radius: 50%;
`

const StageText = styled.span`
  min-width: 0;
  strong { display: block; overflow: hidden; color: ${railTheme.text}; font-size: 12px; font-weight: 620; text-overflow: ellipsis; white-space: nowrap; }
  small { display: block; margin-top: 2px; overflow: hidden; color: ${railTheme.text3}; font-size: 10.5px; text-overflow: ellipsis; white-space: nowrap; }
`

const tasksTitleStatus: Record<GovernanceTaskStatus, string> = {
  DRAFT: '待启动',
  IN_PROGRESS: '整改中',
  PENDING_CONFIRMATION: '待确认',
  PENDING_ACCEPTANCE: '待验收',
  REWORK_REQUIRED: '需返工',
  COMPLETED: '已完成',
}

const fieldLabels: Record<string, string> = {
  DESCRIPTION: '功能说明',
  SPECIALTIES: '专业类别',
  OWNER: '责任人',
  SCOPE: '适用范围',
}

function formatMonthDay(value: string | null | undefined): string {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return '未设置截止日'
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function issueLabel(issue: GovernanceIssue): string {
  return `资产 #${issue.assetId} · ${fieldLabels[issue.targetField] ?? issue.targetField}`
}

function taskPriority(task: GovernanceTask, today: Date): number {
  const diff = dueDayDiff(task.dueDate, today)
  const statusWeight: Record<GovernanceTaskStatus, number> = {
    REWORK_REQUIRED: 60,
    PENDING_ACCEPTANCE: 50,
    PENDING_CONFIRMATION: 40,
    IN_PROGRESS: 30,
    DRAFT: 20,
    COMPLETED: 0,
  }
  return (diff !== null && diff < 0 ? 100 : 0) + statusWeight[task.status] - (diff ?? 999) / 1000
}

interface GovernanceRailHomeProps {
  onOpenTask?: (taskId: number) => void
}

export function GovernanceRailHome({ onOpenTask }: GovernanceRailHomeProps) {
  const navigate = useNavigate()
  const user = authSession.get()
  const isAdministrator = user?.roles.some(role => role === 'CONTENT_ADMIN' || role === 'SYSTEM_ADMIN') ?? true
  const inventoryQuery = useQuery({ queryKey: ['rail-inventory'], queryFn: () => getInventory({ page: 1, perPage: 1 }), staleTime: 60_000 })
  const issuesQuery = useQuery({ queryKey: ['rail-issues'], queryFn: () => getGovernanceIssues(), staleTime: 60_000 })
  const tasksQuery = useQuery({ queryKey: ['governance-tasks'], queryFn: () => getGovernanceTasks(), staleTime: 60_000 })
  const scansQuery = useQuery({ queryKey: ['rail-scans'], queryFn: getGovernanceScanRuns, staleTime: 60_000 })

  const model = useMemo(() => {
    const today = new Date()
    const tasks = tasksQuery.data ?? []
    const issues = issuesQuery.data ?? []
    const scans = scansQuery.data ?? []
    const totals = inventoryQuery.data?.totals
    const taskSummary = summarizeGovernanceTasksForRail(tasks, today)
    const issueSummary = summarizeGovernanceIssuesForRail(issues)
    const dueSummary = summarizeGovernanceDue(tasks, today)
    const pendingIssues = issues.filter(issue => issue.status !== 'RESOLVED' && issue.taskId === null)
    const openTasks = tasks
      .filter(task => task.status !== 'COMPLETED')
      .sort((a, b) => taskPriority(b, today) - taskPriority(a, today))
    const myTasks = user && !isAdministrator
      ? openTasks.filter(task => task.assigneeId === user.userId || task.owner === user.name)
      : openTasks
    const scannedAssetCount = totals && totals.total > 0
      ? totals.total
      : scans.filter(run => run.status === 'SUCCEEDED').reduce((sum, run) => sum + run.scannedAssetCount, 0)
    const counts = {
      ...emptyGovernanceRailCounts(),
      scanRunCount: scans.length,
      scanSucceeded: scans.some(run => run.status === 'SUCCEEDED'),
      latestScanFailed: scans[0]?.status === 'FAILED',
      scannedAssetCount,
      inventoryTotal: totals?.total ?? 0,
      poolOpenCount: issueSummary.poolOpenCount,
      issueTotalEver: issueSummary.issueTotalEver,
      assign: taskSummary.assign,
      confirm: taskSummary.confirm,
      accept: taskSummary.accept,
      completedTaskCount: tasks.filter(task => task.status === 'COMPLETED').length,
      standardizedAssets: totals?.standardized ?? 0,
      taskTotalCount: tasks.length,
      applyOwners: taskSummary.applyOwners,
    }
    return {
      today,
      pendingIssues,
      openTasks,
      focusTasks: myTasks,
      dueSummary,
      nodes: buildGovernanceRailModel(counts),
      escalatedCount: tasks.filter(task => isTaskEscalated(task, today)).length,
      totals,
      rates: inventoryQuery.data?.rates,
    }
  }, [inventoryQuery.data, issuesQuery.data, tasksQuery.data, scansQuery.data, isAdministrator, user])

  const statCards: GovernanceStatCardData[] = [
    { key: 'pending', label: '待整理资产', value: model.totals?.pendingCuration ?? null, unit: '条', footnote: `疑似重复 ${model.totals?.duplicateSuspects ?? 0} 条` },
    { key: 'week', label: '7 天内到期', value: tasksQuery.isError ? null : model.dueSummary.dueWithin7Days, unit: '个', tone: model.dueSummary.dueWithin7Days > 0 ? 'warn' : 'default', footnote: `${model.dueSummary.dueWithin48Hours} 个将在 48 小时内到期` },
    { key: 'overdue', label: '已逾期', value: tasksQuery.isError ? null : model.dueSummary.overdue, unit: '个', tone: 'alert', footnote: model.escalatedCount > 0 ? `${model.escalatedCount} 个已升级，请优先跟进` : '暂无升级任务' },
    { key: 'standardized', label: '已标准化', value: model.totals?.standardized ?? null, unit: '条', tone: 'success', footnote: model.rates ? `适用范围覆盖率 ${Math.round(model.rates.scopeCoverage)}%` : '' },
  ]

  const failedSources = [
    inventoryQuery.isError && '资产盘点',
    issuesQuery.isError && '问题池',
    tasksQuery.isError && '治理任务',
    scansQuery.isError && '扫描运行',
  ].filter((item): item is string => Boolean(item))
  const retryAll = () => {
    for (const query of [inventoryQuery, issuesQuery, tasksQuery, scansQuery]) {
      if (query.isError) void query.refetch()
    }
  }
  const queueLoading = issuesQuery.isLoading || tasksQuery.isLoading
  const toolItems = [
    { key: '/sys/drawing/inventory', label: '资产盘点' },
    { key: '/sys/drawing/scans', label: '自动扫描' },
    { key: '/sys/drawing/standards', label: '标准中心' },
    { key: '/sys/drawing/mappings', label: '映射规则' },
    { key: '/sys/drawing/operations', label: '治理运营' },
  ]
  const openStage = (key: GovernanceRailStageKey) => {
    if (key === 'scan') {
      navigate('/sys/drawing/scans')
      return
    }
    if (key === 'issuePool') {
      navigate('/sys/drawing/issues')
      return
    }
    if (key === 'apply') {
      navigate('/sys/drawing/inventory')
      return
    }
    const stageTasks = model.openTasks.filter(task => classifyGovernanceTaskStage(task) === key)
    if (stageTasks.length === 1 && onOpenTask) {
      onOpenTask(stageTasks[0].id)
      return
    }
    navigate('/sys/drawing/operations')
  }

  return (
    <Page>
      <Hero>
        <HeroCopy>
          <h1>数据治理工作台</h1>
          <p>聚焦今天要推进的任务。先派发问题，再跟进责任人处理。</p>
        </HeroCopy>
        <HeroActions>
          <Dropdown menu={{ items: toolItems, onClick: ({ key }) => navigate(key) }} placement="bottomRight">
            <Button icon={<AppstoreOutlined aria-hidden />}>治理工具</Button>
          </Dropdown>
          <Identity aria-label={`当前用户 ${user?.name ?? '管理员'}`}>
            <Avatar>{(user?.name ?? '管').charAt(0)}</Avatar>
            <IdentityText>
              <strong>{user?.name ?? '管理员'}</strong>
              <small>{user?.department ?? '数据治理中心'} · 当前登录</small>
            </IdentityText>
          </Identity>
        </HeroActions>
      </Hero>

      {failedSources.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message="部分数据未加载成功"
          description={`${failedSources.join('、')}暂不可用，页面已保留可操作内容。`}
          action={<Button size="small" icon={<ReloadOutlined />} onClick={retryAll}>重试</Button>}
        />
      )}

      <ProgressPanel>
        <SectionHeader>
          <div><h2>治理阶段进度</h2><p>从问题发现到正式应用，掌握全链路推进状态</p></div>
          <Button type="link" aria-label="查看完整进度" icon={<ClockCircleOutlined aria-hidden />} onClick={() => navigate('/sys/drawing/operations')}>查看完整进度</Button>
        </SectionHeader>
        <ProgressScroll>
          <ProgressTrack aria-label="治理阶段进度：扫描入库、问题池、整改分派、业务确认、质量验收、正式应用">
            {model.nodes.map((node, index) => (
              <Stage key={node.key} $state={node.state}>
                <StageButton type="button" aria-label={`${node.label}：${node.caption}，点击进入`} onClick={() => openStage(node.key)}>
                  <StageDot $state={node.state}>
                    {node.state === 'done' ? <CheckCircleFilled /> : node.state === 'overdue' ? <ExclamationCircleFilled /> : index + 1}
                  </StageDot>
                  <StageText><strong>{node.label}</strong><small>{node.caption}</small></StageText>
                </StageButton>
              </Stage>
            ))}
          </ProgressTrack>
        </ProgressScroll>
      </ProgressPanel>

      <WorkbenchHeading>
        <div>
          <h2>今日工作</h2>
          <p>两个入口覆盖最常用的派发与处理流程</p>
        </div>
        <Button type="text" aria-label="责任看板" icon={<TeamOutlined aria-hidden />} onClick={() => navigate('/sys/drawing/responsibility')}>责任看板</Button>
      </WorkbenchHeading>

      <WorkGrid>
        <WorkCard $tone="dispatch">
          <WorkCardHeader>
            <WorkCardTitle>
              <span className="icon"><PlusOutlined /></span>
              <div><h3>派发任务</h3><p>从待处理问题直接创建治理任务</p></div>
            </WorkCardTitle>
            <CountBadge><strong>{issuesQuery.isError ? '—' : model.pendingIssues.length}</strong><small>项待派发</small></CountBadge>
          </WorkCardHeader>
          <Queue aria-label="待派发问题">
            {queueLoading ? <Skeleton active paragraph={{ rows: 3 }} title={false} /> : model.pendingIssues.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有待派发问题" style={{ margin: '25px 0' }} />
            ) : model.pendingIssues.slice(0, 3).map(issue => (
              <QueueRow key={issue.id} type="button" onClick={() => navigate('/sys/drawing/issues')}>
                <RowMain><strong>{issueLabel(issue)}</strong><small>{issue.issueType} · {issue.blocking ? '阻塞问题' : `${issue.severity} 级`}</small></RowMain>
                <RowMeta $danger={issue.blocking}>{issue.blocking ? '优先派发' : '待派发'}</RowMeta>
                <RightOutlined aria-hidden />
              </QueueRow>
            ))}
          </Queue>
          <CardFooter>
            <span>支持勾选多个问题后一次派发</span>
            <Button type="primary" aria-label="派发任务" icon={<PlusOutlined aria-hidden />} onClick={() => navigate('/sys/drawing/issues')}>派发任务</Button>
          </CardFooter>
        </WorkCard>

        <WorkCard $tone="task">
          <WorkCardHeader>
            <WorkCardTitle>
              <span className="icon"><FileDoneOutlined /></span>
              <div><h3>认领任务</h3><p>{isAdministrator ? '优先展示逾期和待验收任务' : '优先展示分配给我的任务'}</p></div>
            </WorkCardTitle>
            <CountBadge $alert={model.dueSummary.overdue > 0}><strong>{tasksQuery.isError ? '—' : model.focusTasks.length}</strong><small>项待推进</small></CountBadge>
          </WorkCardHeader>
          <Queue aria-label="待处理任务">
            {queueLoading ? <Skeleton active paragraph={{ rows: 3 }} title={false} /> : model.focusTasks.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有待处理任务" style={{ margin: '25px 0' }} />
            ) : model.focusTasks.slice(0, 3).map(task => {
              const diff = dueDayDiff(task.dueDate, model.today)
              const overdue = diff !== null && diff < 0
              return (
                <QueueRow key={task.id} type="button" onClick={() => onOpenTask?.(task.id)}>
                  <RowMain><strong>{task.name || `治理任务 #${task.id}`}</strong><small>{tasksTitleStatus[task.status]} · {task.owner || '待认领'}</small></RowMain>
                  <RowMeta $danger={overdue}>{overdue ? `逾期 ${Math.abs(diff ?? 0)} 天` : formatMonthDay(task.dueDate)}</RowMeta>
                  <RightOutlined aria-hidden />
                </QueueRow>
              )
            })}
          </Queue>
          <CardFooter>
            <span>任务详情中可启动、移交或继续处理</span>
            <Button type="primary" aria-label={model.focusTasks.length > 0 ? '处理首要任务' : '查看全部任务'} icon={<ArrowRightOutlined aria-hidden />} onClick={() => {
              const firstTask = model.focusTasks[0]
              if (firstTask) onOpenTask?.(firstTask.id)
              else navigate('/sys/drawing/operations')
            }}>{model.focusTasks.length > 0 ? '处理首要任务' : '查看全部任务'}</Button>
          </CardFooter>
        </WorkCard>
      </WorkGrid>

      <StatCards cards={statCards} />
    </Page>
  )
}
