import { describe, expect, it } from 'vitest'
import type { GovernanceProgress, GovernanceTask, GovernanceTaskStatus } from '../types'
import {
  buildGovernanceRailModel,
  classifyGovernanceTaskStage,
  dueDayDiff,
  emptyGovernanceRailCounts,
  governanceTaskStageWait,
  summarizeGovernanceDue,
  summarizeGovernanceIssuesForRail,
  summarizeGovernanceTasksForRail,
} from './governanceRailModel'

const NOW = new Date('2026-09-08T12:00:00')

function makeTask(overrides: Partial<GovernanceTask> & { status: GovernanceTaskStatus; id?: number }): GovernanceTask {
  return {
    id: overrides.id ?? 1,
    name: overrides.name ?? '测试任务',
    scope: overrides.scope ?? '测试范围',
    owner: overrides.owner ?? '王工',
    total: overrides.total ?? 10,
    completed: overrides.completed ?? 0,
    dueDate: overrides.dueDate ?? '2026-09-20',
    progress: overrides.progress ?? null,
    ...overrides,
  } as GovernanceTask
}

const fullProgress = (submitted: number, confirmed: number, accepted: number): GovernanceProgress => ({
  total: 10,
  submitted,
  confirmed,
  accepted,
  blocked: 0,
  reworkRequired: 0,
})

describe('classifyGovernanceTaskStage', () => {
  it('按 progress 计数落到各阶段', () => {
    expect(classifyGovernanceTaskStage(makeTask({ status: 'IN_PROGRESS', progress: fullProgress(4, 0, 0) }))).toBe('assign')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'IN_PROGRESS', progress: fullProgress(10, 0, 0) }))).toBe('confirm')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'PENDING_CONFIRMATION', progress: fullProgress(10, 10, 0) }))).toBe('accept')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'PENDING_ACCEPTANCE', progress: fullProgress(10, 10, 10) }))).toBe('apply')
  })

  it('REWORK_REQUIRED 回到整改阶段，COMPLETED 收口到正式应用', () => {
    expect(classifyGovernanceTaskStage(makeTask({ status: 'REWORK_REQUIRED', progress: fullProgress(10, 0, 0) }))).toBe('assign')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'COMPLETED' }))).toBe('apply')
  })

  it('无 progress 的草稿/历史任务按状态近似', () => {
    expect(classifyGovernanceTaskStage(makeTask({ status: 'DRAFT' }))).toBe('assign')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'PENDING_CONFIRMATION' }))).toBe('confirm')
    expect(classifyGovernanceTaskStage(makeTask({ status: 'PENDING_ACCEPTANCE' }))).toBe('accept')
  })
})

describe('governanceTaskStageWait', () => {
  it('各阶段取"未通过该阶段"的条目数', () => {
    const task = makeTask({ status: 'IN_PROGRESS', progress: fullProgress(6, 4, 0) })
    expect(governanceTaskStageWait(task)).toBe(4) // 10-6：整改阶段未提交
    expect(governanceTaskStageWait(makeTask({ status: 'IN_PROGRESS', progress: fullProgress(10, 4, 0) }))).toBe(6)
    expect(governanceTaskStageWait(makeTask({ status: 'PENDING_CONFIRMATION', progress: fullProgress(10, 10, 2) }))).toBe(8)
    expect(governanceTaskStageWait(makeTask({ status: 'COMPLETED', progress: fullProgress(10, 10, 10) }))).toBe(0)
  })

  it('无 progress 的 assign 任务按 legacy 总量-完成量近似', () => {
    expect(governanceTaskStageWait(makeTask({ status: 'DRAFT', total: 8, completed: 0 }))).toBe(8)
  })
})

describe('buildGovernanceRailModel 状态机', () => {
  it('空系统：仅扫描入库为 current（从这里开始），其余 wait', () => {
    const nodes = buildGovernanceRailModel(emptyGovernanceRailCounts())
    expect(nodes).toHaveLength(6)
    expect(nodes.map(node => [node.key, node.state])).toEqual([
      ['scan', 'current'],
      ['issuePool', 'wait'],
      ['assign', 'wait'],
      ['confirm', 'wait'],
      ['accept', 'wait'],
      ['apply', 'wait'],
    ])
    expect(nodes[0].caption).toContain('尚未运行扫描')
  })

  it('闭环已收口：scan→apply 全 done，正式应用含已标准化计数', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanRunCount = 3
    counts.scanSucceeded = true
    counts.scannedAssetCount = 124
    counts.inventoryTotal = 124
    counts.issueTotalEver = 31
    counts.taskTotalCount = 2
    counts.completedTaskCount = 2
    counts.standardizedAssets = 124
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes.every(node => node.state === 'done')).toBe(true)
    expect(nodes[5].caption).toBe('闭环完成 · 已标准化 124 条')
  })

  it('整改阶段待办最大：assign 为 current；问题池已清空记 done；后续阶段 wait', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanSucceeded = true
    counts.scannedAssetCount = 30
    counts.inventoryTotal = 30
    counts.issueTotalEver = 12
    counts.assign.wait = 6
    counts.confirm.wait = 2
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes.map(node => node.state)).toEqual(['done', 'done', 'current', 'wait', 'wait', 'wait'])
    expect(nodes[2].caption).toContain('当前步骤 · 待整改 6 条')
  })

  it('与原型一致：assign current 时，业务确认阶段的逾期单独标红', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanSucceeded = true
    counts.inventoryTotal = 30
    counts.issueTotalEver = 12
    counts.assign.wait = 6
    counts.confirm.wait = 3
    counts.confirm.overdue = 3
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes[2].state).toBe('current')
    expect(nodes[3].state).toBe('overdue')
    expect(nodes[3].badge).toBe(3)
    expect(nodes[3].caption).toBe('逾期 3 条待跟进')
  })

  it('当前阶段自身含逾期：保持 current，逾期并入说明文字', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanSucceeded = true
    counts.inventoryTotal = 30
    counts.issueTotalEver = 12
    counts.assign.wait = 5
    counts.assign.overdue = 1
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes[2].state).toBe('current')
    expect(nodes[2].caption).toContain('含逾期 1')
  })

  it('最近一次扫描失败：扫描节点为 overdue 警示', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanRunCount = 2
    counts.latestScanFailed = true
    counts.scanSucceeded = false
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes[0].state).toBe('overdue')
    expect(nodes[0].caption).toContain('失败')
  })

  it('无扫描记录但库存已有资产：扫描节点视为已完成（资产已入库）', () => {
    const counts = emptyGovernanceRailCounts()
    counts.inventoryTotal = 42
    counts.issueTotalEver = 3
    counts.poolOpenCount = 3
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes[0].state).toBe('done')
    expect(nodes[1].state).toBe('current') // 待分派问题最多 → 问题池 current
  })

  it('问题池待分派最多时池节点为 current', () => {
    const counts = emptyGovernanceRailCounts()
    counts.scanSucceeded = true
    counts.issueTotalEver = 20
    counts.poolOpenCount = 12
    counts.assign.wait = 3
    const nodes = buildGovernanceRailModel(counts)
    expect(nodes[1].state).toBe('current')
    expect(nodes[1].badge).toBe(12)
  })
})

describe('summarizeGovernanceTasksForRail', () => {
  it('按阶段汇总待办/逾期，责任人去重保序；已完成任务进入 applyOwners', () => {
    const tasks: GovernanceTask[] = [
      makeTask({ id: 1, status: 'IN_PROGRESS', owner: '陈工', progress: fullProgress(4, 0, 0), dueDate: '2026-09-01' }),
      makeTask({ id: 2, status: 'DRAFT', owner: '王工', total: 3, dueDate: '2026-09-10' }),
      makeTask({ id: 3, status: 'PENDING_CONFIRMATION', owner: '李工', progress: fullProgress(10, 0, 0), dueDate: '2026-09-05' }),
      makeTask({ id: 4, status: 'COMPLETED', owner: '赵工', progress: fullProgress(10, 10, 10), dueDate: '2026-09-02' }),
      makeTask({ id: 5, status: 'COMPLETED', owner: '赵工', progress: fullProgress(10, 10, 10), dueDate: '2026-09-02' }),
    ]
    const summary = summarizeGovernanceTasksForRail(tasks, NOW)
    // 任务1：assign wait 10-4=6 且逾期；任务2：assign wait 3 未逾期
    expect(summary.assign.wait).toBe(9)
    expect(summary.assign.overdue).toBe(1)
    expect(summary.assign.owners).toEqual(['陈工', '王工'])
    // 任务3：已提交 10 条全部待业务确认 → confirm wait 10，逾期 1 条
    expect(summary.confirm.wait).toBe(10)
    expect(summary.confirm.overdue).toBe(1)
    expect(summary.confirm.owners).toEqual(['李工'])
    expect(summary.accept).toEqual({ wait: 0, overdue: 0, owners: [] })
    expect(summary.applyOwners).toEqual(['赵工'])
  })
})

describe('summarizeGovernanceDue 指标卡', () => {
  it('逾期 / 7 天内到期 / 48 小时 / 逾期责任人数', () => {
    const tasks: GovernanceTask[] = [
      makeTask({ id: 1, status: 'IN_PROGRESS', owner: '陈工', dueDate: '2026-09-05' }), // 逾期
      makeTask({ id: 2, status: 'IN_PROGRESS', owner: '王工', dueDate: '2026-09-05' }), // 逾期
      makeTask({ id: 3, status: 'PENDING_CONFIRMATION', owner: '王工', dueDate: '2026-09-09' }), // 1 天内 → 7天&48h
      makeTask({ id: 4, status: 'DRAFT', owner: '李工', dueDate: '2026-09-15' }), // 7 天整 → 7 天内
      makeTask({ id: 5, status: 'DRAFT', dueDate: '2026-09-30' }), // 超 7 天不算
      makeTask({ id: 6, status: 'COMPLETED', owner: '赵工', dueDate: '2026-09-01' }), // 已完成不计
      makeTask({ id: 7, status: 'DRAFT', dueDate: '不是日期' }), // 解析失败不计
    ]
    const summary = summarizeGovernanceDue(tasks, NOW)
    expect(summary.overdue).toBe(2)
    expect(summary.overdueOwnerCount).toBe(2)
    expect(summary.dueWithin7Days).toBe(2)
    expect(summary.dueWithin48Hours).toBe(1)
  })
})

describe('dueDayDiff / issues', () => {
  it('自然日差（时区无关）', () => {
    expect(dueDayDiff('2026-09-08', NOW)).toBe(0)
    expect(dueDayDiff('2026-09-07', NOW)).toBe(-1)
    expect(dueDayDiff('2026-09-15', NOW)).toBe(7)
    expect(dueDayDiff(undefined, NOW)).toBeNull()
  })

  it('问题池只统计未解决且未分派（taskId 为空）的问题', () => {
    const issues = [
      { id: 1, status: 'OPEN', taskId: null },
      { id: 2, status: 'CLAIMED', taskId: 5 },
      { id: 3, status: 'RESOLVED', taskId: null },
      { id: 4, status: 'OPEN', taskId: 6 },
    ]
    const summary = summarizeGovernanceIssuesForRail(issues as never)
    expect(summary.poolOpenCount).toBe(1)
    expect(summary.issueTotalEver).toBe(4)
  })
})
