import type { GovernanceProgress, GovernanceTaskDetail, GovernanceTaskStatus } from '../types'

export type GovernanceWorkflowStepState = 'pending' | 'active' | 'done' | 'error'

export interface GovernanceWorkflowStep {
  key: 'planning' | 'processing' | 'confirmation' | 'acceptance' | 'application' | 'ai-ready'
  label: string
  detail: string
  state: GovernanceWorkflowStepState
}

export interface GovernanceWorkflowModel {
  kind: 'closed-loop' | 'legacy'
  title: string
  summary: string
  steps: GovernanceWorkflowStep[]
}

export type GovernanceWorkflowInput = Pick<GovernanceTaskDetail,
  'status' | 'workflowVersion' | 'progress' | 'currentRound' | 'completed' | 'total'>

const statusStep: Record<Exclude<GovernanceTaskStatus, 'REWORK_REQUIRED' | 'COMPLETED'>, number> = {
  DRAFT: 0,
  IN_PROGRESS: 1,
  PENDING_CONFIRMATION: 2,
  PENDING_ACCEPTANCE: 3,
}

function quantityDetail(role: string, label: string, value: number, progress?: GovernanceProgress | null): string {
  return progress ? `${role} · ${label} ${value}/${progress.total}` : role
}

export function buildGovernanceWorkflow(input: GovernanceWorkflowInput): GovernanceWorkflowModel {
  if (input.workflowVersion === 'LEGACY_PROGRESS') {
    return {
      kind: 'legacy',
      title: '历史汇总任务',
      summary: `仅保留汇总进度 ${input.completed}/${input.total}，不推断资料处理、确认或验收节点。`,
      steps: [],
    }
  }

  const progress = input.progress
  const applicationStarted = input.status === 'PENDING_ACCEPTANCE'
    && Boolean(progress?.total)
    && progress!.accepted >= progress!.total
  const current = input.status === 'COMPLETED'
    ? 5
    : input.status === 'REWORK_REQUIRED'
      ? 1
      : applicationStarted
        ? 4
        : statusStep[input.status]
  const reworking = input.status === 'REWORK_REQUIRED'
  const completed = input.status === 'COMPLETED'
  const definitions = [
    { key: 'planning', label: '任务编排', detail: '内容管理员 · 范围、责任与排期' },
    { key: 'processing', label: '资料处理', detail: quantityDetail('执行员工', '已提交', progress?.submitted ?? 0, progress) },
    { key: 'confirmation', label: '业务确认', detail: quantityDetail('资产责任人', '已确认', progress?.confirmed ?? 0, progress) },
    { key: 'acceptance', label: '质量验收', detail: quantityDetail('验收责任人', '已验收', progress?.accepted ?? 0, progress) },
    { key: 'application', label: '标准化入库', detail: '系统 · 验收通过后正式应用' },
    { key: 'ai-ready', label: 'AI 就绪', detail: '公司资产 · 可进入 RAG 语料加工' },
  ] as const
  const steps = definitions.map((step, index): GovernanceWorkflowStep => ({
    ...step,
    state: completed || index < current
      ? 'done'
      : index === current
        ? reworking ? 'error' : 'active'
        : 'pending',
  }))

  return {
    kind: 'closed-loop',
    title: '数据资产治理闭环',
    summary: reworking
      ? `第 ${input.currentRound ?? 1} 轮：确认或验收退回，待重新处理。`
      : '资料处理包含按任务进行的征集、标注或清洗；平台完成标准化后形成 AI 就绪资产。',
    steps,
  }
}
