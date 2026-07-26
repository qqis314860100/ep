import { CheckOutlined, RollbackOutlined } from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Input, Space, Spin, Typography } from 'antd'
import { useEffect, useState } from 'react'
import styled from 'styled-components'
import { completeAcceptance, getCurrentAcceptance, getOperationJob, openGovernanceRework, retryOperationJob, saveAcceptanceSample } from '../api'
import type { AcceptanceSample } from '../types'
import { AcceptanceSampleTable } from './AcceptanceSampleTable'
import { ApplicationJobProgress } from './ApplicationJobProgress'
import { QualityMetricTable } from './QualityMetricTable'

const Section = styled.section`padding:16px 0;border-top:1px solid #dfe5e2;`
type Completion = { taskStatus: string; affectedItemIds: number[]; applicationJobId: number | null }

export function GovernanceAcceptancePage({ taskId }: { taskId: number }) {
  const acceptance = useQuery({ queryKey: ['governance-acceptance', taskId], queryFn: () => getCurrentAcceptance(taskId) })
  const [samples, setSamples] = useState<AcceptanceSample[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [reason, setReason] = useState('')
  const [jobId, setJobId] = useState<number>()
  useEffect(() => setSamples(acceptance.data?.samples ?? []), [acceptance.data])
  const job = useQuery({ queryKey: ['governance-job', jobId], queryFn: () => getOperationJob(jobId!), enabled: Boolean(jobId), refetchInterval: query => { const value = query.state.data; return value && value.processing === 0 ? false : 500 } })
  const sampleMutation = useMutation({ mutationFn: (sample: AcceptanceSample) => saveAcceptanceSample(acceptance.data!.id, sample.itemId, { passed: sample.passed!, issueDescription: sample.issueDescription, reviewerUserId: 'demo-user', sampleVersion: sample.version }), onSuccess: () => acceptance.refetch() })
  const complete = useMutation({ mutationFn: () => completeAcceptance(taskId, acceptance.data!.id, { roundVersion: acceptance.data!.version, operatorUserId: 'demo-user' }) as Promise<Completion>, onSuccess: result => { if (result.applicationJobId) setJobId(result.applicationJobId); void acceptance.refetch() } })
  const rework = useMutation({ mutationFn: () => openGovernanceRework(taskId, { taskVersion: 0, reason, actorUserId: 'demo-user' }) })
  const retry = useMutation({ mutationFn: () => retryOperationJob(jobId!), onSuccess: () => job.refetch() })
  const updateSample = (sample: AcceptanceSample, passed: boolean, issueDescription: string) => { const next = { ...sample, passed, issueDescription }; setSamples(values => values.map(value => value.itemId === sample.itemId ? next : value)); if (passed || issueDescription.trim()) sampleMutation.mutate(next) }
  if (acceptance.isLoading) return <Spin tip="正在加载质量验收"><div style={{ height: 320 }} /></Spin>
  if (!acceptance.data) return <Alert type="error" showIcon message="质量验收加载失败" />
  return <article><Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 14 }}><div><Typography.Title level={3} style={{ margin: 0 }}>质量验收</Typography.Title><Typography.Text type="secondary">固定指标与抽样结果共同决定是否可正式应用</Typography.Text></div><Button type="primary" icon={<CheckOutlined aria-hidden />} loading={complete.isPending} onClick={() => complete.mutate()}>通过并正式应用</Button></Space>
    {(complete.error || sampleMutation.error || rework.error) && <Alert type="error" showIcon message={(complete.error ?? sampleMutation.error ?? rework.error)?.message} style={{ marginBottom: 12 }} />}
    <Section><Typography.Title level={4}>质量指标</Typography.Title><QualityMetricTable metrics={acceptance.data.metricResults} /></Section>
    <Section><Typography.Title level={4}>固定验收抽样</Typography.Title><AcceptanceSampleTable samples={samples} selectedIds={selectedIds} onSelect={setSelectedIds} onChange={updateSample} /></Section>
    <Section><Typography.Title level={4}>验收退回</Typography.Title><Space.Compact style={{ width: '100%' }}><Input aria-label="返工原因" value={reason} onChange={event => setReason(event.target.value)} placeholder="填写返工原因并选择受影响项" /><Button icon={<RollbackOutlined aria-hidden />} disabled={!reason.trim() || !selectedIds.length} loading={rework.isPending} onClick={() => rework.mutate()}>退回选中项</Button></Space.Compact></Section>
    {job.data && <Section><ApplicationJobProgress job={job.data} retrying={retry.isPending} onRetry={() => retry.mutate()} /></Section>}
  </article>
}
