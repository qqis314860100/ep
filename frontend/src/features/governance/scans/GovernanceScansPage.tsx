import { PlayCircleOutlined, ReloadOutlined, RedoOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Descriptions, Empty, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import styled from 'styled-components'
import { getGovernanceScanRuns, retryGovernanceScan, triggerGovernanceScan } from '../api'
import type { GovernanceScanRun, GovernanceScanRunStatus, GovernanceScanTriggerType } from '../types'

const Header = styled.header`display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:14px;@media(max-width:720px){align-items:stretch;flex-direction:column;}`
const Metrics = styled.div`display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;margin-bottom:14px;background:#dfe5e2;border:1px solid #dfe5e2;border-radius:4px;overflow:hidden;@media(max-width:700px){grid-template-columns:repeat(2,1fr);}`
const Metric = styled.div`padding:12px 14px;background:#fff;.ant-statistic-title{font-size:11px;}.ant-statistic-content{font-size:20px;}`

const statusMeta: Record<GovernanceScanRunStatus, { label: string; color: string }> = { RUNNING: { label: '运行中', color: 'processing' }, SUCCEEDED: { label: '成功', color: 'success' }, FAILED: { label: '失败', color: 'error' } }
const triggerLabels: Record<GovernanceScanTriggerType, string> = { MANUAL: '手动触发', SCHEDULED: '计划触发', RETRY: '失败重试' }
const time = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '进行中'

export function GovernanceScansPage() {
  const { message } = App.useApp(); const client = useQueryClient(); const query = useQuery({ queryKey: ['governance-scan-runs'], queryFn: getGovernanceScanRuns })
  const refresh = () => client.invalidateQueries({ queryKey: ['governance-scan-runs'] })
  const trigger = useMutation({ mutationFn: () => triggerGovernanceScan(), onSuccess: async run => { await refresh(); void message.success(run.status === 'SUCCEEDED' ? `扫描完成，发现 ${run.createdIssueCount + run.reopenedIssueCount} 个新增或重开问题` : '扫描运行失败') }, onError: error => void message.error(error instanceof Error ? error.message : '扫描触发失败') })
  const retry = useMutation({ mutationFn: (id: number) => retryGovernanceScan(id), onSuccess: async () => { await refresh(); void message.success('扫描已重试') }, onError: error => void message.error(error instanceof Error ? error.message : '重试失败') })
  const latest = query.data?.[0]
  const totals = query.data?.reduce((result, run) => ({ scanned: result.scanned + run.scannedAssetCount, created: result.created + run.createdIssueCount, reopened: result.reopened + run.reopenedIssueCount }), { scanned: 0, created: 0, reopened: 0 }) ?? { scanned: 0, created: 0, reopened: 0 }
  const columns: ColumnsType<GovernanceScanRun> = [
    { title: '运行时间', dataIndex: 'startedAt', width: 180, render: (value, row) => <span>{time(value)}<br /><Typography.Text type="secondary">#{row.id}</Typography.Text></span> },
    { title: '触发方式', dataIndex: 'triggerType', width: 110, render: (value: GovernanceScanTriggerType) => triggerLabels[value] },
    { title: '状态', dataIndex: 'status', width: 90, render: (value: GovernanceScanRunStatus) => <Tag color={statusMeta[value].color}>{statusMeta[value].label}</Tag> },
    { title: '扫描资产', dataIndex: 'scannedAssetCount', width: 100 }, { title: '新建问题', dataIndex: 'createdIssueCount', width: 100 }, { title: '重开问题', dataIndex: 'reopenedIssueCount', width: 100 }, { title: '未变化', dataIndex: 'unchangedIssueCount', width: 90 },
    { title: '操作', key: 'actions', width: 100, render: (_, row) => row.status === 'FAILED' ? <Button size="small" icon={<RedoOutlined />} onClick={() => retry.mutate(row.id)} loading={retry.isPending}>重试</Button> : null },
  ]
  return <section><Header><div><Typography.Title level={3} style={{ margin: 0 }}>自动问题扫描</Typography.Title><Typography.Text type="secondary">按当前启用标准发现缺失、非法、重复和失效责任问题，扫描不会直接改写资产</Typography.Text></div><Space wrap><Button icon={<ReloadOutlined />} onClick={() => void refresh()}>刷新</Button><Button type="primary" icon={<PlayCircleOutlined />} loading={trigger.isPending} onClick={() => trigger.mutate()}>立即扫描</Button></Space></Header>
    {query.isError && <Alert type="error" showIcon message="扫描记录加载失败" style={{ marginBottom: 14 }} />}
    <Metrics><Metric><Statistic title="累计扫描资产" value={totals.scanned} /></Metric><Metric><Statistic title="新建问题" value={totals.created} /></Metric><Metric><Statistic title="重开问题" value={totals.reopened} /></Metric><Metric><Statistic title="最近运行" value={latest ? time(latest.startedAt) : '尚未运行'} /></Metric></Metrics>
    {latest?.status === 'FAILED' && <Alert type="error" showIcon message="最近一次扫描失败" description={latest.errorMessage || '请重试扫描'} style={{ marginBottom: 14 }} />}
    {latest && <Descriptions size="small" bordered column={{ xs: 1, sm: 2, md: 4 }} style={{ marginBottom: 14 }} items={[{ key: 'standard', label: '运行状态', children: statusMeta[latest.status].label }, { key: 'finished', label: '结束时间', children: time(latest.finishedAt) }, { key: 'unchanged', label: '未变化问题', children: latest.unchangedIssueCount }, { key: 'retry', label: '来源运行', children: latest.retryOfRunId ? `#${latest.retryOfRunId}` : '无' }]} />}
    <Table rowKey="id" size="small" loading={query.isLoading} columns={columns} dataSource={query.data ?? []} scroll={{ x: 920 }} pagination={{ pageSize: 10, showSizeChanger: false }} locale={{ emptyText: <Empty description="尚未运行扫描" /> }} />
  </section>
}

export default GovernanceScansPage
