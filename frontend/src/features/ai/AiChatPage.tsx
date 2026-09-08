import { DeleteOutlined, EditOutlined, RobotOutlined, SendOutlined } from '@ant-design/icons'
import {
  Button,
  Empty,
  Input,
  List,
  Modal,
  Popconfirm,
  Skeleton,
  Tooltip,
  Typography,
  message,
} from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import styled from 'styled-components'
import type { ChatCitation, ChatMessageView, ChatSessionView } from '../../types/ai'
import {
  AiApiError,
  deleteSession,
  listSessions,
  renameSession,
  sessionMessages,
  streamChat,
} from './api'

const { Text } = Typography

const Page = styled.div`
  display: grid;
  grid-template-columns: 248px 1fr;
  gap: 14px;
  height: calc(100vh - 50px - 28px);
  min-height: 460px;
`

const SessionPane = styled.aside`
  background: #fff;
  border: 1px solid #e6e8ea;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
`

const SessionHead = styled.div`
  padding: 10px 12px;
  border-bottom: 1px solid #eceff1;
  color: #102b3d;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: space-between;
`

const SessionList = styled.div`
  flex: 1;
  overflow: auto;
  padding: 6px;
`

const SessionItem = styled.button<{ $active: boolean }>`
  display: block;
  width: 100%;
  padding: 8px 10px;
  margin-bottom: 4px;
  text-align: left;
  color: ${({ $active }) => ($active ? '#fff' : '#3a4a55')};
  background: ${({ $active }) => ($active ? '#2f7567' : 'transparent')};
  border: 0;
  border-radius: 6px;
  cursor: pointer;

  &:hover {
    background: ${({ $active }) => ($active ? '#2f7567' : '#f0f3f2')};
  }
`

const ChatPane = styled.section`
  background: #fff;
  border: 1px solid #e6e8ea;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
`

const ChatBody = styled.div`
  flex: 1;
  overflow: auto;
  padding: 16px 18px;
`

const BubbleRow = styled.div<{ $assistant: boolean }>`
  display: flex;
  margin-bottom: 14px;
  justify-content: ${({ $assistant }) => ($assistant ? 'flex-start' : 'flex-end')};
`

const Bubble = styled.div<{ $assistant: boolean }>`
  max-width: 82%;
  padding: 9px 12px;
  border-radius: 8px;
  background: ${({ $assistant }) => ($assistant ? '#f4f6f5' : '#e7f0ec')};
  white-space: pre-wrap;
  word-break: break-word;
`

const CitationLink = styled.button`
  display: block;
  margin-top: 6px;
  padding: 0;
  color: #2f7567;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
`

const Composer = styled.div`
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #eceff1;
`

interface Turn {
  key: string
  assistant: boolean
  content: string
  citations: ChatCitation[]
  pending?: boolean
}

export default function AiChatPage() {
  const [sessions, setSessions] = useState<ChatSessionView[]>([])
  const [activeId, setActiveId] = useState<number | null>(null)
  const [turns, setTurns] = useState<Turn[]>([])
  const [question, setQuestion] = useState('')
  const [streaming, setStreaming] = useState(false)
  const [loading, setLoading] = useState(true)
  const [renameTarget, setRenameTarget] = useState<ChatSessionView | null>(null)
  const [renameValue, setRenameValue] = useState('')
  const chatBodyRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<AbortController | null>(null)

  const refreshSessions = useCallback(async (preferId?: number) => {
    try {
      const items = await listSessions()
      setSessions(items)
      if (preferId !== undefined) setActiveId(preferId)
      else if (activeId === null && items.length > 0) setActiveId(items[0].id)
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '会话列表加载失败')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loadMessages = useCallback(async (sessionId: number) => {
    try {
      const items = await sessionMessages(sessionId)
      setTurns(items.map((item: ChatMessageView) => ({
        key: String(item.id),
        assistant: item.role === 'ASSISTANT',
        content: item.content,
        citations: item.citations,
      })))
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '历史加载失败')
    }
  }, [])

  useEffect(() => {
    void refreshSessions()
    return () => abortRef.current?.abort()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (chatBodyRef.current) chatBodyRef.current.scrollTop = chatBodyRef.current.scrollHeight
  }, [turns])

  const openSession = async (sessionId: number) => {
    setActiveId(sessionId)
    setTurns([])
    await loadMessages(sessionId)
  }

  const send = async () => {
    const content = question.trim()
    if (!content || streaming) return
    setQuestion('')
    setTurns((previous) => [...previous, {
      key: `user-${Date.now()}`,
      assistant: false,
      content,
      citations: [],
    }, {
      key: `assistant-${Date.now()}`,
      assistant: true,
      content: '',
      citations: [],
      pending: true,
    }])
    setStreaming(true)
    const controller = new AbortController()
    abortRef.current = controller
    let streamingSessionId = activeId
    try {
      await streamChat(
        { sessionId: activeId ?? undefined, question: content },
        {
          onMeta: (sessionId) => {
            streamingSessionId = Number(sessionId)
            if (activeId === null) {
              setActiveId(streamingSessionId)
              void refreshSessions(streamingSessionId)
            }
          },
          onDelta: (text) => {
            setTurns((previous) => {
              const copy = [...previous]
              const last = copy[copy.length - 1]
              if (last && last.assistant && last.pending) {
                copy[copy.length - 1] = { ...last, content: last.content + text }
              }
              return copy
            })
          },
          onCitations: (refs) => {
            setTurns((previous) => {
              const copy = [...previous]
              const last = copy[copy.length - 1]
              if (last && last.assistant && last.pending) {
                copy[copy.length - 1] = { ...last, citations: refs }
              }
              return copy
            })
          },
          onDone: async () => {
            setTurns((previous) => previous.map((turn) => (turn.pending ? { ...turn, pending: false } : turn)))
            if (streamingSessionId !== null) await loadMessages(streamingSessionId)
            if (activeId === null) await refreshSessions(streamingSessionId ?? undefined)
          },
          onError: (code, messageText) => {
            setTurns((previous) => previous.map((turn) => (turn.pending ? { ...turn, pending: false } : turn)))
            message.error(code === 'unavailable' || code === 'timeout'
              ? `AI 服务暂不可用：${messageText}（可重试）`
              : messageText)
          },
        },
        controller.signal,
      )
    } catch (error) {
      if (error instanceof AiApiError) message.error(error.message)
      else if (!(error instanceof DOMException && error.name === 'AbortError')) message.error('问答失败，请重试')
    } finally {
      setStreaming(false)
    }
  }

  const removeSession = async (sessionId: number) => {
    try {
      await deleteSession(sessionId)
      const next = sessions.filter((item) => item.id !== sessionId)
      setSessions(next)
      if (activeId === sessionId) {
        setActiveId(next.length > 0 ? next[0].id : null)
        if (next.length > 0) await loadMessages(next[0].id)
        else setTurns([])
      }
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '删除会话失败')
    }
  }

  const commitRename = async () => {
    if (!renameTarget || !renameValue.trim()) return
    try {
      await renameSession(renameTarget.id, renameValue.trim())
      setRenameTarget(null)
      await refreshSessions()
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '重命名失败')
    }
  }

  return (    <Page>
      <SessionPane>
        <SessionHead>
          <span>会话</span>
          <Button size="small" type="text" icon={<RobotOutlined />} onClick={() => { void refreshSessions(); setActiveId(null); setTurns([]) }}>
            新对话
          </Button>
        </SessionHead>
        <SessionList aria-label="会话列表">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
            <List
              dataSource={sessions}
              split={false}
              locale={{ emptyText: <Empty description="暂无会话" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
              renderItem={(item) => (
                <SessionItem $active={activeId === item.id} onClick={() => void openSession(item.id)}>
                  <Text ellipsis style={{ width: 150, display: 'block' }}>{item.title}</Text>
                  <span>
                    <Tooltip title="重命名">
                      <Button size="small" type="text" icon={<EditOutlined />}
                        onClick={(event) => { event.stopPropagation(); setRenameTarget(item); setRenameValue(item.title) }} />
                    </Tooltip>
                    <Popconfirm title="删除该会话？" onConfirm={() => void removeSession(item.id)}>
                      <Button size="small" type="text" danger icon={<DeleteOutlined />}
                        onClick={(event) => event.stopPropagation()} />
                    </Popconfirm>
                  </span>
                </SessionItem>
              )}
            />
          )}
        </SessionList>
      </SessionPane>

      <ChatPane>
        <ChatBody ref={chatBodyRef}>
          {turns.length === 0 ? (
            <Empty style={{ marginTop: 80 }} description="向 AI 助手提问，如：宁德基地 A 拉线有哪些焊接数模？"
              image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : turns.map((turn) => (
            <BubbleRow key={turn.key} $assistant={turn.assistant}>
              <div style={{ maxWidth: '82%' }}>
                <Bubble $assistant={turn.assistant}>
                  {turn.content || (turn.pending ? '正在思考…' : '')}
                </Bubble>
                {turn.assistant && turn.citations.length > 0 && (
                  <div>
                    {turn.citations.map((citation, index) => (
                      <CitationLink key={`${turn.key}-${index}`} type="button"
                        onClick={() => {
                          const assetId = Number.parseInt(citation.docId, 10)
                          window.open(Number.isFinite(assetId) ? `/assets/${assetId}` : '/', '_blank')
                        }}>
                        引用 {index + 1}：{citation.location || citation.docId}
                      </CitationLink>
                    ))}
                  </div>
                )}
              </div>
            </BubbleRow>
          ))}
        </ChatBody>
        <Composer>
          <Input.TextArea
            aria-label="问题输入框"
            autoSize={{ minRows: 1, maxRows: 4 }}
            value={question}
            disabled={streaming}
            placeholder="输入问题，Enter 发送（Shift+Enter 换行）"
            onChange={(event) => setQuestion(event.target.value)}
            onPressEnter={(event) => {
              if (!event.shiftKey) {
                event.preventDefault()
                void send()
              }
            }}
          />
          <Button type="primary" icon={<SendOutlined />} loading={streaming} disabled={!question.trim()}
            aria-label="发送问题"
            onClick={() => void send()}>
            发送
          </Button>
        </Composer>
      </ChatPane>

      <Modal title="重命名会话" open={renameTarget !== null} onOk={() => void commitRename()}
        onCancel={() => setRenameTarget(null)} okText="保存" cancelText="取消">
        <Input aria-label="会话标题" value={renameValue} onChange={(event) => setRenameValue(event.target.value)} />
      </Modal>
    </Page>
  )
}
