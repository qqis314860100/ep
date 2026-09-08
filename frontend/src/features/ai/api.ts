import type {
  AiSuggestion,
  AiSuggestionStatus,
  AiSuggestionTargetType,
  ChatCitation,
  ChatMessageView,
  ChatSessionView,
  Page,
} from '../../types/ai'
import { currentActor } from '../auth/session'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

export class AiApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

function actorHeaders(): Record<string, string> {
  const actor = currentActor()
  return { 'X-User-Id': actor.userId, 'X-User-Roles': actor.roles }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...init,
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...actorHeaders(),
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...init?.headers,
    },
  })
  if (!response.ok) {
    let envelope: { error?: { code?: string; message?: string } } = {}
    try {
      envelope = await response.json() as typeof envelope
    } catch {
      // 非 JSON 响应也进入类型化错误。
    }
    throw new AiApiError(
      response.status,
      envelope.error?.code ?? 'ai_request_failed',
      envelope.error?.message ?? `AI 请求失败：${response.status}`,
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

// ---- 建议-确认（T1） ----

export interface SuggestionFilters {
  targetType?: AiSuggestionTargetType
  targetId?: number
  status?: AiSuggestionStatus
  page: number
  perPage: number
}

export function listSuggestions(filters: SuggestionFilters): Promise<Page<AiSuggestion>> {
  return request(`/api/v1/ai/suggestions${queryString({
    target_type: filters.targetType,
    target_id: filters.targetId,
    status: filters.status,
    page: filters.page,
    per_page: filters.perPage,
  })}`)
}

export function confirmSuggestion(id: number): Promise<AiSuggestion> {
  return request(`/api/v1/ai/suggestions/${id}/confirm`, { method: 'POST' })
}

export function rejectSuggestion(id: number): Promise<AiSuggestion> {
  return request(`/api/v1/ai/suggestions/${id}/reject`, { method: 'POST' })
}

export function regenerateSuggestion(type: AiSuggestionTargetType, id: number): Promise<AiSuggestion> {
  return request(`/api/v1/ai/targets/${type}/${id}/suggestions/regenerate`, { method: 'POST' })
}

// ---- 会话（T3） ----

export function listSessions(): Promise<ChatSessionView[]> {
  return request('/api/v1/ai/sessions')
}

export function renameSession(id: number, name: string): Promise<ChatSessionView> {
  return request(`/api/v1/ai/sessions/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ name }),
  })
}

export function deleteSession(id: number): Promise<void> {
  return request(`/api/v1/ai/sessions/${id}`, { method: 'DELETE' })
}

export function sessionMessages(id: number): Promise<ChatMessageView[]> {
  return request(`/api/v1/ai/sessions/${id}/messages`)
}

export interface ChatStreamHandlers {
  onMeta: (sessionId: string, messageId: string) => void
  onDelta: (text: string) => void
  onCitations: (refs: ChatCitation[]) => void
  onDone: () => void
  onError: (code: string, message: string) => void
}

/** 流式问答：消费 /api/v1/ai/chat 的 SSE（meta→delta*→citations→done|error）。失败抛 AiApiError。 */
export async function streamChat(
  requestBody: { sessionId?: number; question: string },
  handlers: ChatStreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}/api/v1/ai/chat`, {
      method: 'POST',
      credentials: 'include',
      signal,
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        ...actorHeaders(),
      },
      body: JSON.stringify(requestBody),
    })
  } catch {
    throw new AiApiError(0, 'network_failed', '无法连接 AI 服务，请稍后重试')
  }
  if (!response.ok) {
    let envelope: { error?: { code?: string; message?: string } } = {}
    try {
      envelope = await response.json() as typeof envelope
    } catch {
      // 非 JSON 响应也进入类型化错误。
    }
    throw new AiApiError(
      response.status,
      envelope.error?.code ?? 'ai_chat_failed',
      envelope.error?.message ?? `问答请求失败：${response.status}`,
    )
  }
  const reader = response.body?.getReader()
  if (!reader) throw new AiApiError(0, 'network_failed', 'AI 服务未返回流式响应')
  const decoder = new TextDecoder()
  let buffer = ''
  const dispatch = (block: string) => {
    const trimmed = block.trim()
    if (!trimmed) return
    let event = ''
    let data = ''
    for (const line of trimmed.split('\n')) {
      if (line.startsWith('event:')) event = line.slice('event:'.length).trim()
      else if (line.startsWith('data:')) data = line.slice('data:'.length).trim()
    }
    if (!data) return
    switch (event) {
      case 'meta': {
        const parsed = JSON.parse(data) as { sessionId?: string; messageId?: string }
        handlers.onMeta(parsed.sessionId ?? '', parsed.messageId ?? '')
        break
      }
      case 'delta': {
        handlers.onDelta((JSON.parse(data) as { text?: string }).text ?? '')
        break
      }
      case 'citations': {
        handlers.onCitations((JSON.parse(data) as { refs?: ChatCitation[] }).refs ?? [])
        break
      }
      case 'done':
        handlers.onDone()
        break
      case 'error': {
        const parsed = JSON.parse(data) as { code?: string; message?: string }
        handlers.onError(parsed.code ?? 'error', parsed.message ?? 'AI 问答失败')
        break
      }
    }
  }
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      dispatch(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
  }
  if (buffer.trim()) dispatch(buffer)
}
