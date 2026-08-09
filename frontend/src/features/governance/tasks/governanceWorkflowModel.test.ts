import { describe, expect, it } from 'vitest'
import type { GovernanceTaskStatus } from '../types'
import { buildGovernanceWorkflow } from './governanceWorkflowModel'

const base = {
  workflowVersion: 'CLOSED_LOOP_V1',
  completed: 0,
  total: 4,
  progress: { total: 4, submitted: 2, confirmed: 1, accepted: 0, blocked: 0, reworkRequired: 0 },
  currentRound: 1,
} as const

describe('buildGovernanceWorkflow', () => {
  it.each([
    ['DRAFT', '任务编排'],
    ['IN_PROGRESS', '资料处理'],
    ['PENDING_CONFIRMATION', '业务确认'],
    ['PENDING_ACCEPTANCE', '质量验收'],
  ] satisfies Array<[GovernanceTaskStatus, string]>)('maps %s to the single active stage', (status, label) => {
    const model = buildGovernanceWorkflow({ ...base, status })

    expect(model.steps.filter(step => step.state === 'active')).toHaveLength(1)
    expect(model.steps.find(step => step.state === 'active')?.label).toBe(label)
  })

  it('moves to standardization application after every item is accepted', () => {
    const model = buildGovernanceWorkflow({
      ...base,
      status: 'PENDING_ACCEPTANCE',
      progress: { ...base.progress, submitted: 4, confirmed: 4, accepted: 4 },
    })

    expect(model.steps.find(step => step.state === 'active')?.label).toBe('标准化入库')
  })

  it('uses a neutral rework source when the backend does not provide one', () => {
    const model = buildGovernanceWorkflow({ ...base, status: 'REWORK_REQUIRED', currentRound: 2 })

    expect(model.summary).toBe('第 2 轮：确认或验收退回，待重新处理。')
    expect(model.steps.find(step => step.state === 'error')?.label).toBe('资料处理')
  })

  it('does not invent closed-loop stages for a legacy task', () => {
    const model = buildGovernanceWorkflow({
      status: 'PENDING_CONFIRMATION',
      workflowVersion: 'LEGACY_PROGRESS',
      completed: 421,
      total: 421,
      progress: null,
    })

    expect(model.kind).toBe('legacy')
    expect(model.steps).toEqual([])
    expect(model.summary).toContain('不推断资料处理、确认或验收节点')
  })

  it('marks the whole closed loop done without claiming RAG synchronization', () => {
    const model = buildGovernanceWorkflow({ ...base, status: 'COMPLETED' })

    expect(model.steps.every(step => step.state === 'done')).toBe(true)
    expect(model.steps.at(-1)).toMatchObject({ label: 'AI 就绪', detail: expect.stringContaining('可进入 RAG') })
  })
})
