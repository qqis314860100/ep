import type { GovernancePlan } from '../types'

const DAY_MS = 86_400_000

export type GanttRowState = 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'OVERDUE' | 'DONE'

export interface GanttRow {
  plan: GovernancePlan
  offsetDays: number
  durationDays: number
  progressPercent: number
  state: GanttRowState
  invalidDependencyIds: number[]
}

export interface GanttModel {
  range: { start: string; end: string; totalDays: number; scale: 'day' | 'week' }
  ticks: Array<{ date: string; offsetDays: number; label: string }>
  rows: GanttRow[]
  invalidPlans: GovernancePlan[]
  connections: Array<{ fromPlanId: number; toPlanId: number }>
  todayOffset: number | null
}

function isoDate(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10)
}

function parseDate(value?: string): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const timestamp = Date.parse(`${value}T00:00:00Z`)
  return Number.isNaN(timestamp) || isoDate(timestamp) !== value ? null : timestamp
}

function daysBetween(start: number, end: number): number {
  return Math.round((end - start) / DAY_MS)
}

function isDone(plan: GovernancePlan): boolean {
  return plan.status === 'DONE' || plan.completedQuantity >= plan.plannedQuantity && plan.plannedQuantity > 0
}

export function buildGanttModel(plans: GovernancePlan[], today: string): GanttModel {
  const dated = plans.flatMap(plan => {
    const start = parseDate(plan.plannedStart)
    const end = parseDate(plan.plannedEnd)
    return start !== null && end !== null && end >= start ? [{ plan, start, end }] : []
  })
  const datedIds = new Set(dated.map(item => item.plan.id))
  const invalidPlans = plans.filter(plan => !datedIds.has(plan.id))
  if (dated.length === 0) {
    return {
      range: { start: '', end: '', totalDays: 0, scale: 'day' },
      ticks: [],
      rows: [],
      invalidPlans,
      connections: [],
      todayOffset: null,
    }
  }

  const rangeStart = Math.min(...dated.map(item => item.start)) - DAY_MS
  const rangeEnd = Math.max(...dated.map(item => item.end)) + DAY_MS
  const totalDays = daysBetween(rangeStart, rangeEnd) + 1
  const scale = totalDays <= 31 ? 'day' : 'week'
  const planById = new Map(plans.map(plan => [plan.id, plan]))
  const todayTimestamp = parseDate(today)
  const rows = dated.map(({ plan, start, end }): GanttRow => {
    const invalidDependencyIds = plan.dependencyIds.filter(id => !planById.has(id))
    const blocked = plan.dependencyIds.some(id => {
      const dependency = planById.get(id)
      return dependency ? !isDone(dependency) : false
    })
    const completed = isDone(plan)
    const overdue = todayTimestamp !== null && end < todayTimestamp && !completed
    const state: GanttRowState = completed
      ? 'DONE'
      : overdue
        ? 'OVERDUE'
        : blocked || plan.status === 'BLOCKED'
          ? 'BLOCKED'
          : plan.status === 'IN_PROGRESS'
            ? 'IN_PROGRESS'
            : 'NOT_STARTED'
    const rawPercent = plan.plannedQuantity > 0 ? plan.completedQuantity * 100 / plan.plannedQuantity : 0
    return {
      plan,
      offsetDays: daysBetween(rangeStart, start),
      durationDays: daysBetween(start, end) + 1,
      progressPercent: Math.round(Math.max(0, Math.min(100, rawPercent))),
      state,
      invalidDependencyIds,
    }
  })
  const validIds = new Set(rows.map(row => row.plan.id))
  const connections = rows.flatMap(row => row.plan.dependencyIds
    .filter(id => validIds.has(id))
    .map(id => ({ fromPlanId: id, toPlanId: row.plan.id })))
  const step = scale === 'day' ? 1 : 7
  const ticks = Array.from({ length: Math.ceil(totalDays / step) }, (_, index) => {
    const offsetDays = index * step
    const date = isoDate(rangeStart + offsetDays * DAY_MS)
    return { date, offsetDays, label: scale === 'day' ? date.slice(5) : date }
  })
  const candidateTodayOffset = todayTimestamp === null ? -1 : daysBetween(rangeStart, todayTimestamp)
  return {
    range: { start: isoDate(rangeStart), end: isoDate(rangeEnd), totalDays, scale },
    ticks,
    rows,
    invalidPlans,
    connections,
    todayOffset: candidateTodayOffset >= 0 && candidateTodayOffset < totalDays ? candidateTodayOffset : null,
  }
}
