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
    render(<GovernanceGanttView
      plans={plans}
      employees={[
        { id: 'u-1', name: '王工', department: '数据部', source: 'dev' },
        { id: 'u-2', name: '李工', department: '业务部', source: 'dev' },
      ]}
      taskStatus="PENDING_CONFIRMATION"
      today="2026-07-27"
    />)

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
    render(<GovernanceGanttView
      plans={[...plans, { ...plans[0], id: 3, title: '缺少排期', plannedStart: undefined, plannedEnd: undefined }]}
      employees={[]}
      taskStatus="IN_PROGRESS"
      today="2026-07-27"
    />)

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
