import { SaveOutlined, SendOutlined, SyncOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Space, Spin, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import styled from 'styled-components'

import { getGovernanceEmployees, getGovernanceItems, saveBatchResults, saveResultDraft } from '../api'
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

function standardVersion(entry: GovernanceItemExecution): number {
  if (entry.currentResult) return entry.currentResult.standardVersion
  const context = entry.ruleContext
  return context && typeof context === 'object' && !Array.isArray(context) && typeof context.standardVersion === 'number' ? context.standardVersion : 0
}

function sameBatch(left: GovernanceItemExecution, right: GovernanceItemExecution): boolean {
  return left.item.targetField === right.item.targetField && left.item.scopeFingerprint === right.item.scopeFingerprint && standardVersion(left) === standardVersion(right)
}

export function GovernanceExecutionPage({ taskId }: { taskId: number }) {
  const itemsQuery = useQuery({ queryKey: ['governance-items', taskId], queryFn: () => getGovernanceItems(taskId) })
  const employeesQuery = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 300_000 })
  const [currentId, setCurrentId] = useState<number>()
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [value, setValue] = useState<JsonValue>('')
  const [savedValue, setSavedValue] = useState<JsonValue>('')
  const [batchResults, setBatchResults] = useState<BatchItemResult[]>([])
  const [batchOpen, setBatchOpen] = useState(false)
  const current = useMemo(() => itemsQuery.data?.find(entry => entry.item.id === currentId), [itemsQuery.data, currentId])
  const dirty = JSON.stringify(value) !== JSON.stringify(savedValue)

  useEffect(() => {
    if (!currentId && itemsQuery.data?.length) setCurrentId(itemsQuery.data[0].item.id)
  }, [currentId, itemsQuery.data])

  useEffect(() => {
    if (!current) return
    const next = current.currentResult?.proposedValue ?? parseOriginal(current.originalFactJson)
    setValue(next)
    setSavedValue(next)
  }, [current])

  useEffect(() => {
    const protect = (event: BeforeUnloadEvent) => { if (dirty) event.preventDefault() }
    window.addEventListener('beforeunload', protect)
    return () => window.removeEventListener('beforeunload', protect)
  }, [dirty])

  const saveMutation = useMutation({
    mutationFn: () => saveResultDraft(current!.item.id, { itemVersion: current!.item.version, assetVersion: current!.item.assetVersion, proposedValue: value, actorUserId: 'demo-user' }),
    onSuccess: result => { setSavedValue(result.proposedValue); void itemsQuery.refetch() },
  })
  const batchMutation = useMutation({
    mutationFn: () => saveBatchResults(crypto.randomUUID(), selectedIds.map(id => {
      const entry = itemsQuery.data!.find(item => item.item.id === id)!
      return { itemId: id, itemVersion: entry.item.version, assetVersion: entry.item.assetVersion, proposedValue: entry.currentResult?.proposedValue ?? parseOriginal(entry.originalFactJson), submit: true, targetField: entry.item.targetField, standardVersion: standardVersion(entry), scopeFingerprint: entry.item.scopeFingerprint, actorUserId: 'demo-user' }
    })),
    onSuccess: data => { setBatchResults(data.results); setBatchOpen(true); void itemsQuery.refetch() },
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
  const anchor = itemsQuery.data?.find(entry => selectedIds.includes(entry.item.id))

  if (itemsQuery.isLoading) return <Spin tip="正在加载清洗工作台"><div style={{ height: 320 }} /></Spin>
  if (itemsQuery.error) return <Alert type="error" showIcon message="清洗工作台加载失败" description={itemsQuery.error.message} />
  if (!current) return <Alert type="info" showIcon message="当前任务没有可执行治理项" />

  return <article>
    <Toolbar><div><Typography.Title level={3} style={{ margin: 0 }}>字段清洗工作台</Typography.Title><Typography.Text type="secondary">任务 #{taskId} · 原值与拟值逐项核对</Typography.Text></div><Space><Button aria-label="批量提交" icon={<SendOutlined aria-hidden />} disabled={!selectedIds.length} loading={batchMutation.isPending} onClick={() => batchMutation.mutate()}>批量提交</Button><Button aria-label="保存草稿" type="primary" icon={<SaveOutlined aria-hidden />} disabled={!dirty} loading={saveMutation.isPending} onClick={() => saveMutation.mutate()}>保存草稿</Button></Space></Toolbar>
    {saveMutation.error && <Alert type="error" showIcon message={saveMutation.error.message} action={<Button aria-label="刷新当前项" icon={<SyncOutlined aria-hidden />} onClick={() => void refresh(current.item.id)}>刷新当前项</Button>} style={{ marginBottom: 12 }} />}
    {batchMutation.error && <Alert type="error" showIcon message={batchMutation.error.message} style={{ marginBottom: 12 }} />}
    <Layout>
      <GovernanceItemQueue items={itemsQuery.data ?? []} currentId={currentId} selectedIds={selectedIds} onSelect={choose} isSelectable={entry => !anchor || selectedIds.includes(entry.item.id) || sameBatch(anchor, entry)} onToggle={(entry, checked) => setSelectedIds(ids => checked ? [...ids, entry.item.id] : ids.filter(id => id !== entry.item.id))} />
      <Editor><FieldResultEditor field={current.item.targetField} originalValue={current.originalFactJson} value={value} employees={employeesQuery.data ?? []} ruleContext={current.ruleContext} disabled={current.item.status === 'SUBMITTED'} onChange={setValue} /></Editor>
      <Context><RuleContextPanel execution={current} /></Context>
    </Layout>
    <BatchResultDrawer open={batchOpen} results={batchResults} onClose={() => setBatchOpen(false)} onRefresh={itemId => { setBatchOpen(false); void refresh(itemId) }} />
  </article>
}
