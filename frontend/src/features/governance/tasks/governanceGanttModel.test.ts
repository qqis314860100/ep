import { describe, expect, it } from 'vitest'
import type { GovernancePlan } from '../types'
import { buildGanttModel } from './governanceGanttModel'

const plan = (values: Partial<GovernancePlan> = {}): GovernancePlan => ({
  id: 1,
  taskId: 9,
  title: '字段补充',
  status: 'NOT_STARTED',
  plannedStart: '2026-07-28',
  plannedEnd: '2026-07-28',
  plannedQuantity: 4,
  completedQuantity: 1,
  quantityUnit: '字段',
  responsibleUserId: 'u-1',
  dependencyIds: [],
  ...values,
})

describe('buildGanttModel', () => {
  it('uses inclusive dates and one day of range padding', () => {
    const model = buildGanttModel([plan()], '2026-07-27')

    expect(model.range).toMatchObject({ start: '2026-07-27', end: '2026-07-29', totalDays: 3, scale: 'day' })
    expect(model.rows[0]).toMatchObject({ offsetDays: 1, durationDays: 1, progressPercent: 25, state: 'NOT_STARTED' })
  })

  it('clamps progress and separately marks overdue work', () => {
    const model = buildGanttModel([
      plan({ id: 1, plannedStart: '2026-07-25', plannedEnd: '2026-07-26', plannedQuantity: 2, completedQuantity: 3 }),
      plan({ id: 2, plannedStart: '2026-07-25', plannedEnd: '2026-07-26', plannedQuantity: 2, completedQuantity: 1 }),
    ], '2026-07-27')

    expect(model.rows[0].progressPercent).toBe(100)
    expect(model.rows[0].state).toBe('DONE')
    expect(model.rows[1].state).toBe('OVERDUE')
  })

  it('marks a plan blocked by an unfinished dependency', () => {
    const model = buildGanttModel([
      plan({ id: 1, title: '前置计划' }),
      plan({ id: 2, title: '后续计划', dependencyIds: [1] }),
    ], '2026-07-27')

    expect(model.rows[1].state).toBe('BLOCKED')
    expect(model.connections).toEqual([{ fromPlanId: 1, toPlanId: 2 }])
  })

  it('uses week ticks for ranges longer than 31 days', () => {
    const model = buildGanttModel([plan({ plannedEnd: '2026-09-10' })], '2026-07-27')

    expect(model.range.scale).toBe('week')
    expect(model.ticks.every((tick, index) => index === 0 || tick.offsetDays % 7 === 0)).toBe(true)
  })

  it('returns invalid plans and unknown dependencies without drawing them', () => {
    const model = buildGanttModel([
      plan({ id: 1, title: '日期错误', plannedStart: '2026-07-30', plannedEnd: '2026-07-28' }),
      plan({ id: 2, title: '依赖错误', dependencyIds: [999] }),
    ], '2026-07-27')

    expect(model.invalidPlans.map(item => item.title)).toContain('日期错误')
    expect(model.rows.find(item => item.plan.id === 2)?.invalidDependencyIds).toEqual([999])
    expect(model.connections).toEqual([])
  })
})
