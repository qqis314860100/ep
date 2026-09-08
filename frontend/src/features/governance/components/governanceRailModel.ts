/**
 * 治理轨道 v1 纯函数模型（R1a 首屏）。
 *
 * 职责：把治理事实（任务/问题/扫描/库存的汇总计数）映射为六节点轨道
 * （扫描入库→问题池→整改分派→业务确认→质量验收→正式应用）的节点状态，
 * 全部派生逻辑保持纯函数、可单测；组件层只负责取数与渲染。
 *
 * 节点状态机（规格 §2）：done 绿勾 / current 蓝呼吸 / overdue 红警示 / wait 灰+计数。
 * 状态判定约定（多任务并存时以"待办量最大"的阶段为 current，其余非逾期阶段保持
 * wait，其它逾期阶段标红——与原型里"整改分派 current + 业务确认 overdue 并存"一致）：
 *  - current 阶段的逾期并入说明文字，不单独标红；其余含逾期条目的阶段 → overdue；
 *  - 无任何待办但系统已有数据（闭环已收口）→ 中间阶段记为 done、正式应用节点 done；
 *  - 系统完全没有数据 → 仅"扫描入库"为 current（从这里开始）。
 */
import type { GovernanceIssue, GovernanceTask } from '../types'

export type GovernanceRailStageKey = 'scan' | 'issuePool' | 'assign' | 'confirm' | 'accept' | 'apply'
export type GovernanceRailNodeState = 'done' | 'current' | 'overdue' | 'wait'

export interface GovernanceRailNodeModel {
  key: GovernanceRailStageKey
  index: number
  label: string
  state: GovernanceRailNodeState
  /** 徽标计数；0 时不渲染徽标 */
  badge: number
  /** 状态说明文字（可达性：状态不只靠颜色表达） */
  caption: string
  /** 责任人姓名示意（≤4 展示 +N 折叠） */
  owners: string[]
}

export interface GovernanceRailStageCounts {
  /** 该阶段待办条目数 */
  wait: number
  /** 该阶段逾期任务数 */
  overdue: number
  /** 该阶段涉及的责任人姓名（去重保序） */
  owners: string[]
}

export interface GovernanceRailCounts {
  /** 扫描运行次数 */
  scanRunCount: number
  /** 是否存在成功扫描 */
  scanSucceeded: boolean
  /** 最近一次扫描是否失败 */
  latestScanFailed: boolean
  /** 已扫描/已入库资产数（扫描节点计数） */
  scannedAssetCount: number
  /** 盘点资产总量（作为入库证据） */
  inventoryTotal: number
  /** 问题池待分派数（开放且尚未进入任务） */
  poolOpenCount: number
  /** 问题累计产生数（含已分派/已解决） */
  issueTotalEver: number
  assign: GovernanceRailStageCounts
  confirm: GovernanceRailStageCounts
  accept: GovernanceRailStageCounts
  /** 已完成（闭环收口）任务数 */
  completedTaskCount: number
  /** 已标准化（正式应用）资产数 */
  standardizedAssets: number
  /** 任务总数（是否已有治理任务） */
  taskTotalCount: number
  /** 已完成任务的责任人（正式应用节点示意） */
  applyOwners: string[]
}

export function emptyGovernanceRailCounts(): GovernanceRailCounts {
  return {
    scanRunCount: 0,
    scanSucceeded: false,
    latestScanFailed: false,
    scannedAssetCount: 0,
    inventoryTotal: 0,
    poolOpenCount: 0,
    issueTotalEver: 0,
    assign: { wait: 0, overdue: 0, owners: [] },
    confirm: { wait: 0, overdue: 0, owners: [] },
    accept: { wait: 0, overdue: 0, owners: [] },
    completedTaskCount: 0,
    standardizedAssets: 0,
    taskTotalCount: 0,
    applyOwners: [],
  }
}

export const GOVERNANCE_RAIL_LABELS: Record<GovernanceRailStageKey, string> = {
  scan: '扫描入库',
  issuePool: '问题池',
  assign: '整改分派',
  confirm: '业务确认',
  accept: '质量验收',
  apply: '正式应用',
}

/**
 * 任务归属哪个治理阶段（轨道 2~5 节点）。
 * 依据任务状态与 progress 计数推断：提交<总量→整改；已提交未确认→业务确认；
 * 已确认未验收→质量验收；其余已收口→正式应用。
 */
export function classifyGovernanceTaskStage(task: GovernanceTask): 'assign' | 'confirm' | 'accept' | 'apply' {
  if (task.status === 'COMPLETED') return 'apply'
  if (task.status === 'REWORK_REQUIRED') return 'assign'
  const progress = task.progress
  if (!progress) {
    // 无 progress 的任务（草稿/历史汇总）：按状态落到最近似阶段，待办按 legacy 总量近似。
    if (task.status === 'PENDING_CONFIRMATION') return 'confirm'
    if (task.status === 'PENDING_ACCEPTANCE') return 'accept'
    return 'assign'
  }
  if (progress.submitted < progress.total) return 'assign'
  if (progress.confirmed < progress.submitted) return 'confirm'
  if (progress.accepted < progress.confirmed) return 'accept'
  return 'apply'
}

/** 任务在当前阶段的待办条目数（wait 口径，apply 无待办） */
export function governanceTaskStageWait(task: GovernanceTask): number {
  const stage = classifyGovernanceTaskStage(task)
  if (stage === 'apply') return 0
  const progress = task.progress
  if (!progress) {
    // legacy/草稿：无 progress 时无法区分确认/验收，仅 assign 阶段按 legacy 总量-完成量近似。
    return stage === 'assign' ? Math.max(0, (task.total ?? 0) - (task.completed ?? 0)) : 0
  }
  if (stage === 'assign') return Math.max(0, progress.total - progress.submitted)
  if (stage === 'confirm') return Math.max(0, progress.submitted - progress.confirmed)
  return Math.max(0, progress.confirmed - progress.accepted)
}

function pushUnique(names: string[], name: string) {
  const trimmed = (name ?? '').trim()
  if (trimmed && !names.includes(trimmed)) names.push(trimmed)
}

/** 逾期口径：dueDate 早于"今天"且任务未完成。 */
export function isTaskOverdue(task: Pick<GovernanceTask, 'status' | 'dueDate'>, now: Date): boolean {
  if (task.status === 'COMPLETED') return false
  const diff = dueDayDiff(task.dueDate, now)
  return diff !== null && diff < 0
}

/**
 * 汇总任务列表为轨道计数（assign/confirm/accept 三阶段待办、逾期、责任人）
 * 与正式应用节点的已完成任务责任人。逾期为任务级计数，计入其所在阶段。
 */
export function summarizeGovernanceTasksForRail(tasks: GovernanceTask[], now: Date) {
  const assign: GovernanceRailStageCounts = { wait: 0, overdue: 0, owners: [] }
  const confirm: GovernanceRailStageCounts = { wait: 0, overdue: 0, owners: [] }
  const accept: GovernanceRailStageCounts = { wait: 0, overdue: 0, owners: [] }
  const applyOwners: string[] = []
  for (const task of tasks) {
    const stage = classifyGovernanceTaskStage(task)
    const target = stage === 'apply' ? null : stage === 'confirm' ? confirm : stage === 'accept' ? accept : assign
    if (!target) {
      pushUnique(applyOwners, task.owner)
      continue
    }
    target.wait += governanceTaskStageWait(task)
    pushUnique(target.owners, task.owner)
    if (isTaskOverdue(task, now)) target.overdue += 1
  }
  return { assign, confirm, accept, applyOwners }
}

/** 问题池口径：开放（未解决）且尚未进入任务（taskId 为空）的问题为"待分派"。 */
export function summarizeGovernanceIssuesForRail(issues: GovernanceIssue[]) {
  const poolOpenCount = issues.filter(issue => issue.status !== 'RESOLVED' && issue.taskId === null).length
  return { poolOpenCount, issueTotalEver: issues.length }
}

/* ---------------- 日期工具（统一为"自然日差"，与具体时区无关，便于单测） ---------------- */

export function parseDueDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? null : date
}

function dayNumber(date: Date): number {
  return Date.UTC(date.getFullYear(), date.getMonth(), date.getDate())
}

/** 截止日距"今天"相差的自然日数：负数=已逾期，0=今天到期，正数=未来第 N 天。解析失败返回 null。 */
export function dueDayDiff(dueDate: string | null | undefined, now: Date): number | null {
  const due = parseDueDate(dueDate)
  if (!due) return null
  return Math.round((dayNumber(due) - dayNumber(now)) / 86_400_000)
}

export interface GovernanceDueSummary {
  /** 已逾期（未完成任务） */
  overdue: number
  /** 未来 7 天内到期（含今天，不含已逾期） */
  dueWithin7Days: number
  /** 48 小时内到期（不含已逾期） */
  dueWithin48Hours: number
  /** 逾期涉及的责任人数（去重） */
  overdueOwnerCount: number
}

/** 顶部指标卡：逾期 / 本周到期 / 48h 到期，均排除已完成任务。 */
export function summarizeGovernanceDue(tasks: GovernanceTask[], now: Date): GovernanceDueSummary {
  const owners = new Set<string>()
  let overdue = 0
  let dueWithin7Days = 0
  let dueWithin48Hours = 0
  for (const task of tasks) {
    if (task.status === 'COMPLETED') continue
    const diff = dueDayDiff(task.dueDate, now)
    if (diff === null) continue
    if (diff < 0) {
      overdue += 1
      const owner = (task.owner ?? '').trim()
      if (owner) owners.add(owner)
      continue
    }
    if (diff <= 7) {
      dueWithin7Days += 1
      if (diff <= 2) dueWithin48Hours += 1
    }
  }
  return { overdue, dueWithin7Days, dueWithin48Hours, overdueOwnerCount: owners.size }
}

/* ---------------- 六节点状态机 ---------------- */

/** 组装轨道：输入计数 → 六个按流程排序的节点模型。 */
export function buildGovernanceRailModel(counts: GovernanceRailCounts): GovernanceRailNodeModel[] {
  const dataExists = counts.scanRunCount > 0
    || counts.inventoryTotal > 0
    || counts.issueTotalEver > 0
    || counts.taskTotalCount > 0

  const middle: Array<{ key: 'assign' | 'confirm' | 'accept'; stage: GovernanceRailStageCounts }> = [
    { key: 'assign', stage: counts.assign },
    { key: 'confirm', stage: counts.confirm },
    { key: 'accept', stage: counts.accept },
  ]

  // 唯一 current：待办量最大的阶段（并列取更靠前）；无任何待办则为 -1。
  // 候选阶段为节点索引 1（问题池）与 2/3/4（整改/确认/验收）。
  let dominantIndex = -1
  let dominantWait = 0
  if (counts.poolOpenCount > 0) {
    dominantIndex = 1
    dominantWait = counts.poolOpenCount
  }
  middle.forEach((entry, offset) => {
    if (entry.stage.wait > dominantWait) {
      dominantIndex = offset + 2
      dominantWait = entry.stage.wait
    }
  })
  const everythingDrained = dataExists && dominantIndex === -1
  const isStartup = !dataExists

  const nodes: GovernanceRailNodeModel[] = [scanNode(counts, dataExists, isStartup)]
  nodes.push(poolNode(counts, dominantIndex))
  middle.forEach((entry, offset) => {
    nodes.push(middleNode(entry.key, offset + 2, entry.stage, dominantIndex, everythingDrained))
  })
  nodes.push(applyNode(counts, everythingDrained, dataExists))
  return nodes
}

function scanNode(counts: GovernanceRailCounts, dataExists: boolean, isStartup: boolean): GovernanceRailNodeModel {
  let state: GovernanceRailNodeState
  let caption: string
  if (counts.scanRunCount > 0) {
    if (counts.latestScanFailed && !counts.scanSucceeded) {
      state = 'overdue'
      caption = '最近一次扫描失败，请重试'
    } else {
      state = 'done'
      caption = counts.scannedAssetCount > 0 ? `已完成 · 扫描 ${counts.scannedAssetCount} 条` : '已完成'
    }
  } else if (isStartup) {
    state = 'current'
    caption = '尚未运行扫描，从这里开始'
  } else if (dataExists) {
    state = 'done'
    caption = counts.scannedAssetCount > 0 ? `资产已入库 ${counts.scannedAssetCount} 条` : '已完成'
  } else {
    state = 'wait'
    caption = '等待首次扫描'
  }
  return node('scan', 0, state, counts.scannedAssetCount, caption, [])
}

function poolNode(counts: GovernanceRailCounts, dominantIndex: number): GovernanceRailNodeModel {
  const waiting = counts.poolOpenCount > 0
  let state: GovernanceRailNodeState
  let caption: string
  if (waiting) {
    state = dominantIndex === 1 ? 'current' : 'wait'
    caption = dominantIndex === 1 ? `当前步骤 · ${counts.poolOpenCount} 条待分派` : `待分派 ${counts.poolOpenCount} 条`
  } else if (counts.issueTotalEver > 0 || counts.taskTotalCount > 0 || dominantIndex > 1) {
    state = 'done'
    caption = counts.issueTotalEver > 0 ? `已分派 · 累计 ${counts.issueTotalEver} 条` : '问题池已清空'
  } else {
    state = 'wait'
    caption = '暂无待分派问题'
  }
  return node('issuePool', 1, state, waiting ? counts.poolOpenCount : 0, caption, [])
}

function middleNode(
  key: 'assign' | 'confirm' | 'accept',
  index: number,
  stage: GovernanceRailStageCounts,
  dominantIndex: number,
  everythingDrained: boolean,
): GovernanceRailNodeModel {
  const isCurrent = index === dominantIndex && stage.wait > 0
  let state: GovernanceRailNodeState
  if (stage.wait > 0) {
    state = isCurrent ? 'current' : stage.overdue > 0 ? 'overdue' : 'wait'
  } else if (stage.overdue > 0) {
    state = 'overdue'
  } else if (index < dominantIndex || everythingDrained) {
    state = 'done'
  } else {
    state = 'wait'
  }
  const verb = key === 'assign' ? '整改' : key === 'confirm' ? '业务确认' : '验收'
  let caption: string
  if (state === 'done') {
    caption = key === 'assign' ? '整改已完成' : key === 'confirm' ? '业务确认已完成' : '质量验收已完成'
  } else if (state === 'overdue') {
    caption = `逾期 ${stage.overdue} 条待跟进`
  } else if (state === 'current') {
    caption = stage.overdue > 0
      ? `当前步骤 · 待${verb} ${stage.wait} 条（含逾期 ${stage.overdue}）`
      : `当前步骤 · 待${verb} ${stage.wait} 条`
  } else if (stage.wait > 0) {
    caption = `待${verb} ${stage.wait} 条`
  } else {
    caption = '等待进入'
  }
  return node(key, index, state, state === 'overdue' ? stage.overdue : stage.wait, caption, stage.owners)
}

function applyNode(counts: GovernanceRailCounts, everythingDrained: boolean, dataExists: boolean): GovernanceRailNodeModel {
  const applied = counts.standardizedAssets
  let state: GovernanceRailNodeState
  let caption: string
  if (everythingDrained) {
    state = 'done'
    caption = applied > 0 ? `闭环完成 · 已标准化 ${applied} 条` : '闭环完成'
  } else if (counts.completedTaskCount > 0 || applied > 0) {
    state = 'wait'
    caption = '验收通过后写入资产，原值保留可追溯'
  } else {
    state = 'wait'
    caption = dataExists ? '等待整改、确认与验收通过' : '等待扫描入库'
  }
  const owners: string[] = []
  for (const owner of counts.applyOwners) pushUnique(owners, owner)
  return node('apply', 5, state, applied, caption, owners)
}

function node(
  key: GovernanceRailStageKey,
  index: number,
  state: GovernanceRailNodeState,
  badge: number,
  caption: string,
  owners: string[],
): GovernanceRailNodeModel {
  return { key, index, label: GOVERNANCE_RAIL_LABELS[key], state, badge, caption, owners }
}
