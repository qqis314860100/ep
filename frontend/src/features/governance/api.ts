import type {
  AcceptanceRound,
  BatchItemResult,
  BatchResultCommand,
  ConfirmationView,
  CreateGovernancePlanInput,
  CreateGovernanceStandardInput,
  CreateGovernanceStandardVersionInput,
  GovernanceApiErrorDetail,
  GovernanceDataStandard,
  GovernanceEmployee,
  GovernanceField,
  GovernanceIssue,
  GovernanceIssueStatus,
  GovernanceItemExecution,
  GovernanceMappingRule,
  GovernanceMappingStatus,
  GovernanceScanRun,
  GovernancePlan,
  GovernancePlanProjection,
  GovernancePlanStatus,
  GovernanceResultVersion,
  GovernanceStandardImpactReview,
  GovernanceTask,
  GovernanceTaskDetail,
  GovernanceTaskStatus,
  JsonValue,
  OperationJob,
} from './types'
import { GovernanceApiError } from './types'

export type * from './types'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
const governanceIdentity = {
  userId: 'demo-user',
  roles: 'CONTENT_ADMIN,SYSTEM_ADMIN',
}

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
      'X-User-Id': governanceIdentity.userId,
      'X-User-Roles': governanceIdentity.roles,
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
  return request<GovernancePlanProjection[]>(`/api/v1/governance/tasks/${taskId}/plans`).then(items => items.map(({ plan, status, completedQuantity }) => ({
    ...plan,
    status,
    completedQuantity,
  })))
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

export function getGovernanceStandards(): Promise<GovernanceDataStandard[]> {
  return request('/api/v1/governance/standards')
}

export function createGovernanceStandard(
  input: CreateGovernanceStandardInput,
): Promise<GovernanceDataStandard> {
  return request('/api/v1/governance/standards', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function createGovernanceStandardVersion(
  sourceId: number,
  input: CreateGovernanceStandardVersionInput,
): Promise<GovernanceDataStandard> {
  return request(`/api/v1/governance/standards/${sourceId}/versions`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function enableGovernanceStandard(
  id: number,
  version: number,
): Promise<{ standard: GovernanceDataStandard; impactReview: GovernanceStandardImpactReview }> {
  return request(`/api/v1/governance/standards/${id}/enable`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })
}

export function disableGovernanceStandard(id: number, version: number): Promise<GovernanceDataStandard> {
  return request(`/api/v1/governance/standards/${id}/disable`, {
    method: 'POST',
    body: JSON.stringify({ version }),
  })
}

export function getGovernanceStandardImpactReviews(
  id: number,
): Promise<GovernanceStandardImpactReview[]> {
  return request(`/api/v1/governance/standards/${id}/impact-reviews`)
}

export function getGovernanceMappings(filters: {
  status?: GovernanceMappingStatus
  sourceDimension?: string
  query?: string
} = {}): Promise<GovernanceMappingRule[]> {
  const params = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
  const suffix = params.toString() ? `?${params.toString()}` : ''
  return request(`/api/v1/governance/mappings${suffix}`)
}

export function createGovernanceMapping(input: {
  standardId: number
  sourceDimension: string
  sourceValue: string
  targetDictionaryCategory: string
  targetDictionaryItemId: number
  scope: GovernanceMappingRule['scope']
  ambiguous: boolean
  affectedAssetCount: number
}): Promise<GovernanceMappingRule> {
  return request('/api/v1/governance/mappings', { method: 'POST', body: JSON.stringify(input) })
}

export function createGovernanceMappingVersion(id: number, input: {
  standardId: number
  standardVersion: number
  sourceDimension: string
  sourceValue: string
  targetDictionaryCategory: string
  targetDictionaryItemId: number
  scope: GovernanceMappingRule['scope']
  ambiguous: boolean
  affectedAssetCount: number
}): Promise<GovernanceMappingRule> {
  return request(`/api/v1/governance/mappings/${id}/versions`, { method: 'POST', body: JSON.stringify(input) })
}

export function confirmGovernanceMapping(id: number, input: { version: number; userId: string; userName: string; comment?: string }): Promise<GovernanceMappingRule> {
  return request(`/api/v1/governance/mappings/${id}/confirm`, { method: 'POST', body: JSON.stringify(input) })
}

export function disableGovernanceMapping(id: number, version: number): Promise<GovernanceMappingRule> {
  return request(`/api/v1/governance/mappings/${id}/disable`, { method: 'POST', body: JSON.stringify({ version }) })
}

export function getGovernanceMappingHistory(id: number): Promise<GovernanceMappingRule[]> {
  return request(`/api/v1/governance/mappings/${id}/history`)
}

export function getGovernanceScanRuns(): Promise<GovernanceScanRun[]> {
  return request('/api/v1/governance/scans')
}

export function triggerGovernanceScan(): Promise<GovernanceScanRun> {
  return request('/api/v1/governance/scans', { method: 'POST' })
}

export function retryGovernanceScan(id: number): Promise<GovernanceScanRun> {
  return request(`/api/v1/governance/scans/${id}/retry`, { method: 'POST' })
}
