# Governance Task Gantt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only task-level Gantt view to governance task details using existing plan dates, quantities, owners, dependencies, and workflow state.

**Architecture:** Keep all date and dependency calculations in a pure TypeScript model. Render the timeline with React, CSS Grid, and an SVG dependency overlay, then compose it into the existing plan editor behind a table/Gantt segmented control. Reuse the current governance APIs without backend or database changes.

**Tech Stack:** React 18, TypeScript 6 strict mode, Ant Design 5, styled-components, Vitest, Testing Library, pnpm.

---

## File Map

- Create `frontend/src/features/governance/tasks/governanceGanttModel.ts`: parse dates and calculate range, ticks, rows, risk states, and dependency geometry inputs.
- Create `frontend/src/features/governance/tasks/governanceGanttModel.test.ts`: pure model coverage.
- Create `frontend/src/features/governance/tasks/GovernanceDependencyLayer.tsx`: render dependency paths without owning layout or data fetching.
- Create `frontend/src/features/governance/tasks/GovernanceMilestoneStrip.tsx`: map task workflow status to milestone presentation.
- Create `frontend/src/features/governance/tasks/GovernanceGanttView.tsx`: render the fixed information column and horizontally scrollable timeline.
- Create `frontend/src/features/governance/tasks/GovernanceGanttView.test.tsx`: timeline, empty, invalid-date, milestone, and accessibility coverage.
- Modify `frontend/src/features/governance/types.ts`: align plan statuses with the backend contract while retaining legacy `TODO` compatibility.
- Modify `frontend/src/features/governance/tasks/GovernancePlanEditor.tsx`: add table/Gantt switching and explicit loading/error states.
- Modify `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx`: pass task and query state into the plan workspace.
- Modify `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`: cover switching and failed plan queries.
- Modify `requirement.md`: add the task Gantt behavior and acceptance scenario.

## Task 1: Build the Pure Gantt Model

**Files:**
- Create: `frontend/src/features/governance/tasks/governanceGanttModel.ts`
- Create: `frontend/src/features/governance/tasks/governanceGanttModel.test.ts`
- Modify: `frontend/src/features/governance/types.ts`

- [ ] **Step 1: Write failing model tests**

Create tests with a local plan factory and cover inclusive single-day width, padded range, progress clamping, overdue state, dependency blocking, weekly scale, invalid dates, and unknown dependency IDs:

```ts
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
```

- [ ] **Step 2: Run the model test and verify RED**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/governanceGanttModel.test.ts`

Expected: FAIL because `governanceGanttModel.ts` does not exist.

- [ ] **Step 3: Align the frontend plan-status contract**

Replace `GovernancePlanStatus` with the backend values plus legacy compatibility:

```ts
export type GovernancePlanStatus =
  | 'TODO'
  | 'NOT_STARTED'
  | 'IN_PROGRESS'
  | 'BLOCKED'
  | 'DONE'
```

- [ ] **Step 4: Implement the pure model**

Define the public model and pure calculations. Use UTC date-only arithmetic so Asia/Shanghai and daylight-saving environments produce the same day offsets:

```ts
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

function parseDate(value?: string): number | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const timestamp = Date.parse(`${value}T00:00:00Z`)
  return Number.isNaN(timestamp) || isoDate(timestamp) !== value ? null : timestamp
}

function isoDate(timestamp: number): string {
  return new Date(timestamp).toISOString().slice(0, 10)
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
  const invalidPlans = plans.filter(plan => !dated.some(item => item.plan.id === plan.id))
  if (dated.length === 0) {
    return { range: { start: '', end: '', totalDays: 0, scale: 'day' }, ticks: [], rows: [], invalidPlans, connections: [], todayOffset: null }
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
    const state: GanttRowState = completed ? 'DONE' : overdue ? 'OVERDUE' : blocked || plan.status === 'BLOCKED' ? 'BLOCKED' : plan.status === 'IN_PROGRESS' ? 'IN_PROGRESS' : 'NOT_STARTED'
    const rawPercent = plan.plannedQuantity > 0 ? plan.completedQuantity * 100 / plan.plannedQuantity : 0
    return { plan, offsetDays: daysBetween(rangeStart, start), durationDays: daysBetween(start, end) + 1, progressPercent: Math.round(Math.max(0, Math.min(100, rawPercent))), state, invalidDependencyIds }
  })
  const validIds = new Set(rows.map(row => row.plan.id))
  const connections = rows.flatMap(row => row.plan.dependencyIds.filter(id => validIds.has(id)).map(id => ({ fromPlanId: id, toPlanId: row.plan.id })))
  const step = scale === 'day' ? 1 : 7
  const ticks = Array.from({ length: Math.ceil(totalDays / step) }, (_, index) => {
    const offsetDays = index * step
    const date = isoDate(rangeStart + offsetDays * DAY_MS)
    return { date, offsetDays, label: scale === 'day' ? date.slice(5) : date }
  })
  const candidateTodayOffset = todayTimestamp === null ? -1 : daysBetween(rangeStart, todayTimestamp)
  return { range: { start: isoDate(rangeStart), end: isoDate(rangeEnd), totalDays, scale }, ticks, rows, invalidPlans, connections, todayOffset: candidateTodayOffset >= 0 && candidateTodayOffset < totalDays ? candidateTodayOffset : null }
}
```

- [ ] **Step 5: Run model tests and typecheck**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/governanceGanttModel.test.ts && rtk pnpm typecheck`

Expected: model test PASS and TypeScript reports no errors.

- [ ] **Step 6: Commit the model**

```bash
git add frontend/src/features/governance/types.ts frontend/src/features/governance/tasks/governanceGanttModel.ts frontend/src/features/governance/tasks/governanceGanttModel.test.ts
git commit -m "feat(前端): 建立治理甘特时间轴模型" -m "产品版本：V1.7 治理任务甘特图。"
```

## Task 2: Render the Gantt Timeline and Milestones

**Files:**
- Create: `frontend/src/features/governance/tasks/GovernanceDependencyLayer.tsx`
- Create: `frontend/src/features/governance/tasks/GovernanceMilestoneStrip.tsx`
- Create: `frontend/src/features/governance/tasks/GovernanceGanttView.tsx`
- Create: `frontend/src/features/governance/tasks/GovernanceGanttView.test.tsx`

- [ ] **Step 1: Write failing component tests**

Render the component with fixed `today="2026-07-27"` and assert real user-visible behavior:

```tsx
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { GovernancePlan } from '../types'
import { GovernanceGanttView } from './GovernanceGanttView'

const plans: GovernancePlan[] = [
  { id: 1, taskId: 9, title: '清洗字段', status: 'DONE', plannedStart: '2026-07-27', plannedEnd: '2026-07-28', plannedQuantity: 2, completedQuantity: 2, quantityUnit: '字段', responsibleUserId: 'u-1', dependencyIds: [] },
  { id: 2, taskId: 9, title: '业务复核', status: 'NOT_STARTED', plannedStart: '2026-07-29', plannedEnd: '2026-07-30', plannedQuantity: 2, completedQuantity: 1, quantityUnit: '字段', responsibleUserId: 'u-2', dependencyIds: [1] },
]

describe('GovernanceGanttView', () => {
  it('shows timeline rows, progress, milestones, and dependencies', () => {
    render(<GovernanceGanttView plans={plans} employees={[{ id: 'u-1', name: '王工', department: '数据部', source: 'dev' }, { id: 'u-2', name: '李工', department: '业务部', source: 'dev' }]} taskStatus="PENDING_CONFIRMATION" today="2026-07-27" />)
    expect(screen.getByRole('img', { name: /清洗字段.*2026-07-27.*2026-07-28.*100%/ })).toBeVisible()
    expect(screen.getByRole('img', { name: /业务复核.*50%/ })).toBeVisible()
    expect(screen.getByText('王工')).toBeVisible()
    expect(screen.getByText('业务确认')).toBeVisible()
    expect(screen.getByTestId('dependency-1-2')).toBeInTheDocument()
  })

  it('shows an empty state without plans', () => {
    render(<GovernanceGanttView plans={[]} employees={[]} taskStatus="DRAFT" today="2026-07-27" />)
    expect(screen.getByText('尚未编排计划项')).toBeVisible()
  })

  it('keeps valid rows and reports invalid schedules', () => {
    render(<GovernanceGanttView plans={[...plans, { ...plans[0], id: 3, title: '缺少排期', plannedStart: undefined, plannedEnd: undefined }]} employees={[]} taskStatus="IN_PROGRESS" today="2026-07-27" />)
    expect(screen.getByText(/缺少排期/)).toBeVisible()
    expect(screen.getByRole('img', { name: /清洗字段/ })).toBeVisible()
  })

  it('reports an unknown dependency without drawing a false path', () => {
    render(<GovernanceGanttView plans={[{ ...plans[0], dependencyIds: [999] }]} employees={[]} taskStatus="DRAFT" today="2026-07-27" />)
    expect(screen.getByText('依赖数据异常：999')).toBeVisible()
    expect(screen.queryByTestId('dependency-999-1')).not.toBeInTheDocument()
  })

  it('does not infer passed milestones while rework is required', () => {
    render(<GovernanceGanttView plans={plans} employees={[]} taskStatus="REWORK_REQUIRED" today="2026-07-27" />)
    expect(screen.getByText('返工中')).toBeVisible()
    expect(screen.getByText('质量验收').closest('[data-state]')).toHaveAttribute('data-state', 'pending')
  })
})
```

- [ ] **Step 2: Run the component test and verify RED**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/GovernanceGanttView.test.tsx`

Expected: FAIL because `GovernanceGanttView.tsx` does not exist.

- [ ] **Step 3: Implement milestone mapping**

Export a small component whose milestone mapping is explicit and exhaustive:

```tsx
import { CheckCircleOutlined, ClockCircleOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import { Tag } from 'antd'
import styled from 'styled-components'
import type { GovernanceTaskStatus } from '../types'

type MilestoneState = 'pending' | 'active' | 'done'
const milestones = ['计划锁定', '业务确认', '质量验收', '正式应用'] as const

function states(status: GovernanceTaskStatus): MilestoneState[] {
  if (status === 'DRAFT') return ['pending', 'pending', 'pending', 'pending']
  if (status === 'IN_PROGRESS' || status === 'REWORK_REQUIRED') return ['done', 'pending', 'pending', 'pending']
  if (status === 'PENDING_CONFIRMATION') return ['done', 'active', 'pending', 'pending']
  if (status === 'PENDING_ACCEPTANCE') return ['done', 'done', 'active', 'pending']
  return ['done', 'done', 'done', 'done']
}

const Strip = styled.div`display:flex; align-items:center; gap:8px; flex-wrap:wrap; min-height:32px;`

export function GovernanceMilestoneStrip({ status }: { status: GovernanceTaskStatus }) {
  const values = states(status)
  return <Strip aria-label="治理里程碑">
    {milestones.map((label, index) => {
      const state = values[index]
      const icon = state === 'done' ? <CheckCircleOutlined /> : <ClockCircleOutlined />
      return <Tag key={label} data-state={state} color={state === 'done' ? 'success' : state === 'active' ? 'processing' : 'default'} icon={icon}>{label}</Tag>
    })}
    {status === 'REWORK_REQUIRED' && <Tag color="error" icon={<ExclamationCircleOutlined />}>返工中</Tag>}
  </Strip>
}
```

- [ ] **Step 4: Implement dependency paths**

Use the same fixed constants as the timeline so no DOM measurement is required:

```tsx
import type { GanttModel } from './governanceGanttModel'

export const GANTT_DAY_WIDTH = 36
export const GANTT_ROW_HEIGHT = 56

export function GovernanceDependencyLayer({ model }: { model: GanttModel }) {
  const rowById = new Map(model.rows.map((row, index) => [row.plan.id, { row, index }]))
  return <svg aria-hidden width={model.range.totalDays * GANTT_DAY_WIDTH} height={model.rows.length * GANTT_ROW_HEIGHT} style={{ position: 'absolute', inset: 0, pointerEvents: 'none', overflow: 'visible' }}>
    <defs><marker id="governance-gantt-arrow" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto"><path d="M0,0 L6,3 L0,6 Z" fill="#77827d" /></marker></defs>
    {model.connections.map(connection => {
      const from = rowById.get(connection.fromPlanId)
      const to = rowById.get(connection.toPlanId)
      if (!from || !to) return null
      const x1 = (from.row.offsetDays + from.row.durationDays) * GANTT_DAY_WIDTH
      const x2 = to.row.offsetDays * GANTT_DAY_WIDTH
      const y1 = from.index * GANTT_ROW_HEIGHT + GANTT_ROW_HEIGHT / 2
      const y2 = to.index * GANTT_ROW_HEIGHT + GANTT_ROW_HEIGHT / 2
      const elbow = Math.max(x1 + 10, (x1 + x2) / 2)
      return <path key={`${connection.fromPlanId}-${connection.toPlanId}`} data-testid={`dependency-${connection.fromPlanId}-${connection.toPlanId}`} d={`M${x1},${y1} H${elbow} V${y2} H${x2}`} fill="none" stroke="#77827d" strokeWidth="1.5" markerEnd="url(#governance-gantt-arrow)" />
    })}
  </svg>
}
```

- [ ] **Step 5: Implement the timeline view**

Build `GovernanceGanttView` around `buildGanttModel`. Use `dayWidth=36`, `rowHeight=56`, a 260px information column, internal horizontal overflow, Ant Design `Alert`/`Empty` for invalid and empty states, and styled bar variants for `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `OVERDUE`, and `DONE`. Render the model ticks in a fixed-height header, render a one-pixel today line only when `todayOffset` is not null, and show `actualStart`/`actualEnd` as secondary text without moving the planned bar. Show `依赖数据异常：<ids>` in a row whenever `invalidDependencyIds` is non-empty. Each bar must use `role="img"` and this accessible label:

```tsx
const label = `${row.plan.title}，${row.plan.plannedStart} 至 ${row.plan.plannedEnd}，${ownerName}，完成 ${row.progressPercent}%，${stateLabels[row.state]}`
```

Render the timeline geometry from the model without recalculating business rules in JSX:

```tsx
<Bar
  role="img"
  aria-label={label}
  $state={row.state}
  style={{ left: row.offsetDays * GANTT_DAY_WIDTH, width: Math.max(GANTT_DAY_WIDTH, row.durationDays * GANTT_DAY_WIDTH) }}
>
  <Fill style={{ width: `${row.progressPercent}%` }} />
  <BarText>{row.progressPercent}%</BarText>
</Bar>
```

The timeline body must contain `GovernanceDependencyLayer` after the bars, and the top of the component must contain `GovernanceMilestoneStrip`.

- [ ] **Step 6: Run component and model tests**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/governanceGanttModel.test.ts src/features/governance/tasks/GovernanceGanttView.test.tsx`

Expected: both test files PASS.

- [ ] **Step 7: Commit the visual components**

```bash
git add frontend/src/features/governance/tasks/GovernanceDependencyLayer.tsx frontend/src/features/governance/tasks/GovernanceMilestoneStrip.tsx frontend/src/features/governance/tasks/GovernanceGanttView.tsx frontend/src/features/governance/tasks/GovernanceGanttView.test.tsx
git commit -m "feat(前端): 实现治理任务甘特视图" -m "产品版本：V1.7 治理任务甘特图。"
```

## Task 3: Integrate the Gantt View Into Task Details

**Files:**
- Modify: `frontend/src/features/governance/tasks/GovernancePlanEditor.tsx`
- Modify: `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx`
- Modify: `frontend/src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`

- [ ] **Step 1: Add failing task-detail behavior tests**

Extend the existing mock setup with two dated plans and add these tests:

```tsx
it('switches from the existing table to the Gantt view without changing data', async () => {
  const user = userEvent.setup()
  vi.mocked(governanceApi.getGovernancePlans).mockResolvedValue([
    { id: 1, taskId: 9, title: '字段清洗', status: 'IN_PROGRESS', plannedStart: '2026-07-27', plannedEnd: '2026-07-29', plannedQuantity: 4, completedQuantity: 2, quantityUnit: '字段', responsibleUserId: 'owner-1', dependencyIds: [] },
  ])
  renderPage()

  expect(await screen.findByRole('columnheader', { name: '计划项' })).toBeVisible()
  await user.click(screen.getByText('甘特图'))
  expect(screen.getByRole('img', { name: /字段清洗.*50%/ })).toBeVisible()
  expect(screen.queryByRole('columnheader', { name: '计划项' })).not.toBeInTheDocument()
})

it('shows a plan loading failure instead of an empty plan state', async () => {
  vi.mocked(governanceApi.getGovernancePlans).mockRejectedValue(new Error('计划服务不可用'))
  renderPage()
  expect(await screen.findByText('计划服务不可用')).toBeVisible()
  expect(screen.queryByText('尚未编排计划项')).not.toBeInTheDocument()
})

it('keeps plan editing locked after the task starts', async () => {
  vi.mocked(governanceApi.getGovernanceTask).mockResolvedValue({ id: 9, name: '字段治理', scope: '问题池选择', owner: '王工', total: 1, completed: 0, dueDate: '2026-08-10', status: 'IN_PROGRESS', editable: false })
  renderPage()
  expect(await screen.findByRole('button', { name: '编辑计划' })).toBeDisabled()
  expect(screen.getByText('任务启动后计划已锁定')).toBeVisible()
})
```

- [ ] **Step 2: Run the task-detail test and verify RED**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx`

Expected: FAIL because no segmented Gantt view or plan-query error state exists.

- [ ] **Step 3: Extend `GovernancePlanEditor` props and view state**

Use explicit props and keep table as the default:

```tsx
export function GovernancePlanEditor({ taskId, plans, issues, employees, editable, taskStatus, loading, error }: {
  taskId: number
  plans: GovernancePlan[]
  issues: GovernanceIssue[]
  employees: GovernanceEmployee[]
  editable: boolean
  taskStatus: GovernanceTaskStatus
  loading?: boolean
  error?: string
}) {
  const [view, setView] = useState<'table' | 'gantt'>('table')
```

Add an Ant Design `Segmented` control with `options={[{ label: '表格', value: 'table' }, { label: '甘特图', value: 'gantt' }]}`. When the edit button opens the form, call `setView('table')` so edits always remain in the established form. Below the form, render exactly one state:

```tsx
{loading ? <Skeleton active paragraph={{ rows: 3 }} />
  : error ? <Alert type="error" showIcon message={error} />
  : view === 'table' ? <Table rowKey="id" size="small" columns={columns} dataSource={plans} pagination={false} locale={{ emptyText: '尚未编排计划项' }} />
  : <GovernanceGanttView plans={plans} employees={employees} taskStatus={taskStatus} />}
```

- [ ] **Step 4: Pass task and query state from `GovernanceTaskDetailPage`**

Replace the existing plan editor call with:

```tsx
<GovernancePlanEditor
  taskId={taskId}
  plans={plansQuery.data ?? []}
  issues={issuesQuery.data ?? []}
  employees={employeesQuery.data ?? []}
  editable={editable}
  taskStatus={task.status}
  loading={plansQuery.isLoading}
  error={plansQuery.error instanceof Error ? plansQuery.error.message : undefined}
/>
```

- [ ] **Step 5: Run focused and all governance frontend tests**

Run: `cd frontend && rtk pnpm test -- src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx src/features/governance/tasks/GovernanceGanttView.test.tsx src/features/governance/tasks/governanceGanttModel.test.ts`

Expected: focused tests PASS.

Run: `cd frontend && rtk pnpm test -- src/features/governance`

Expected: all governance frontend tests PASS.

- [ ] **Step 6: Run lint and typecheck**

Run: `cd frontend && rtk pnpm lint && rtk pnpm typecheck`

Expected: both commands PASS.

- [ ] **Step 7: Commit task-detail integration**

```bash
git add frontend/src/features/governance/tasks/GovernancePlanEditor.tsx frontend/src/features/governance/tasks/GovernanceTaskDetailPage.tsx frontend/src/features/governance/tasks/GovernanceTaskDetailPage.test.tsx
git commit -m "feat(前端): 接入治理任务甘特工作区" -m "产品版本：V1.7 治理任务甘特图。"
```

## Task 4: Acceptance Documentation and Browser Verification

**Files:**
- Modify: `requirement.md`

- [ ] **Step 1: Add the product behavior and acceptance scenario**

After the GOVERN-06 plan-locking rules, add:

```markdown
- 任务详情支持在表格和甘特图之间切换；甘特图按计划起止日期展示责任人、完成比例、逾期、阻塞和同任务前置依赖。
- 甘特图进度来自治理项状态聚合，不能在图上手工拖动改期或覆盖进度；任务启动后保持只读。
- 业务确认、质量验收和正式应用以工作流里程碑展示；没有业务日期的里程碑不得伪造时间轴日期。
```

Append this acceptance row after AC-44:

```markdown
| AC-45 | 治理任务甘特图 | 可在任务详情切换表格与甘特图，真实展示计划排期、责任人、完成比例、逾期、阻塞和前置依赖；启动后不能通过甘特图改期 |
```

- [ ] **Step 2: Run complete frontend verification**

Run: `cd frontend && rtk pnpm test && rtk pnpm lint && rtk pnpm typecheck && rtk pnpm build`

Expected: all tests PASS; lint and typecheck report no errors; Vite production build succeeds.

- [ ] **Step 3: Start the default in-memory backend and frontend**

Run in separate terminals:

```bash
cd backend && rtk mvn spring-boot:run
cd frontend && rtk pnpm dev --host 127.0.0.1
```

Expected: backend uses the default `dev` profile on port 8080; Vite prints a local URL. Do not configure `local` or `oceanbase`.

- [ ] **Step 4: Verify the browser workflow at 1366x768**

Open the governance overview, enter a seeded task detail, and verify:

1. The plan table remains the default.
2. “甘特图” switches to the timeline without a network request.
3. Plan bars show dates, owners, completion, status text, and dependency paths.
4. The page has no horizontal overflow; only the timeline scrolls horizontally.
5. Switching back preserves the original table and draft-edit lock state.

- [ ] **Step 5: Verify the browser workflow at 1920x1080**

Repeat the same task detail and confirm the wider timeline displays additional dates, bars and dependency paths remain aligned, and labels do not overlap.

- [ ] **Step 6: Review the relevant diff**

Run:

```bash
git diff --check
rtk git diff -- frontend/src/features/governance/tasks frontend/src/features/governance/types.ts requirement.md
git status --short
```

Expected: no whitespace errors, no generated browser/build output, and only the documented frontend/requirement files are modified.

- [ ] **Step 7: Commit the acceptance contract**

```bash
git add requirement.md
git commit -m "docs(需求): 增加治理甘特图验收标准" -m "产品版本：V1.7 治理任务甘特图。"
```

- [ ] **Step 8: Confirm final repository state**

Run: `rtk git status --short --branch`

Expected: clean worktree. Report the three frontend commits, one documentation commit, verification results, browser viewports, and any remaining human confirmation.
