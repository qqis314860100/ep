export type GovernanceTaskStatus = 'DRAFT' | 'IN_PROGRESS' | 'PENDING_CONFIRMATION' | 'COMPLETED'
export type GovernancePlanStatus = 'TODO' | 'IN_PROGRESS' | 'DONE'

export interface GovernanceEmployee {
  id: string
  name: string
  department: string
  source: 'OFFICE_DIRECTORY'
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
  dependencyIds: number[]
}

export interface GovernanceTask {
  id: number
  name: string
  scope: string
  owner: string
  assigneeId?: string
  total: number
  completed: number
  dueDate: string
  status: GovernanceTaskStatus
}

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
const useMocks = import.meta.env.VITE_USE_MOCKS !== 'false'

const mockTasks: GovernanceTask[] = [
  { id: 1, name: 'A 拉线历史数模范围补充', scope: '旧拉线：XM-PL01、A线', owner: '陈工', assigneeId: 'emp-chen', total: 286, completed: 174, dueDate: '2026-08-15', status: 'IN_PROGRESS' },
  { id: 2, name: '历史专业类别标准化', scope: '机械、电气自由文本', owner: '李工', assigneeId: 'emp-li', total: 421, completed: 421, dueDate: '2026-07-31', status: 'PENDING_CONFIRMATION' },
  { id: 3, name: '失效文件引用治理', scope: '无法访问的对象存储文件', owner: '王工', assigneeId: 'emp-wang', total: 37, completed: 37, dueDate: '2026-07-25', status: 'COMPLETED' },
]

const mockEmployees: GovernanceEmployee[] = [
  { id: 'emp-chen', name: '陈工', department: '制造工程部', source: 'OFFICE_DIRECTORY' },
  { id: 'emp-li', name: '李工', department: '标准化小组', source: 'OFFICE_DIRECTORY' },
  { id: 'emp-wang', name: '王工', department: '资料管理组', source: 'OFFICE_DIRECTORY' },
]

const mockPlans: Record<number, GovernancePlan[]> = {
  1: [
    { id: 101, taskId: 1, title: '导出历史模组资产清单', status: 'DONE', completedAt: '2026-07-10', plannedStart: '2026-08-01', plannedEnd: '2026-08-02', actualStart: '2026-08-01', actualEnd: '2026-08-02', plannedQuantity: 286, completedQuantity: 286, quantityUnit: '个资产', assigneeId: 'emp-chen', dependencyIds: [] },
    { id: 102, taskId: 1, title: '补充平台、基地和拉线范围', status: 'IN_PROGRESS', plannedStart: '2026-08-03', plannedEnd: '2026-08-09', actualStart: '2026-08-03', plannedQuantity: 286, completedQuantity: 174, quantityUnit: '个资产', assigneeId: 'emp-chen', dependencyIds: [101] },
    { id: 103, taskId: 1, title: '提交业务专家确认', status: 'TODO', plannedStart: '2026-08-10', plannedEnd: '2026-08-12', plannedQuantity: 174, completedQuantity: 0, quantityUnit: '个资产', assigneeId: 'emp-li', dependencyIds: [102] },
  ],
  2: [
    { id: 201, taskId: 2, title: '整理历史专业自由文本', status: 'DONE', completedAt: '2026-07-12', plannedStart: '2026-07-08', plannedEnd: '2026-07-12', actualStart: '2026-07-08', actualEnd: '2026-07-12', plannedQuantity: 421, completedQuantity: 421, quantityUnit: '个字段', assigneeId: 'emp-li', dependencyIds: [] },
    { id: 202, taskId: 2, title: '提交标准化结果确认', status: 'DONE', completedAt: '2026-07-15', plannedStart: '2026-07-13', plannedEnd: '2026-07-15', actualStart: '2026-07-13', actualEnd: '2026-07-15', plannedQuantity: 421, completedQuantity: 421, quantityUnit: '个字段', assigneeId: 'emp-li', dependencyIds: [201] },
  ],
  3: [
    { id: 301, taskId: 3, title: '检查对象存储文件可访问性', status: 'DONE', completedAt: '2026-07-17', plannedStart: '2026-07-16', plannedEnd: '2026-07-17', actualStart: '2026-07-16', actualEnd: '2026-07-17', plannedQuantity: 37, completedQuantity: 37, quantityUnit: '个文件', assigneeId: 'emp-wang', dependencyIds: [] },
    { id: 302, taskId: 3, title: '登记失效引用处理结论', status: 'DONE', completedAt: '2026-07-18', plannedStart: '2026-07-18', plannedEnd: '2026-07-18', actualStart: '2026-07-18', actualEnd: '2026-07-18', plannedQuantity: 37, completedQuantity: 37, quantityUnit: '个文件', assigneeId: 'emp-wang', dependencyIds: [301] },
  ],
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
  })
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json() as Promise<T>
}

export async function getGovernanceTasks(): Promise<GovernanceTask[]> {
  if (useMocks) return mockTasks
  return request<GovernanceTask[]>('/api/v1/governance/tasks')
}

export async function createGovernanceTask(input: Omit<GovernanceTask, 'id' | 'completed' | 'status'>): Promise<GovernanceTask> {
  if (useMocks) {
    const task: GovernanceTask = { ...input, id: mockTasks.length + 1, completed: 0, status: 'DRAFT' }
    mockTasks.push(task)
    return task
  }
  return request<GovernanceTask>('/api/v1/governance/tasks', { method: 'POST', body: JSON.stringify(input) })
}

export async function getGovernanceEmployees(): Promise<GovernanceEmployee[]> {
  if (useMocks) return mockEmployees
  return request<GovernanceEmployee[]>('/api/v1/governance/tasks/employees')
}

export async function getGovernancePlans(taskId: number): Promise<GovernancePlan[]> {
  if (useMocks) return mockPlans[taskId] ?? []
  return request<GovernancePlan[]>(`/api/v1/governance/tasks/${taskId}/plans`)
}

export async function updateGovernanceProgress(taskId: number, completed: number): Promise<GovernanceTask> {
  if (useMocks) {
    const task = mockTasks.find((item) => item.id === taskId)
    if (!task) throw new Error('治理任务不存在')
    if (task.status !== 'IN_PROGRESS') throw new Error('只有进行中的任务可以更新进度')
    task.completed = Math.max(0, Math.min(completed, task.total))
    task.status = task.completed === task.total ? 'PENDING_CONFIRMATION' : 'IN_PROGRESS'
    return task
  }
  return request<GovernanceTask>(`/api/v1/governance/tasks/${taskId}/progress`, {
    method: 'PATCH',
    body: JSON.stringify({ completed }),
  })
}

export async function updateGovernancePlan(taskId: number, planId: number, status: GovernancePlanStatus): Promise<GovernancePlan> {
  if (useMocks) {
    const task = mockTasks.find((item) => item.id === taskId)
    if (!task) throw new Error('治理任务不存在')
    if (task.status !== 'IN_PROGRESS') throw new Error('只有进行中的任务可以更新计划状态')
    const plan = (mockPlans[taskId] ?? []).find((item) => item.id === planId)
    if (!plan) throw new Error('计划项不存在')
    plan.status = status
    plan.completedAt = status === 'DONE' ? new Date().toISOString().slice(0, 10) : undefined
    plan.completedQuantity = status === 'DONE' ? plan.plannedQuantity : 0
    plan.actualStart = status === 'IN_PROGRESS' && !plan.actualStart ? new Date().toISOString().slice(0, 10) : plan.actualStart
    plan.actualEnd = status === 'DONE' ? new Date().toISOString().slice(0, 10) : undefined
    return plan
  }
  return request<GovernancePlan>(`/api/v1/governance/tasks/${taskId}/plans/${planId}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export type CreateGovernancePlanInput = Omit<GovernancePlan, 'id' | 'taskId' | 'status' | 'completedAt' | 'completedQuantity'> & { title: string }

export async function createGovernancePlan(taskId: number, input: CreateGovernancePlanInput): Promise<GovernancePlan> {
  if (useMocks) {
    const task = mockTasks.find((item) => item.id === taskId)
    if (!task) throw new Error('治理任务不存在')
    if (task.status !== 'DRAFT') throw new Error('任务开始执行后计划已锁定，不能直接新增计划')
    const current = mockPlans[taskId] ?? []
    const plan = { ...input, id: Date.now(), taskId, status: 'TODO' as const, completedQuantity: 0 }
    mockPlans[taskId] = [...current, plan]
    return plan
  }
  return request<GovernancePlan>(`/api/v1/governance/tasks/${taskId}/plans`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export async function startGovernanceTask(taskId: number): Promise<GovernanceTask> {
  if (useMocks) {
    const task = mockTasks.find((item) => item.id === taskId)
    if (!task) throw new Error('治理任务不存在')
    if (task.status !== 'DRAFT') throw new Error('只有草稿任务可以开始执行')
    const plans = mockPlans[taskId] ?? []
    if (!plans.length) throw new Error('至少添加一项计划后才能开始执行')
    if (plans.some((plan) => !plan.assigneeId || !plan.plannedStart || !plan.plannedEnd || plan.plannedQuantity <= 0)) {
      throw new Error('所有计划都必须设置责任人、起止日期和计划数量')
    }
    task.status = 'IN_PROGRESS'
    return task
  }
  return request<GovernanceTask>(`/api/v1/governance/tasks/${taskId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status: 'IN_PROGRESS' }),
  })
}
