import {
  DeleteOutlined,
  EditOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  Button,
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
import CitationBlock from './components/CitationBlock'
import MarkdownAnswer from './components/MarkdownAnswer'
import WelcomePanel from './components/WelcomePanel'

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

const SessionItem = styled.div<{ $active: boolean }>`
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 4px;
  padding: 2px 2px 2px 8px;
  border-radius: 6px;
  background: ${({ $active }) => ($active ? '#e7f0ec' : 'transparent')};

  &:hover {
    background: ${({ $active }) => ($active ? '#e7f0ec' : '#f4f6f5')};
  }
`

const SessionOpen = styled.button`
  flex: 1;
  min-width: 0;
  padding: 6px 4px;
  border: 0;
  background: transparent;
  text-align: left;
  color: #3a4a55;
  cursor: pointer;
  font-size: 13px;
`

const SessionActions = styled.span`
  display: inline-flex;
  flex: none;
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
  margin-bottom: 16px;
  justify-content: ${({ $assistant }) => ($assistant ? 'flex-start' : 'flex-end')};
`

const BubbleColumn = styled.div<{ $assistant: boolean }>`
  max-width: 82%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: ${({ $assistant }) => ($assistant ? 'flex-start' : 'flex-end')};
`

const RoleRow = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
`

const RoleChip = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #2f7567;
  font-weight: 500;
`

const TurnTime = styled.span`
  font-size: 11px;
  color: #a5b2b8;
`

const Bubble = styled.div<{ $assistant: boolean }>`
  padding: 9px 12px;
  border-radius: 8px;
  background: ${({ $assistant }) => ($assistant ? '#f4f6f5' : '#e7f0ec')};
  border: ${({ $assistant }) => ($assistant ? '1px solid #eceff1' : '1px solid transparent')};
  word-break: break-word;
  min-width: 0;
`

const Thinking = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #5a6b74;
  font-size: 13px;
`

const Dot = styled.span<{ $delay: number }>`
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #2f7567;
  animation: ai-dot-bounce 1.2s infinite;
  animation-delay: ${({ $delay }) => `${$delay}ms`};

  @keyframes ai-dot-bounce {
    0%, 60%, 100% { opacity: 0.25; transform: translateY(0); }
    30% { opacity: 1; transform: translateY(-2px); }
  }
`

const Caret = styled.span`
  display: inline-block;
  width: 7px;
  height: 1em;
  margin-left: 3px;
  vertical-align: text-bottom;
  background: #2f7567;
  border-radius: 1px;
  animation: ai-caret-blink 1s step-end infinite;

  @keyframes ai-caret-blink {
    50% { opacity: 0; }
  }
`

const ErrorBox = styled.div`
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: #fdf0ef;
  border: 1px solid #f3d3d0;
  color: #c2452f;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
`

const Composer = styled.div`
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #eceff1;
`

const StopBar = styled.div`
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #e0e5e2;
`

interface Turn {
  key: string
  assistant: boolean
  content: string
  citations: ChatCitation[]
  pending?: boolean
  createdAt?: string
  failed?: { code: string; message: string; question: string } | null
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
      else if (items.length > 0) setActiveId((current) => current ?? items[0].id)
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
        createdAt: item.createdAt,
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

  // 选中会话（含路由返回/初次挂载自动选中首会话）时载入该会话历史
  useEffect(() => {
    if (activeId === null) {
      setTurns([])
      return
    }
    void loadMessages(activeId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeId])

  const openSession = async (sessionId: number) => {
    setActiveId(sessionId)
  }

  const finishPending = (updates: Partial<Turn> & { failed?: Turn['failed'] }) => {
    setTurns((previous) => previous.map((turn) => (turn.pending ? { ...turn, pending: false, ...updates } : turn)))
  }

  const appendAssistant = () => {
    setTurns((previous) => [...previous, {
      key: `assistant-${Date.now()}`,
      assistant: true,
      content: '',
      citations: [],
      pending: true,
    }])
  }

  const stream = async (content: string) => {
    const controller = new AbortController()
    abortRef.current = controller
    let streamingSessionId = activeId
    try {
      await streamChat(
        { sessionId: activeId ?? undefined, question: content },
        {
          onMeta: (sessionId) => {
            const parsed = Number(sessionId)
            if (Number.isFinite(parsed) && parsed > 0) streamingSessionId = parsed
            if (activeId === null && Number.isFinite(parsed) && parsed > 0) {
              setActiveId(parsed)
              void refreshSessions(parsed)
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
            finishPending({ failed: null })
            if (streamingSessionId !== null) await loadMessages(streamingSessionId)
          },
          onError: (code, messageText) => {
            finishPending({ failed: { code, message: messageText, question: content } })
          },
        },
        controller.signal,
      )
    } catch (error) {
      if (error instanceof AiApiError) {
        finishPending({ failed: { code: error.code, message: error.message, question: content } })
      } else if (error instanceof DOMException && error.name === 'AbortError') {
        finishPending({ failed: null })
      } else {
        finishPending({ failed: { code: 'unknown', message: '问答失败，请重试', question: content } })
      }
    } finally {
      setStreaming(false)
      abortRef.current = null
    }
  }

  const send = async (text?: string) => {
    const content = (text ?? question).trim()
    if (!content || streaming) return
    setQuestion('')
    setTurns((previous) => [...previous, {
      key: `user-${Date.now()}`,
      assistant: false,
      content,
      citations: [],
    }])
    appendAssistant()
    setStreaming(true)
    await stream(content)
  }

  const retry = async (failed: NonNullable<Turn['failed']>) => {
    if (streaming) return
    setTurns((previous) => {
      const copy = [...previous]
      if (copy.length > 0 && copy[copy.length - 1].assistant) copy.pop()
      return copy
    })
    appendAssistant()
    setStreaming(true)
    await stream(failed.question)
  }

  const stop = () => abortRef.current?.abort()

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

  const streamingTurn = turns[turns.length - 1]?.assistant && turns[turns.length - 1]?.pending
    ? turns[turns.length - 1]
    : null

  return (
    <Page>
      <SessionPane>
        <SessionHead>
          <span>会话</span>
          <Button size="small" type="text" icon={<RobotOutlined />} aria-label="新建会话"
            onClick={() => { void refreshSessions(); setActiveId(null); setTurns([]) }}>
            新对话
          </Button>
        </SessionHead>
        <SessionList aria-label="会话列表">
          {loading ? <Skeleton active paragraph={{ rows: 4 }} /> : (
            <List
              dataSource={sessions}
              split={false}
              locale={{ emptyText: '暂无会话' }}
              renderItem={(item) => (
                <SessionItem $active={activeId === item.id}>
                  <SessionOpen type="button" aria-label={`打开会话：${item.title}`}
                    onClick={() => void openSession(item.id)}>
                    <Text ellipsis style={{ display: 'block', color: 'inherit', fontSize: 13 }}>
                      {item.title}
                    </Text>
                  </SessionOpen>
                  <SessionActions>
                    <Tooltip title="重命名">
                      <Button size="small" type="text" icon={<EditOutlined />}
                        aria-label={`重命名会话：${item.title}`}
                        onClick={() => { setRenameTarget(item); setRenameValue(item.title) }} />
                    </Tooltip>
                    <Popconfirm title="删除该会话？" onConfirm={() => void removeSession(item.id)}>
                      <Button size="small" type="text" danger icon={<DeleteOutlined />}
                        aria-label={`删除会话：${item.title}`} />
                    </Popconfirm>
                  </SessionActions>
                </SessionItem>
              )}
            />
          )}
        </SessionList>
      </SessionPane>

      <ChatPane>
        <ChatBody ref={chatBodyRef}>
          {turns.length === 0 ? (
            <WelcomePanel onAsk={(prompt) => void send(prompt)} />
          ) : turns.map((turn) => {
            if (turn.assistant && !turn.pending && !turn.failed && !turn.content) return null
            return (
            <BubbleRow key={turn.key} $assistant={turn.assistant}>
              <BubbleColumn $assistant={turn.assistant}>
                {turn.assistant && (
                  <RoleRow>
                    <RoleChip>
                      <RobotOutlined /> AI
                    </RoleChip>
                    {turn.createdAt && <TurnTime>{new Date(turn.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</TurnTime>}
                  </RoleRow>
                )}
                <Bubble $assistant={turn.assistant}>
                  {turn.pending ? (
                    turn.content === '' ? (
                      <Thinking>
                        正在生成答案
                        <Dot $delay={0} />
                        <Dot $delay={150} />
                        <Dot $delay={300} />
                      </Thinking>
                    ) : (
                      <span>
                        {turn.content}
                        <Caret />
                      </span>
                    )
                  ) : turn.failed ? (
                    <>
                      {turn.content && <MarkdownAnswer content={turn.content} />}
                      <ErrorBox role="alert">
                        <span>
                          {(turn.failed.code === 'unavailable' || turn.failed.code === 'timeout' || turn.failed.code === 'network_failed')
                            ? `AI 服务暂不可用：${turn.failed.message}`
                            : turn.failed.message}
                        </span>
                        <Button size="small" danger onClick={() => void retry(turn.failed!)}>
                          重试
                        </Button>
                      </ErrorBox>
                    </>
                  ) : (
                    turn.content && <MarkdownAnswer content={turn.content} />
                  )}
                  {turn.pending && streamingTurn === turn && (
                    <StopBar>
                      <Button type="text" size="small" danger icon={<StopOutlined />}
                        aria-label="停止生成" onClick={stop}>
                        停止
                      </Button>
                    </StopBar>
                  )}
                </Bubble>
                {turn.assistant && turn.citations.length > 0 && (
                  <CitationBlock citations={turn.citations} />
                )}
              </BubbleColumn>
            </BubbleRow>
            )
          })}
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
