export type GovernanceTaskStatus =
  | 'DRAFT'
  | 'IN_PROGRESS'
  | 'PENDING_CONFIRMATION'
  | 'PENDING_ACCEPTANCE'
  | 'REWORK_REQUIRED'
  | 'COMPLETED'

export type GovernanceItemStatus =
  | 'PENDING'
  | 'PROCESSING'
  | 'SUBMITTED'
  | 'CONFIRMED'
  | 'ACCEPTED'
  | 'BLOCKED'
  | 'REWORK_REQUIRED'

export type GovernanceField = 'DESCRIPTION' | 'SPECIALTIES' | 'OWNER' | 'SCOPE'
export type GovernancePlanStatus = 'TODO' | 'NOT_STARTED' | 'IN_PROGRESS' | 'BLOCKED' | 'DONE'
export type GovernanceIssueStatus = 'OPEN' | 'CLAIMED' | 'RESOLVED'
export type GovernanceStandardStatus = 'DRAFT' | 'ENABLED' | 'DISABLED'
export type GovernanceStandardRuleType = 'REQUIRED' | 'NAMING' | 'CONTROLLED_VALUE' | 'FILE_ROLE' | 'QUALITY_THRESHOLD'
export type GovernanceAssetType = 'THREE_DIMENSIONAL_MODEL' | 'TWO_DIMENSIONAL_DRAWING' | 'MIXED_ASSET' | 'OTHER'
export type JsonValue = null | boolean | number | string | JsonValue[] | { [key: string]: JsonValue }

export interface GovernanceStandardRule {
  targetField: string
  ruleType: GovernanceStandardRuleType
  description: string
  blocking: boolean
  configurationJson: string
}

export interface GovernanceDataStandard {
  id: number
  standardCode: string
  standardVersion: number
  name: string
  status: GovernanceStandardStatus
  applicableAssetTypes: GovernanceAssetType[]
  ownerUserId: string
  ownerName: string
  effectiveAt: string | null
  changeSummary: string
  affectedAssetCount: number
  rules: GovernanceStandardRule[]
  version: number
  createdAt: string
  updatedAt: string
}

export interface GovernanceStandardImpactReview {
  id: number
  standardId: number
  affectedAssetCount: number
  assetIds: number[]
  status: 'OPEN' | 'COMPLETED'
  createdAt: string
}

export interface CreateGovernanceStandardVersionInput {
  standardVersion: number
  name: string
  applicableAssetTypes: GovernanceAssetType[]
  ownerUserId: string
  ownerName: string
  changeSummary: string
  rules: GovernanceStandardRule[]
}

export interface CreateGovernanceStandardInput extends CreateGovernanceStandardVersionInput {
  standardCode: string
}

export type GovernanceMappingStatus = 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'DISABLED'

export interface GovernanceAssetScope {
  platform: string
  productLine: string
  base: string
  productionLine: string
  processSection: string
  platformFamily: string
  platformVariant: string
}

export interface GovernanceMappingRule {
  id: number
  standardId: number
  standardCode: string
  standardVersion: number
  ruleVersion: number
  sourceDimension: string
  sourceValue: string
  targetDictionaryCategory: string
  targetDictionaryItemId: number
  targetCode: string
  targetName: string
  scope: GovernanceAssetScope
  ambiguous: boolean
  confirmationComment: string | null
  confirmedByUserId: string | null
  confirmedByName: string | null
  confirmedAt: string | null
  usageCount: number
  matchedAssetCount: number
  affectedAssetCount: number
  status: GovernanceMappingStatus
  version: number
  createdAt: string
  updatedAt: string
}

export type GovernanceScanTriggerType = 'MANUAL' | 'SCHEDULED' | 'RETRY'
export type GovernanceScanRunStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface GovernanceScanRun {
  id: number
  triggerType: GovernanceScanTriggerType
  status: GovernanceScanRunStatus
  startedAt: string
  finishedAt: string | null
  scannedAssetCount: number
  createdIssueCount: number
  reopenedIssueCount: number
  unchangedIssueCount: number
  errorMessage: string
  retryOfRunId: number | null
  version: number
}

export interface GovernanceOperationsFilter {
  standardCode?: string
  issueType?: string
  ownerUserId?: string
  assetType?: GovernanceAssetType
  base?: string
  fromDate?: string
  toDate?: string
}

export interface GovernanceOperationsMetric {
  key: string
  label: string
  value: number | null
  numerator: number
  denominator: number
  available: boolean
  unit: string
  source: string
}

export interface GovernanceOperationsOverview {
  filter: GovernanceOperationsFilter
  assetCount: number
  coveredAssetCount: number
  openIssueCount: number
  overdueTaskCount: number
  metrics: GovernanceOperationsMetric[]
  issuesByType: Array<{ key: string; count: number }>
  overdueTasks: Array<{ taskId: number; taskName: string; ownerName: string; dueDate: string; status: GovernanceTaskStatus }>
  cadences: Array<{ key: string; name: string; ownerRole: string; status: string; nextDueAt: string; evidence: string }>
  generatedAt: string
}

export interface GovernanceProgress {
  total: number
  submitted: number
  confirmed: number
  accepted: number
  blocked: number
  reworkRequired: number
}

export interface GovernanceRuleSnapshot {
  id: number
  dataStandardId: string
  dataStandardVersion: number
  fieldRuleVersion: number
  dictionaryVersions: Record<string, number>
  qualityPolicyId: string
  qualityPolicyVersion: number
}

export interface GovernanceScopeSnapshot {
  id: number
  taskId: number
  claimedIssueIds: number[]
  assetIds: number[]
  ruleSnapshot: GovernanceRuleSnapshot
  createdBy: string
  frozenAt: string
  itemCount: number
}

export interface GovernanceIssue {
  id: number
  assetId: number
  targetField: GovernanceField
  issueType: string
  targetPath: string
  originalFactJson: string
  severity: string
  blocking: boolean
  status: GovernanceIssueStatus
  taskId: number | null
  version: number
  createdAt: string
  updatedAt: string
}

export interface GovernancePlan {
  id: number
  taskId: number
  title: string
  status: GovernancePlanStatus
  completedAt?: string
  plannedStart?: string
  plannedEnd?: string
  actualStart?: string
  actualEnd?: string
  plannedQuantity: number
  completedQuantity: number
  quantityUnit: string
  assigneeId?: string
  responsibleUserId?: string
  dependencyIds: number[]
  issueIds?: number[]
}

export interface GovernancePlanProjection {
  plan: GovernancePlan
  status: GovernancePlanStatus
  completedQuantity: number
}

export interface GovernanceTaskDetail {
  id: number
  name: string
  scope: string
  owner: string
  assigneeId?: string
  total: number
  completed: number
  dueDate: string
  status: GovernanceTaskStatus
  workflowVersion?: string
  currentRound?: number
  version?: number
  editable?: boolean
  progress?: GovernanceProgress | null
  riskCount?: number
  plans?: GovernancePlan[]
  scopeSnapshot?: GovernanceScopeSnapshot | null
  ruleSnapshot?: GovernanceRuleSnapshot | null
  workbenchEntries?: Record<string, string>
}

export type GovernanceTask = GovernanceTaskDetail

export interface GovernanceItem {
  id: number
  taskId: number
  planId: number
  issueId: number
  assetId: number
  targetField: GovernanceField
  actionType: string
  responsibleUserId: string
  status: GovernanceItemStatus
  assetVersion: number
  governanceRound: number
  scopeFingerprint: string
  version: number
  currentResultVersionId: number | null
  blockReason: string | null
  reworkSourceItemId: number | null
}

export interface GovernanceResultVersion {
  id: number
  itemId: number
  governanceRound: number
  resultVersion: number
  field: GovernanceField
  originalValueJson: string
  proposedValue: JsonValue
  standardVersion: number
  dictionaryVersions: Record<string, number>
  status: 'DRAFT' | 'SUBMITTED' | 'SUPERSEDED' | 'APPLIED'
  reworkReason: string
  actorUserId: string
  savedAt: string
  submittedAt: string | null
  version: number
}

export interface GovernanceItemExecution {
  item: GovernanceItem
  currentResult: GovernanceResultVersion | null
  originalFactJson: string
  ruleContext: JsonValue
  blockReason: string | null
  reworkSourceItemId: number | null
}

export interface ConfirmationRound {
  id: number
  taskId: number
  governanceRound: number
  resultVersionIds: Record<string, number>
  status: 'PENDING' | 'COMPLETED'
  createdAt: string
  completedAt: string | null
  version: number
}

export interface ConfirmationView {
  round: ConfirmationRound
  items: ConfirmationItem[]
  decisions: ConfirmationDecision[]
  coveredCount: number
  approvedCount: number
  coverageRate: number
  approvalRate: number
}

export interface ConfirmationItem {
  itemId: number
  assetId: number
  resultVersionId: number
  resultType: string
  responsibleUserId: string
  responsibilityScope: string
}

export interface ConfirmationDecision {
  id: number
  roundId: number
  itemId: number
  resultVersionId: number
  decision: 'APPROVED' | 'REJECTED'
  comment: string
  confirmerUserId: string
  decidedAt: string
  version: number
}

export type GovernanceQualityMetric = 'REQUIRED_FIELD_COMPLETENESS' | 'ASSET_SCOPE_VALIDITY' | 'STANDARD_DICTIONARY_HIT_RATE' | 'OWNER_COVERAGE' | 'SAMPLE_ACCURACY'

export interface AcceptanceMetricResult {
  id: number
  roundId: number
  metric: GovernanceQualityMetric
  numerator: number
  denominator: number
  value: number | null
  threshold: number
  applicability: 'APPLICABLE' | 'NOT_APPLICABLE'
  passed: boolean
  affectedItemIds: number[]
  version: number
}

export interface AcceptanceSample {
  id: number
  roundId: number
  itemId: number
  passed: boolean | null
  issueDescription: string
  reviewerUserId: string
  checkedAt: string | null
  version: number
}

export interface AcceptanceRound {
  id: number
  taskId: number
  governanceRound: number
  policy: JsonValue
  metricResults: AcceptanceMetricResult[]
  samples: AcceptanceSample[]
  status: 'OPEN' | 'PASSED' | 'FAILED'
  createdAt: string
  completedAt: string | null
  version: number
}

export interface OperationJob {
  jobId: number
  taskId: number
  total: number
  succeeded: number
  failed: number
  processing: number
  errors: Record<string, string>
  retryable: boolean
}

export interface BatchItemResult {
  itemId: number
  outcome: 'SUCCESS' | 'VALIDATION_FAILED' | 'CONFLICT'
  resultVersionId?: number
  errorCode?: string
  message?: string
  currentVersion?: number
}

export interface BatchResultCommand {
  itemId: number
  itemVersion: number
  assetVersion: number
  proposedValue: JsonValue
  submit: boolean
  targetField?: GovernanceField
  standardVersion?: number
  scopeFingerprint?: string
  actorUserId?: string
}

export interface GovernanceEmployee {
  id: string
  name: string
  department: string
  source: string
}

export interface GovernanceApiErrorDetail {
  field: string
  message: string
}

export class GovernanceApiError extends Error {
  readonly status: number
  readonly code: string
  readonly details: GovernanceApiErrorDetail[]

  constructor(
    status: number,
    code: string,
    message: string,
    details: GovernanceApiErrorDetail[],
  ) {
    super(message)
    this.name = 'GovernanceApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

export type CreateGovernancePlanInput = Omit<
  GovernancePlan,
  'id' | 'taskId' | 'status' | 'completedAt' | 'completedQuantity'
>
