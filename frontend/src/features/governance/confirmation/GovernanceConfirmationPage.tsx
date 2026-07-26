import { CheckOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Descriptions, List, Space, Spin, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import styled from 'styled-components'
import { completeConfirmation, getCurrentConfirmation, getGovernanceItems, saveConfirmationDecision } from '../api'
import { ConfirmationDecisionPanel } from './ConfirmationDecisionPanel'

const Layout = styled.div`display:grid;grid-template-columns:260px minmax(360px,1fr) 300px;border-block:1px solid #dfe5e2;min-height:520px;@media(max-width:1000px){grid-template-columns:220px 1fr}.decision{border-left:1px solid #dfe5e2;padding:18px}@media(max-width:1000px){.decision{grid-column:1/-1;border-left:0;border-top:1px solid #dfe5e2}}`
const Pane = styled.div`padding:18px;min-width:0;`

function display(value: unknown) { return typeof value === 'string' ? value : JSON.stringify(value ?? '') }

export function GovernanceConfirmationPage({ taskId }: { taskId: number }) {
  const queryClient = useQueryClient()
  const confirmation = useQuery({ queryKey: ['governance-confirmation', taskId], queryFn: () => getCurrentConfirmation(taskId) })
  const executions = useQuery({ queryKey: ['governance-items', taskId], queryFn: () => getGovernanceItems(taskId) })
  const [currentId, setCurrentId] = useState<number>()
  useEffect(() => { if (!currentId && confirmation.data?.items.length) setCurrentId(confirmation.data.items[0].itemId) }, [confirmation.data, currentId])
  const current = confirmation.data?.items.find(item => item.itemId === currentId)
  const execution = executions.data?.find(item => item.item.id === currentId)
  const currentDecision = confirmation.data?.decisions.find(item => item.itemId === currentId)
  const refresh = () => confirmation.refetch()
  const save = useMutation({ mutationFn: (values: { decision: 'APPROVED' | 'REJECTED'; comment?: string }) => saveConfirmationDecision(confirmation.data!.round.id, current!.itemId, { ...values, decisionVersion: currentDecision?.version ?? 0, confirmerUserId: current!.responsibleUserId || 'demo-user' }), onSuccess: result => queryClient.setQueryData(['governance-confirmation', taskId], result) })
  const complete = useMutation({ mutationFn: () => completeConfirmation(taskId, confirmation.data!.round.id, confirmation.data!.round.version), onSuccess: refresh })
  const sameScopeIds = useMemo(() => confirmation.data?.items.filter(item => item.responsibilityScope === current?.responsibilityScope && !confirmation.data?.decisions.some(decision => decision.itemId === item.itemId)).map(item => item.itemId) ?? [], [confirmation.data, current])
  const batchApprove = async () => { for (const itemId of sameScopeIds) { const item = confirmation.data!.items.find(value => value.itemId === itemId)!; await saveConfirmationDecision(confirmation.data!.round.id, itemId, { decision: 'APPROVED', decisionVersion: 0, confirmerUserId: item.responsibleUserId || 'demo-user' }) } await refresh() }
  if (confirmation.isLoading || executions.isLoading) return <Spin tip="正在加载业务确认"><div style={{ height: 320 }} /></Spin>
  if (!confirmation.data || !current) return <Alert type="error" showIcon message="业务确认加载失败" />
  return <article><Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 14 }}><div><Typography.Title level={3} style={{ margin: 0 }}>业务确认</Typography.Title><Typography.Text type="secondary">确认覆盖率 {confirmation.data.coveredCount} / {confirmation.data.items.length}</Typography.Text></div><Space><Button onClick={() => void batchApprove()}>同责任范围批量通过</Button><Button type="primary" icon={<CheckOutlined aria-hidden />} disabled={confirmation.data.coveredCount !== confirmation.data.items.length} loading={complete.isPending} onClick={() => complete.mutate()}>完成确认轮次</Button></Space></Space>
    {(save.error || complete.error) && <Alert type="error" showIcon message={(save.error ?? complete.error)?.message} style={{ marginBottom: 12 }} />}
    <Layout><List dataSource={confirmation.data.items} renderItem={item => <List.Item onClick={() => setCurrentId(item.itemId)} style={{ paddingInline: 14, cursor: 'pointer', background: item.itemId === currentId ? '#edf4f1' : undefined }}><List.Item.Meta title={`资产 #${item.assetId}`} description={confirmation.data!.decisions.some(value => value.itemId === item.itemId) ? '已决定' : '待决定'} /></List.Item>} />
      <Pane><Typography.Title level={4}>字段核对</Typography.Title><Descriptions column={1} bordered size="small" items={[{ key: 'original', label: '原值', children: execution?.originalFactJson ?? '-' }, { key: 'proposed', label: '拟值', children: display(execution?.currentResult?.proposedValue) }, { key: 'rule', label: '规则来源', children: display(execution?.ruleContext) }, { key: 'actor', label: '执行人', children: execution?.currentResult?.actorUserId ?? current.responsibleUserId }]} /></Pane>
      <div className="decision"><ConfirmationDecisionPanel decision={currentDecision} loading={save.isPending} onSave={values => save.mutate(values)} /></div></Layout>
  </article>
}
