import type { GanttModel } from './governanceGanttModel'

export const GANTT_DAY_WIDTH = 36
export const GANTT_ROW_HEIGHT = 56

export function buildDependencyPath(x1: number, y1: number, x2: number, y2: number): string {
  const handle = Math.max(18, Math.min(48, Math.abs(x2 - x1) / 2))
  return `M${x1},${y1} C${x1 + handle},${y1} ${x2 - handle},${y2} ${x2},${y2}`
}

export function GovernanceDependencyLayer({ model }: { model: GanttModel }) {
  const rowById = new Map(model.rows.map((row, index) => [row.plan.id, { row, index }]))

  return <svg
    aria-hidden
    width={model.range.totalDays * GANTT_DAY_WIDTH}
    height={model.rows.length * GANTT_ROW_HEIGHT}
    style={{ position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'visible' }}
  >
    <defs>
      <marker id="governance-gantt-arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
        <path d="M0,0 L6,3 L0,6 Z" fill="#66736d" />
      </marker>
    </defs>
    {model.connections.map(connection => {
      const from = rowById.get(connection.fromPlanId)
      const to = rowById.get(connection.toPlanId)
      if (!from || !to) return null
      const x1 = (from.row.offsetDays + from.row.durationDays) * GANTT_DAY_WIDTH
      const x2 = to.row.offsetDays * GANTT_DAY_WIDTH
      const y1 = from.index * GANTT_ROW_HEIGHT + GANTT_ROW_HEIGHT / 2
      const y2 = to.index * GANTT_ROW_HEIGHT + GANTT_ROW_HEIGHT / 2

      return <path
        key={`${connection.fromPlanId}-${connection.toPlanId}`}
        data-testid={`dependency-${connection.fromPlanId}-${connection.toPlanId}`}
        d={buildDependencyPath(x1, y1, x2, y2)}
        fill="none"
        stroke="#66736d"
        strokeWidth="1.5"
        markerEnd="url(#governance-gantt-arrow)"
      />
    })}
  </svg>
}
