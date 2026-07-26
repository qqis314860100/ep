import { CheckCircleOutlined, SaveOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Space, Spin, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'

import { getDictionaryItems } from '../../../services/dictionaryService'
import { getGovernanceEmployees, getGovernanceItems, getGovernanceTask, saveBatchResults, saveResultDraft, submitForConfirmation } from '../api'
import { BatchResultDrawer } from '../shared/BatchResultDrawer'
import type { BatchItemResult, GovernanceItemExecution, JsonValue } from '../types'
import { FieldResultEditor } from './FieldResultEditor'
import { GovernanceItemQueue } from './GovernanceItemQueue'
import { RuleContextPanel } from './RuleContextPanel'

const Layout = styled.div`display:flex; height:clamp(560px, calc(100vh - 210px), 760px); border-top:1px solid #dfe5e2; border-bottom:1px solid #dfe5e2;`
const Editor = styled.main`flex:1; min-width:360px; padding:18px 22px; overflow:auto;`
const Context = styled.div`width:300px; min-width:260px; padding:18px; border-left:1px solid #dfe5e2; overflow:auto;`
const Toolbar = styled.header`display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:12px;`

function parseOriginal(value: string): JsonValue {
  try { return JSON.parse(value) as JsonValue } catch { return value }
}

function objectValue(value: JsonValue): Record<string, JsonValue> | undefined {
  return value && typeof value === 'object' && !Array.isArray(value) ? value : undefined
}

function toEditorValue(field: GovernanceItemExecution['item']['targetField'], value: JsonValue): JsonValue {
  const object = objectValue(value)
  if (!object) return value
  if (field === 'DESCRIPTION') return object.description ?? ''
  if (field === 'SPECIALTIES') return object.specialtyItemIds ?? []
  if (field === 'OWNER') return object.ownerUserId ?? ''
  if (field === 'SCOPE') return object.scopes ?? []
  return value
}

function toCommandValue(field: GovernanceItemExecution['item']['targetField'], value: JsonValue, employees: Awaited<ReturnType<typeof getGovernanceEmployees>>): JsonValue {
  if (field === 'DESCRIPTION') return { description: value }
  if (field === 'SPECIALTIES') return { specialtyItemIds: value }
  if (field === 'OWNER') {
    const employee = employees.find(item => item.id === value)
    return { ownerUserId: value, ownerName: employee?.name ?? '' }
  }
  if (field === 'SCOPE') return { scopes: value }
  return value
}

function standardVersion(entry: GovernanceItemExecution): number {
  if (entry.currentResult) return entry.currentResult.standardVersion
  const context = entry.ruleContext
  return context && typeof context === 'object' && !Array.isArray(context) && typeof context.standardVersion === 'number' ? context.standardVersion : 0
}

function sameBatch(left: GovernanceItemExecution, right: GovernanceItemExecution): boolean {
  return left.item.targetField === right.item.targetField && left.item.scopeFingerprint === right.item.scopeFingerprint && standardVersion(left) === standardVersion(right)
}

export function GovernanceExecutionPage({ taskId }: { taskId: number }) {
  const navigate = useNavigate()
  const itemsQuery = useQuery({ queryKey: ['governance-items', taskId], queryFn: () => getGovernanceItems(taskId) })
  const taskQuery = useQuery({ queryKey: ['governance-task', taskId], queryFn: () => getGovernanceTask(taskId) })
  const employeesQuery = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 300_000 })
  const dictionariesQuery = useQuery({ queryKey: ['dictionary-items'], queryFn: getDictionaryItems, staleTime: 300_000 })
  const [currentId, setCurrentId] = useState<number>()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [value, setValue] = useState<JsonValue>('')
  const [savedValue, setSavedValue] = useState<JsonValue>('')
  const [batchResults, setBatchResults] = useState<BatchItemResult[]>([])
  const [batchOpen, setBatchOpen] = useState(false)
  const workItems = useMemo(() => itemsQuery.data?.filter(entry => entry.item.status !== 'CONFIRMED' && entry.item.status !== 'ACCEPTED') ?? [], [itemsQuery.data])
  const current = useMemo(() => workItems.find(entry => entry.item.id === currentId), [workItems, currentId])
  const dirty = JSON.stringify(value) !== JSON.stringify(savedValue)

  useEffect(() => {
    if (workItems.length && !workItems.some(entry => entry.item.id === currentId)) setCurrentId(workItems[0].item.id)
  }, [currentId, workItems])

  useEffect(() => {
    if (!current) return
    const next = toEditorValue(current.item.targetField, current.currentResult?.proposedValue ?? parseOriginal(current.originalFactJson))
    setValue(next)
    setSavedValue(next)
  }, [current])

  useEffect(() => {
    const protect = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault() }
    window.addEventListener('beforeunload', protect)
    return () => window.removeEventListener('beforeunload', protect)
  }, [dirty])

  const saveMutation = useMutation({
    mutationFn: () => saveResultDraft(current!.item.id, { itemVersion: current!.item.version, assetVersion: current!.item.assetVersion, proposedValue: toCommandValue(current!.item.targetField, value, employeesQuery.data ?? []), actorUserId: 'demo-user' }),
    onSuccess: result => { setSavedValue(toEditorValue(current!.item.targetField, result.proposedValue)); void itemsQuery.refetch() },
  })
  const batchMutation = useMutation({
    mutationFn: () => saveBatchResults(crypto.randomUUID(), selectedIds.map(id => {
      const entry = workItems.find(item => item.item.id === id)!
      const editorValue = toEditorValue(entry.item.targetField, entry.currentResult?.proposedValue ?? parseOriginal(entry.originalFactJson))
      return { itemId: id, itemVersion: entry.item.version, assetVersion: entry.item.assetVersion, proposedValue: toCommandValue(entry.item.targetField, editorValue, employeesQuery.data ?? []), submit: true, targetField: entry.item.targetField, standardVersion: standardVersion(entry), scopeFingerprint: entry.item.scopeFingerprint, actorUserId: 'demo-user' }
    })),
    onSuccess: data => { setBatchResults(data.results); setBatchOpen(true); void itemsQuery.refetch() },
  })
  const confirmationMutation = useMutation({
    mutationFn: () => submitForConfirmation(taskId, taskQuery.data?.version ?? 0),
    onSuccess: () => navigate(`/sys/drawing/tasks/${taskId}/confirm`),
  })

  const choose = (entry: GovernanceItemExecution) => {
    if (entry.item.id === currentId) return
    if (dirty && !window.confirm('当前治理项有未保存内容，确定离开吗？')) return
    setCurrentId(entry.item.id)
    saveMutation.reset()
  }
  const refresh = async (itemId: number) => {
    setCurrentId(itemId)
    saveMutation.reset()
    await itemsQuery.refetch()
  }
  const anchor = workItems.find(entry => selectedIds.includes(entry.item.id))
  const allSubmitted = Boolean(workItems.length) && workItems.every(entry => entry.item.status === 'SUBMITTED')

  if (itemsQuery.isLoading) return <Spin tip="正在加载清洗工作台"><div style={{ height: 320 }} /></Spin>
  if (itemsQuery.error) return <Alert type="error" showIcon message="清洗工作台加载失败" description={itemsQuery.error.message} />
  if (!current) return <Alert type="info" showIcon message="当前任务没有可执行治理项" />

  return <article>
    <Toolbar><div><Typography.Title level={3} style={{ margin: 0 }}>字段清洗工作台</Typography.Title><Typography.Text type="secondary">任务 #{taskId} · 原值与拟值逐项核对</Typography.Text></div><Space>{allSubmitted && <Button aria-label="提交确认" type="primary" icon={<CheckCircleOutlined aria-hidden />} loading={confirmationMutation.isPending} onClick={() => confirmationMutation.mutate()}>提交确认</Button>}<Button aria-label="批量提交" icon={<SendOutlined aria-hidden />} disabled={!selectedIds.length || allSubmitted} loading={batchMutation.isPending} onClick={() => batchMutation.mutate()}>批量提交</Button><Button aria-label="保存草稿" type={allSubmitted ? 'default' : 'primary'} icon={<SaveOutlined aria-hidden />} disabled={!dirty} loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>保存草稿</Button></Space></Toolbar>
    {saveMutation.error && <Alert type="error" showIcon message={saveMutation.error.message} action={<Button aria-label="刷新当前项" icon={<SyncOutlined aria-hidden />} onClick={() => void refresh(current.item.id)}>刷新当前项</Button>} style={{ marginBottom: 12 }} />}
    {batchMutation.error && <Alert type="error" showIcon message={batchMutation.error.message} style={{ marginBottom: 12 }} />}
    {confirmationMutation.error && <Alert type="error" showIcon message={confirmationMutation.error.message} style={{ marginBottom: 12 }} />}
    <Layout>
      <GovernanceItemQueue items={workItems} currentId={currentId} selectedIds={selectedIds} onSelect={choose} isSelectable={entry => !anchor || selectedIds.includes(entry.item.id) || sameBatch(anchor, entry)} onToggle={(entry, checked) => setSelectedIds(ids => checked ? [...ids, entry.item.id] : ids.filter(id => id !== entry.item.id))} />
      <Editor><FieldResultEditor field={current.item.targetField} originalValue={current.originalFactJson} value={value} employees={employeesQuery.data ?? []} dictionaryItems={dictionariesQuery.data ?? []} ruleContext={current.ruleContext} disabled={current.item.status === 'SUBMITTED'} onChange={setValue} /></Editor>
      <Context><RuleContextPanel execution={current} /></Context>
    </Layout>
    <BatchResultDrawer open={batchOpen} results={batchResults} onClose={() => setBatchOpen(false)} onRefresh={itemId => { setBatchOpen(false); void refresh(itemId) }} />
  </article>
}
