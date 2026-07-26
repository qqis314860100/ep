import type {
  AcceptanceRound,
  BatchItemResult,
  BatchResultCommand,
  ConfirmationView,
  CreateGovernancePlanInput,
  GovernanceApiErrorDetail,
  GovernanceEmployee,
  GovernanceField,
  GovernanceIssue,
  GovernanceIssueStatus,
  GovernanceItemExecution,
  GovernancePlan,
  GovernancePlanStatus,
  GovernanceResultVersion,
  GovernanceTask,
  GovernanceTaskDetail,
  GovernanceTaskStatus,
  JsonValue,
  OperationJob,
} from './types'
import { GovernanceApiError } from './types'

export type * from './types'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

interface ErrorEnvelope {
  error?: {
    code?: string
    message?: string
    details?: GovernanceApiErrorDetail[]
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    let envelope: ErrorEnvelope = {}
    try {
      envelope = await response.json() as ErrorEnvelope
    } catch {
      // An empty or non-JSON response still becomes a typed API error.
    }
    throw new GovernanceApiError(
      response.status,
      envelope.error?.code ?? 'governance_request_failed',
      envelope.error?.message ?? `治理请求失败：${response.status}`,
      envelope.error?.details ?? [],
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function queryString(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== '') params.set(key, String(value))
  })
  const query = params.toString()
  return query ? `?${query}` : ''
}

export function getGovernanceIssues(filters: {
  field?: GovernanceField
  status?: GovernanceIssueStatus
  assetId?: number
} = {}): Promise<GovernanceIssue[]> {
  return request(`/api/v1/governance/issues${queryString(filters)}`)
}

export function getGovernanceTasks(filters: {
  status?: GovernanceTaskStatus
  ownerUserId?: string
  dueBefore?: string
  field?: GovernanceField
  scopeFingerprint?: string
} = {}): Promise<GovernanceTask[]> {
  return request(`/api/v1/governance/tasks${queryString(filters)}`)
}

export function getGovernanceTask(taskId: number): Promise<GovernanceTaskDetail> {
  return request(`/api/v1/governance/tasks/${taskId}`)
}

export function createGovernanceTask(input: {
  name: string
  issueIds?: number[]
  ownerUserId?: string
  ownerName?: string
  dueDate: string
  scope?: string
  owner?: string
  total?: number
  assigneeId?: string
}): Promise<GovernanceTask> {
  return request('/api/v1/governance/tasks', { method: 'POST', body: JSON.stringify(input) })
}

export function getGovernanceEmployees(): Promise<GovernanceEmployee[]> {
  return request('/api/v1/governance/tasks/employees')
}

export function getGovernancePlans(taskId: number): Promise<GovernancePlan[]> {
  return request(`/api/v1/governance/tasks/${taskId}/plans`)
}

export function createGovernancePlan(
  taskId: number,
  input: CreateGovernancePlanInput,
): Promise<GovernancePlan> {
  return request(`/api/v1/governance/tasks/${taskId}/plans`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateGovernancePlan(
  taskId: number,
  planId: number,
  status: GovernancePlanStatus,
): Promise<GovernancePlan> {
  return request(`/api/v1/governance/tasks/${taskId}/plans/${planId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function startGovernanceTask(
  taskId: number,
  input: { version: number; actorUserId: string } = { version: 0, actorUserId: 'demo-user' },
): Promise<GovernanceTask> {
  return request(`/api/v1/governance/tasks/${taskId}/start`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateGovernanceProgress(taskId: number, completed: number): Promise<GovernanceTask> {
  return request(`/api/v1/governance/tasks/${taskId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ completed }),
  })
}

export function getGovernanceItems(taskId: number): Promise<GovernanceItemExecution[]> {
  return request(`/api/v1/governance/tasks/${taskId}/items`)
}

export function saveResultDraft(itemId: number, command: {
  itemVersion: number
  assetVersion: number
  proposedValue: JsonValue
  actorUserId: string
}): Promise<GovernanceResultVersion> {
  return request(`/api/v1/governance/items/${itemId}/result-draft`, {
    method: 'PUT',
    body: JSON.stringify(command),
  })
}

export function submitResult(itemId: number, command: {
  resultVersionId: number
  resultVersion: number
  actorUserId: string
}): Promise<GovernanceResultVersion> {
  return request(`/api/v1/governance/items/${itemId}/submit`, {
    method: 'POST',
    body: JSON.stringify(command),
  })
}

export function saveBatchResults(
  idempotencyKey: string,
  commands: BatchResultCommand[],
): Promise<{ results: BatchItemResult[] }> {
  return request('/api/v1/governance/results/batch', {
    method: 'POST',
    body: JSON.stringify({ idempotencyKey, commands }),
  })
}

export function submitForConfirmation(taskId: number, version: number): Promise<GovernanceTask> {
  return request(`/api/v1/governance/tasks/${taskId}/submit-for-confirmation`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })
}

export function getCurrentConfirmation(taskId: number): Promise<ConfirmationView> {
  return request(`/api/v1/governance/tasks/${taskId}/confirmation-rounds/current`)
}

export function saveConfirmationDecision(roundId: number, itemId: number, command: {
  decision: 'APPROVED' | 'REJECTED'
  comment?: string
  decisionVersion: number
  confirmerUserId: string
}): Promise<ConfirmationView> {
  return request(`/api/v1/governance/confirmation-rounds/${roundId}/items/${itemId}/decision`, {
    method: 'PUT',
    body: JSON.stringify(command),
  })
}

export function completeConfirmation(taskId: number, roundId: number, roundVersion: number): Promise<JsonValue> {
  return request(`/api/v1/governance/tasks/${taskId}/confirmation-rounds/${roundId}/complete`, {
    method: 'POST',
    body: JSON.stringify({ roundVersion }),
  })
}

export function getCurrentAcceptance(taskId: number): Promise<AcceptanceRound> {
  return request(`/api/v1/governance/tasks/${taskId}/acceptance-rounds/current`)
}

export function saveAcceptanceSample(roundId: number, itemId: number, command: {
  passed: boolean
  issueDescription?: string
  reviewerUserId: string
  sampleVersion: number
}): Promise<JsonValue> {
  return request(`/api/v1/governance/acceptance-rounds/${roundId}/samples/${itemId}`, {
    method: 'PUT',
    body: JSON.stringify(command),
  })
}

export function completeAcceptance(taskId: number, roundId: number, command: {
  roundVersion: number
  operatorUserId: string
}): Promise<JsonValue> {
  return request(`/api/v1/governance/tasks/${taskId}/acceptance-rounds/${roundId}/complete`, {
    method: 'POST',
    body: JSON.stringify(command),
  })
}

export function openGovernanceRework(taskId: number, command: {
  taskVersion: number
  reason: string
  actorUserId: string
}): Promise<GovernanceTask> {
  return request(`/api/v1/governance/tasks/${taskId}/rework`, {
    method: 'POST',
    body: JSON.stringify(command),
  })
}

export function getGovernanceHistory(taskId: number): Promise<JsonValue[]> {
  return request(`/api/v1/governance/tasks/${taskId}/history`)
}

export function getGovernanceReport(taskId: number): Promise<JsonValue> {
  return request(`/api/v1/governance/tasks/${taskId}/report`)
}

export function getOperationJob(jobId: number): Promise<OperationJob> {
  return request(`/api/v1/governance/jobs/${jobId}`)
}

export function retryOperationJob(jobId: number): Promise<OperationJob> {
  return request(`/api/v1/governance/jobs/${jobId}/retry`, { method: 'POST' })
}
