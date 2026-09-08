import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Collapse, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getGovernanceIssues, getGovernanceScanRuns, getGovernanceTasks, getInventory } from '../api'
import { GovernanceOverviewPage } from '../overview/GovernanceOverviewPage'
import type { GovernanceIssue, GovernanceScanRun, GovernanceTask, GovernanceTaskStatus } from '../types'
import { GovernanceNextSuggestion, GovernanceTodoPanel } from './GovernanceStepPanel'
import type { GovernanceTodoRow } from './GovernanceStepPanel'
import type { GovernanceStatCardData } from './StatCards'
import { StatCards } from './StatCards'
import {
  buildGovernanceRailModel,
  classifyGovernanceTaskStage,
  dueDayDiff,
  emptyGovernanceRailCounts,
  governanceTaskStageWait,
  isTaskEscalated,
  summarizeGovernanceDue,
  summarizeGovernanceIssuesForRail,
  summarizeGovernanceTasksForRail,
} from './governanceRailModel'
import type { GovernanceRailCounts, GovernanceRailStageKey } from './governanceRailModel'
import { GovernanceRail } from './GovernanceRail'
import { GovernanceMyTodo } from './GovernanceMyTodo'
import { railTheme } from './railTheme'

const Section = styled.section`
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
`

const Hero = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
`

const PanelTitle = styled.h3`
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 8px;
  color: ${railTheme.text};
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.1px;

  .hint {
    color: ${railTheme.text3};
    font-size: 12px;
    font-weight: 400;
  }
`

const SecondarySection = styled.div`
  .ant-collapse {
    overflow: hidden;
    background: ${railTheme.card};
    border: 1px solid ${railTheme.line};
    border-radius: ${railTheme.radius}px;
    box-shadow: ${railTheme.shadow};
  }

  .ant-collapse-header {
    align-items: center !important;
    min-height: 52px;
    color: ${railTheme.text} !important;
    font-size: 14px;
    font-weight: 650;
  }

  .ant-collapse-content {
    border-top-color: ${railTheme.line};
  }

  .ant-collapse-content-box {
    padding: 16px !important;
  }
`

const ActionGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(300px, 0.75fr);
  gap: 16px;
  align-items: start;

  @media (max-width: 1080px) {
    grid-template-columns: 1fr;
  }
`

const FocusHeader = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 2px 0 -8px;

  h2 {
    margin: 0;
    color: ${railTheme.text};
    font-size: 18px;
    font-weight: 700;
  }

  span {
    color: ${railTheme.text3};
    font-size: 12px;
  }
`

const tasksTitleStatus: Record<GovernanceTaskStatus, string> = {
  DRAFT: '草稿',
  IN_PROGRESS: '执行中',
  PENDING_CONFIRMATION: '待确认',
  PENDING_ACCEPTANCE: '待验收',
  REWORK_REQUIRED: '需返工',
  COMPLETED: '已完成',
}

type PillTone = 'success' | 'danger' | 'warning' | 'info' | 'plain'

const fieldLabels: Record<string, string> = {
  DESCRIPTION: '功能说明',
  SPECIALTIES: '专业类别',
  OWNER: '责任人',
  SCOPE: '适用范围',
}

const scanStatusMeta: Record<GovernanceScanRun['status'], { label: string; tone: PillTone }> = {
  SUCCEEDED: { label: '成功', tone: 'success' },
  FAILED: { label: '失败', tone: 'danger' },
  RUNNING: { label: '运行中', tone: 'warning' },
}

const taskStatusTone: Record<GovernanceTaskStatus, PillTone> = {
  DRAFT: 'plain',
  IN_PROGRESS: 'info',
  PENDING_CONFIRMATION: 'warning',
  PENDING_ACCEPTANCE: 'warning',
  REWORK_REQUIRED: 'danger',
  COMPLETED: 'success',
}

function formatMonthDay(value: string | null | undefined): string | null {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return null
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

function formatDateTime(value: string | null | undefined): string {
  const date = value ? new Date(value) : null
  if (!date || Number.isNaN(date.getTime())) return '—'
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function groupTasksByStage(tasks: GovernanceTask[]): Record<'assign' | 'confirm' | 'accept' | 'apply', GovernanceTask[]> {
  const grouped: Record<'assign' | 'confirm' | 'accept' | 'apply', GovernanceTask[]> = { assign: [], confirm: [], accept: [], apply: [] }
  for (const task of tasks) {
    grouped[classifyGovernanceTaskStage(task)].push(task)
  }
  return grouped
}

interface GovernanceRailHomeProps {
  onOpenTask?: (taskId: number) => void
}

/** R1a 治理中心首页：指标卡 + 六节点轨道 + 该步待办 + 下一步建议 + 任务闭环明细。 */
export function GovernanceRailHome({ onOpenTask }: GovernanceRailHomeProps) {
  const navigate = useNavigate()
  const [selectedKey, setSelectedKey] = useState<GovernanceRailStageKey | null>(null)
  const [overdueOnly, setOverdueOnly] = useState(false)

  const inventoryQuery = useQuery({ queryKey: ['rail-inventory'], queryFn: () => getInventory({ page: 1, perPage: 1 }), staleTime: 60_000 })
  const issuesQuery = useQuery({ queryKey: ['rail-issues'], queryFn: () => getGovernanceIssues(), staleTime: 60_000 })
  const tasksQuery = useQuery({ queryKey: ['governance-tasks'], queryFn: () => getGovernanceTasks(), staleTime: 60_000 })
  const scansQuery = useQuery({ queryKey: ['rail-scans'], queryFn: getGovernanceScanRuns, staleTime: 60_000 })

  const derived = useMemo(() => {
    const today = new Date()
    const tasks = tasksQuery.data ?? []
    const issues = issuesQuery.data ?? []
    const scans = scansQuery.data ?? []
    const totals = inventoryQuery.data?.totals
    const rates = inventoryQuery.data?.rates

    const taskSummary = summarizeGovernanceTasksForRail(tasks, today)
    const issueSummary = summarizeGovernanceIssuesForRail(issues)
    const dueSummary = summarizeGovernanceDue(tasks, today)

    const successfulScanAssets = scans
      .filter(run => run.status === 'SUCCEEDED')
      .reduce((sum, run) => sum + run.scannedAssetCount, 0)
    const scannedAssetCount = totals && totals.total > 0 ? totals.total : successfulScanAssets
    const acceptedInCompleted = tasks
      .filter(task => task.status === 'COMPLETED')
      .reduce((sum, task) => sum + (task.progress?.accepted ?? task.completed ?? 0), 0)

    const counts: GovernanceRailCounts = {
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
      standardizedAssets: totals?.standardized ?? acceptedInCompleted,
      taskTotalCount: tasks.length,
      applyOwners: taskSummary.applyOwners,
    }

    return {
      counts,
      nodes: buildGovernanceRailModel(counts),
      stageTasks: groupTasksByStage(tasks),
      poolIssues: issues.filter(issue => issue.status !== 'RESOLVED' && issue.taskId === null),
      scans,
      dueSummary,
      escalatedCount: tasks.filter(task => isTaskEscalated(task, today)).length,
      rates,
      inventoryTotal: totals?.total,
      pendingCuration: totals?.pendingCuration ?? null,
      standardized: totals?.standardized ?? null,
      duplicateSuspects: totals?.duplicateSuspects ?? 0,
      today,
    }
  }, [inventoryQuery.data, issuesQuery.data, tasksQuery.data, scansQuery.data])

  const { nodes } = derived
  const effectiveKey: GovernanceRailStageKey = selectedKey
    ?? (nodes.find(node => node.state === 'current')?.key ?? (nodes.every(node => node.state === 'done') ? 'apply' : 'scan'))

  const statCards: GovernanceStatCardData[] = [
    {
      key: 'pending',
      label: '待整理资产',
      value: derived.pendingCuration,
      unit: '条',
      tone: 'default',
      footnote: derived.inventoryTotal !== undefined
        ? `共 ${derived.inventoryTotal.toLocaleString('zh-CN')} 条存量 · 疑似重复 ${derived.duplicateSuspects} 条`
        : '',
    },
    {
      key: 'week',
      label: '本周到期任务',
      value: tasksQuery.isError ? null : derived.dueSummary.dueWithin7Days,
      unit: '个',
      tone: derived.dueSummary.dueWithin7Days > 0 ? 'warn' : 'default',
      footnote: `${derived.dueSummary.dueWithin48Hours} 条 48 小时内到期`,
    },
    {
      key: 'overdue',
      label: '已逾期',
      value: tasksQuery.isError ? null : derived.dueSummary.overdue,
      unit: '个',
      tone: 'alert',
      footnote: derived.dueSummary.overdue === 0
        ? '暂无逾期任务 · 逾期满 3 天自动升级内容管理员'
        : derived.escalatedCount > 0
          ? `涉及 ${derived.dueSummary.overdueOwnerCount} 位责任人 · ${derived.escalatedCount} 个已升级，管理员待跟进`
          : `涉及 ${derived.dueSummary.overdueOwnerCount} 位责任人 · 责任人跟进中（逾期满 3 天升级）`,
    },
    {
      key: 'standardized',
      label: '已标准化',
      value: derived.standardized,
      unit: '条',
      tone: 'success',
      footnote: derived.rates?.scopeCoverage !== undefined ? `范围覆盖率 ${Math.round(derived.rates.scopeCoverage)}%` : '',
    },
  ]

  const failedSources = [
    inventoryQuery.isError && '待整理/标准化盘点',
    issuesQuery.isError && '问题池',
    tasksQuery.isError && '治理任务',
    scansQuery.isError && '扫描运行',
  ].filter((label): label is string => Boolean(label))
  const retryAll = () => {
    for (const query of [inventoryQuery, issuesQuery, tasksQuery, scansQuery]) {
      if (query.isError) void query.refetch()
    }
  }

  const stepView = useMemo(() => {
    const buildTaskRows = (stageTasks: GovernanceTask[]): GovernanceTodoRow[] => {
      const rows: GovernanceTodoRow[] = stageTasks
        .map(task => {
          const diff = dueDayDiff(task.dueDate, derived.today)
          const overdue = diff !== null && diff < 0
          const wait = governanceTaskStageWait(task)
          return {
            key: `task-${task.id}`,
            kind: 'task',
            title: task.name || `治理任务 #${task.id}`,
            description: wait > 0 ? `该阶段待办 ${wait} 条 · ${tasksTitleStatus[task.status]}` : tasksTitleStatus[task.status],
            pill: { label: overdue ? '已逾期' : tasksTitleStatus[task.status], tone: overdue ? 'danger' : taskStatusTone[task.status] },
            owner: task.owner,
            due: formatMonthDay(task.dueDate) ?? undefined,
            dueTone: overdue ? 'overdue' : diff !== null && diff >= 0 && diff <= 2 ? 'soon' : undefined,
            overdue,
            onClick: () => onOpenTask?.(task.id),
          } satisfies GovernanceTodoRow
        })
        .sort((a, b) => Number(b.overdue) - Number(a.overdue) || a.due?.localeCompare(b.due ?? '') || a.title.localeCompare(b.title, 'zh-CN'))
      const filtered = overdueOnly ? rows.filter(row => row.overdue) : rows
      return filtered.slice(0, 6)
    }

    const middleKeys: Array<Exclude<GovernanceRailStageKey, 'scan' | 'issuePool'>> = ['assign', 'confirm', 'accept', 'apply']
    if (middleKeys.includes(effectiveKey as (typeof middleKeys)[number])) {
      const key = effectiveKey as (typeof middleKeys)[number]
      const stageTasks = derived.stageTasks[key]
      const overdueCount = key === 'apply' ? 0 : derived.counts[key].overdue
      const singleTask = stageTasks.length === 1 ? stageTasks[0] : null
      const targetRoute = singleTask ? `/sys/drawing/tasks/${singleTask.id}` : '/sys/drawing/operations'
      const tasksRow = buildTaskRows(stageTasks)
      const isApply = key === 'apply'
      return {
        key,
        title: isApply
          ? '正式应用 · 已完成闭环'
          : key === 'assign' ? '整改分派 · 整改待办' : key === 'confirm' ? '业务确认 · 待确认清单' : '质量验收 · 待验收清单',
        rows: tasksRow,
        emptyText: isApply
          ? (derived.standardized ?? 0) > 0 ? `尚无已完成闭环任务（已标准化 ${derived.standardized} 条资产）` : '还没有完成闭环的治理任务'
          : `该阶段暂无待办${overdueCount > 0 ? '，仅剩逾期未跟进' : ''}`,
        emptyAction: isApply ? { label: '去盘点查看标准化成果', onClick: () => navigate('/sys/drawing/inventory') } : undefined,
        footer: isApply
          ? `已标准化 ${derived.standardized ?? 0} 条资产 · 已完成 ${derived.stageTasks.apply.length} 个闭环任务`
          : `共 ${stageTasks.length} 个任务处于该阶段 · 点击行进入任务详情`,
        suggestionText: suggestionTextFor(key),
        primary: singleTask ? `进入「${singleTask.name || `任务 #${singleTask.id}`}」` : primaryLabelFor(key),
        onPrimary: () => navigate(targetRoute),
        note: noteFor(key),
      }
    }

    if (effectiveKey === 'scan') {
      const scanRows: GovernanceTodoRow[] = derived.scans.slice(0, 5).map(run => {
        const meta = scanStatusMeta[run.status]
        return {
          key: `scan-${run.id}`,
          kind: 'scan',
          title: `扫描 #${run.id} · ${formatDateTime(run.startedAt)}`,
          description: `扫描 ${run.scannedAssetCount} 条资产 · 新增问题 ${run.createdIssueCount}${run.errorMessage ? ` · ${run.errorMessage}` : ''}`,
          pill: { label: meta.label, tone: meta.tone },
        } satisfies GovernanceTodoRow
      })
      return {
        key: 'scan' as const,
        title: '扫描入库 · 运行记录',
        rows: scanRows,
        emptyText: '还没有扫描运行记录',
        emptyAction: { label: '去自动扫描', onClick: () => navigate('/sys/drawing/scans') },
        footer: scanRows.length > 0 ? `共 ${derived.scans.length} 次运行 · 完整记录见「自动扫描」子页` : undefined,
        suggestionText: scanRows.length === 0
          ? '运行扫描会按启用中的标准核验资产并生成问题记录，之后资产进入治理轨道。'
          : '扫描已完成入库，接下来把扫描发现的问题从问题池分派成治理任务即可推进闭环。',
        primary: '去自动扫描',
        onPrimary: () => navigate('/sys/drawing/scans'),
        note: '每次扫描会记录核验资产数与新发现问题数，可在「自动扫描」回看。',
      }
    }

    const issueRows: GovernanceTodoRow[] = derived.poolIssues.slice(0, 6).map((issue: GovernanceIssue) => ({
      key: `issue-${issue.id}`,
      kind: 'issue',
      title: `资产 #${issue.assetId} · ${fieldLabels[issue.targetField] ?? issue.targetField}`,
      description: `${issue.issueType} · ${issue.severity} 级${issue.blocking ? ' · 阻塞' : ''} · ${formatDateTime(issue.createdAt)} 发现`,
      pill: { label: '待分派', tone: 'info' },
    }))
    return {
      key: 'issuePool' as const,
      title: '问题池 · 待分派',
      rows: issueRows,
      emptyText: '问题池已清空或尚未生成问题',
      emptyAction: issueRows.length === 0 && derived.counts.issueTotalEver === 0
        ? { label: '先去运行扫描', onClick: () => navigate('/sys/drawing/scans') }
        : undefined,
      footer: derived.counts.issueTotalEver > 0
        ? `累计生成 ${derived.counts.issueTotalEver} 条问题 · 可在「字段问题池」筛选并创建治理任务`
        : undefined,
      suggestionText: derived.counts.poolOpenCount > 0
        ? `把「问题池」的 ${derived.counts.poolOpenCount} 条开放问题按集合创建治理任务，任务进入整改分派后由责任人跟进。`
        : '当前没有待分派问题；新的扫描发现会出现在这里。',
      primary: '去问题池分派',
      onPrimary: () => navigate('/sys/drawing/issues'),
      note: '勾选多条问题即可一次创建治理任务，系统自动把资产与规则带入任务。',
    }
  }, [effectiveKey, overdueOnly, derived, navigate, onOpenTask])

  const rowsCount = stepView.rows.length
  const panelLoading = effectiveKey === 'scan' ? scansQuery.isLoading : effectiveKey === 'issuePool' ? issuesQuery.isLoading : tasksQuery.isLoading

  return (
    <Section>
      <Hero>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>数据治理 · 分派工作台</Typography.Title>
          <Typography.Text type="secondary">让「下一步该谁做」一眼可见 —— 指标与轨道由治理事实实时汇总</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={retryAll}>刷新</Button>
      </Hero>

      {failedSources.length > 0 && (
        <Alert
          type="warning"
          showIcon
          message="部分数据未加载成功"
          description={`以下数据源加载失败：${failedSources.join('、')}。相关数字显示为「—」，可点击重试恢复。`}
          action={<Button size="small" type="primary" onClick={retryAll}>重试</Button>}
        />
      )}

      <StatCards cards={statCards} />

      <FocusHeader>
        <h2>现在先处理</h2>
        <span>只看当前最需要推进的一步</span>
      </FocusHeader>
      <ActionGrid>
        <GovernanceTodoPanel
          title={stepView.title}
          hint={overdueOnly ? '仅显示逾期条目' : undefined}
          rows={stepView.rows}
          loading={panelLoading}
          emptyText={stepView.emptyText}
          emptyAction={stepView.emptyAction}
          footer={stepView.footer}
          filtered={overdueOnly}
        />
        <GovernanceNextSuggestion
          text={stepView.suggestionText}
          primaryLabel={stepView.primary}
          onPrimary={stepView.onPrimary}
          secondaryLabel={['assign', 'confirm', 'accept'].includes(effectiveKey) && rowsCount > 0 ? '只看逾期' : undefined}
          secondaryActive={overdueOnly}
          onSecondary={() => setOverdueOnly(value => !value)}
          note={stepView.note}
        />
      </ActionGrid>

      <GovernanceMyTodo onOpenTask={onOpenTask} />

      <SecondarySection>
        <Collapse
          ghost
          items={[{
            key: 'rail',
            label: '查看治理阶段进度',
            children: <>
              <PanelTitle>治理轨道 <span className="hint">点击任一节点查看该步待办</span></PanelTitle>
              <GovernanceRail
                nodes={nodes}
                selectedKey={effectiveKey}
                onSelect={key => { setSelectedKey(key); setOverdueOnly(false) }}
                hint="完成即打勾变绿；当前步骤蓝色呼吸；逾期节点红色警示。"
              />
            </>,
          }]}
        />
      </SecondarySection>

      <SecondarySection>
        <Collapse
          ghost
          items={[{
            key: 'details',
            label: '查看全部任务明细',
            children: <GovernanceOverviewPage embedded onOpenTask={onOpenTask} />,
          }]}
        />
      </SecondarySection>
    </Section>
  )
}

function suggestionTextFor(key: 'assign' | 'confirm' | 'accept' | 'apply'): string {
  switch (key) {
    case 'assign':
      return '把待整改条目分配给责任人执行，提交后进入业务确认；临近到期自动提醒责任人，逾期满 3 天自动升级给内容管理员跟进。'
    case 'confirm':
      return '资产责任人核对整改结果并确认归属；全部通过后进入质量验收，退回项回到整改分派。'
    case 'accept':
      return '按质量指标与抽样复核验收；全部通过后修正值正式应用，原值保留可追溯。'
    default:
      return '验收通过的修正值已写入资产并保留原值；可在盘点与标准中心核对成果。'
  }
}

function primaryLabelFor(key: 'assign' | 'confirm' | 'accept' | 'apply'): string {
  switch (key) {
    case 'apply': return '查看标准化成果'
    default: return '查看治理运营'
  }
}

function noteFor(key: GovernanceRailStageKey): string {
  switch (key) {
    case 'scan':
      return '每次扫描会记录核验资产数与新发现问题数。'
    case 'issuePool':
      return '勾选多条问题即可一次创建治理任务。'
    case 'assign':
      return '分派后任务进入责任人「我的待办」；可在下方任务闭环明细查看每个任务所处阶段。'
    case 'confirm':
      return '业务确认由资产责任人执行；逾期满 3 天自动升级给内容管理员跟进（3/7/14 天分档提醒）。'
    case 'accept':
      return '验收包含指标通过率与抽样复核；失败项返回整改。'
    default:
      return '正式应用由系统执行，全程保留操作记录与原始值快照。'
  }
}
