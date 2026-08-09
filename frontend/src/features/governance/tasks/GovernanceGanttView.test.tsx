import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { GovernancePlan } from '../types'
import { buildDependencyPath } from './governanceDependencyPath'
import { GovernanceGanttView } from './GovernanceGanttView'

const plans: GovernancePlan[] = [
  { id: 1, taskId: 9, title: '清洗字段', status: 'DONE', plannedStart: '2026-07-27', plannedEnd: '2026-07-28', plannedQuantity: 2, completedQuantity: 2, quantityUnit: '字段', responsibleUserId: 'u-1', dependencyIds: [] },
  { id: 2, taskId: 9, title: '业务复核', status: 'NOT_STARTED', plannedStart: '2026-07-29', plannedEnd: '2026-07-30', plannedQuantity: 2, completedQuantity: 1, quantityUnit: '字段', responsibleUserId: 'u-2', dependencyIds: [1] },
]

describe('GovernanceGanttView', () => {
  it.each([
    ['normal schedules', 72, 144, 'M72,28 C108,28 108,84 144,84'],
    ['adjacent schedules', 108, 108, 'M108,28 C126,28 90,84 108,84'],
    ['overlapping schedules', 180, 144, 'M180,28 C198,28 126,84 144,84'],
    ['reverse-date schedules', 216, 108, 'M216,28 C264,28 60,84 108,84'],
  ])('approaches the target from left to right for %s', (_name, x1, x2, expected) => {
    expect(buildDependencyPath(x1, 28, x2, 84)).toBe(expected)
  })

  it('shows timeline rows, progress, milestones, and dependencies', () => {
    render(<GovernanceGanttView
      plans={plans}
      employees={[
        { id: 'u-1', name: '王工', department: '数据部', source: 'dev' },
        { id: 'u-2', name: '李工', department: '业务部', source: 'dev' },
      ]}
      today="2026-07-27"
    />)

    expect(screen.getByRole('img', { name: /清洗字段.*2026-07-27.*2026-07-28.*100%/ })).toBeVisible()
    expect(screen.getByRole('img', { name: /业务复核.*50%/ })).toBeVisible()
    expect(screen.getByText('王工')).toBeVisible()
    expect(screen.getByTestId('dependency-1-2')).toBeInTheDocument()
  })

  it('shows an empty state without plans', () => {
    render(<GovernanceGanttView plans={[]} employees={[]} today="2026-07-27" />)

    expect(screen.getByText('尚未编排计划项')).toBeVisible()
  })

  it('keeps valid rows and reports invalid schedules', () => {
    render(<GovernanceGanttView
      plans={[...plans, { ...plans[0], id: 3, title: '缺少排期', plannedStart: undefined, plannedEnd: undefined }]}
      employees={[]}
      today="2026-07-27"
    />)

    expect(screen.getByText(/缺少排期/)).toBeVisible()
    expect(screen.getByRole('img', { name: /清洗字段/ })).toBeVisible()
  })

  it('reports an unknown dependency without drawing a false path', () => {
    render(<GovernanceGanttView plans={[{ ...plans[0], dependencyIds: [999] }]} employees={[]} today="2026-07-27" />)

    expect(screen.getByText('依赖数据异常：999')).toBeVisible()
    expect(screen.queryByTestId('dependency-999-1')).not.toBeInTheDocument()
  })

})
