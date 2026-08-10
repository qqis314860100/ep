import { FilterOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Col, Empty, Form, Input, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import styled from 'styled-components'
import { getGovernanceEmployees, getGovernanceOperationsOverview, getGovernanceStandards } from '../api'
import type { GovernanceAssetType, GovernanceOperationsFilter, GovernanceOperationsMetric } from '../types'

const Header = styled.header`display:flex;align-items:flex-end;justify-content:space-between;gap:16px;margin-bottom:14px;@media(max-width:760px){align-items:stretch;flex-direction:column;}`
const FilterBar = styled.div`padding:14px;background:#fff;border:1px solid #dfe5e2;border-radius:4px;margin-bottom:14px;`
const MetricGrid = styled.div`display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:1px;background:#dfe5e2;border:1px solid #dfe5e2;border-radius:4px;overflow:hidden;margin-bottom:14px;@media(max-width:900px){grid-template-columns:repeat(2,minmax(0,1fr));}@media(max-width:520px){grid-template-columns:1fr;}`
const MetricCell = styled.div`min-height:78px;padding:12px 14px;background:#fff;.ant-statistic-title{font-size:11px}.ant-statistic-content{font-size:21px}`
const Section = styled.section`min-width:0;background:#fff;border:1px solid #dfe5e2;border-radius:4px;padding:14px;margin-bottom:14px;`
const SectionTitle = styled.div`display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px;color:#34423c;font-size:13px;font-weight:700;`

const assetTypeLabels: Record<GovernanceAssetType, string> = { THREE_DIMENSIONAL_MODEL: '三维模型', TWO_DIMENSIONAL_DRAWING: '二维图纸', MIXED_ASSET: '混合资产', OTHER: '其他资料' }
const cadenceMeta: Record<string, { label: string; color: string }> = { ON_TRACK: { label: '正常', color: 'success' }, DUE: { label: '待处理', color: 'warning' }, PLANNED: { label: '计划中', color: 'processing' } }
const metricOrder = ['responsibilityCoverage', 'issueClosureCycle', 'recurrenceRate', 'automatedTreatmentRate', 'firstConfirmationPassRate', 'acceptancePassRate', 'reworkRate', 'applicationSuccessRate']

function metricDisplay(metric: GovernanceOperationsMetric) {
  if (!metric.available || metric.value === null) return '暂无数据'
  if (metric.unit === '%') return `${(metric.value * 100).toFixed(1)}%`
  return `${metric.value.toFixed(1)} ${metric.unit}`
}

export function GovernanceOperationsPage() {
  const [form] = Form.useForm<GovernanceOperationsFilter>()
  const [filters, setFilters] = useState<GovernanceOperationsFilter>({})
  const standards = useQuery({ queryKey: ['governance-standards'], queryFn: getGovernanceStandards, staleTime: 300_000 })
  const employees = useQuery({ queryKey: ['governance-employees'], queryFn: getGovernanceEmployees, staleTime: 300_000 })
  const overview = useQuery({ queryKey: ['governance-operations', filters], queryFn: () => getGovernanceOperationsOverview(filters) })
  const metrics = useMemo(() => new Map((overview.data?.metrics ?? []).map(metric => [metric.key, metric])), [overview.data])
  const issueColumns: ColumnsType<{ key: string; count: number }> = [{ title: '问题类型', dataIndex: 'key' }, { title: '数量', dataIndex: 'count', width: 100 }]
  const riskColumns: ColumnsType<NonNullable<typeof overview.data>['overdueTasks'][number]> = [
    { title: '任务', dataIndex: 'taskName' }, { title: '负责人', dataIndex: 'ownerName', width: 100 }, { title: '截止日期', dataIndex: 'dueDate', width: 120 }, { title: '状态', dataIndex: 'status', width: 130 },
  ]
  const cadenceColumns: ColumnsType<NonNullable<typeof overview.data>['cadences'][number]> = [
    { title: '节奏', dataIndex: 'name' }, { title: '责任角色', dataIndex: 'ownerRole', width: 120 }, { title: '状态', dataIndex: 'status', width: 90, render: value => <Tag color={cadenceMeta[value]?.color}>{cadenceMeta[value]?.label ?? value}</Tag> }, { title: '下一节点', dataIndex: 'nextDueAt', width: 180 }, { title: '依据', dataIndex: 'evidence' },
  ]
  const submit = (values: GovernanceOperationsFilter) => setFilters(Object.fromEntries(Object.entries(values).filter(([, value]) => value !== undefined && value !== '')))
  return <section>
    <Header><div><Typography.Title level={3} style={{ margin: 0 }}>治理运营</Typography.Title><Typography.Text type="secondary">从问题发现到验收应用，按责任、标准和风险推动常态治理</Typography.Text></div><Button icon={<ReloadOutlined />} onClick={() => void overview.refetch()}>刷新</Button></Header>
    <FilterBar><Form form={form} layout="vertical" onFinish={submit}><Row gutter={[12, 0]}><Col xs={24} sm={12} md={6}><Form.Item name="standardCode" label="数据标准"><Select allowClear placeholder="全部标准" options={(standards.data ?? []).map(item => ({ value: item.standardCode, label: `${item.standardCode} · V${item.standardVersion}` }))} /></Form.Item></Col><Col xs={24} sm={12} md={6}><Form.Item name="issueType" label="问题类型"><Select allowClear placeholder="全部问题" options={(overview.data?.issuesByType ?? []).map(item => ({ value: item.key, label: item.key }))} /></Form.Item></Col><Col xs={24} sm={12} md={6}><Form.Item name="ownerUserId" label="责任人"><Select allowClear placeholder="全部责任人" options={(employees.data ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item></Col><Col xs={24} sm={12} md={6}><Form.Item name="assetType" label="资产类型"><Select allowClear placeholder="全部类型" options={Object.entries(assetTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item></Col><Col xs={24} sm={12} md={6}><Form.Item name="base" label="基地"><Input allowClear placeholder="输入基地" /></Form.Item></Col><Col xs={12} sm={6} md={4}><Form.Item name="fromDate" label="开始日期"><Input type="date" /></Form.Item></Col><Col xs={12} sm={6} md={4}><Form.Item name="toDate" label="结束日期"><Input type="date" /></Form.Item></Col><Col xs={24} md={4} style={{ display: 'flex', alignItems: 'end', paddingBottom: 24 }}><Space><Button type="primary" icon={<FilterOutlined />} htmlType="submit">应用筛选</Button><Button onClick={() => { form.resetFields(); setFilters({}) }}>重置</Button></Space></Col></Row></Form></FilterBar>
    {overview.isError && <Alert type="error" showIcon message="运营指标加载失败" style={{ marginBottom: 14 }} />}
    <MetricGrid>{metricOrder.map(key => { const metric = metrics.get(key); return <MetricCell key={key}><Statistic title={metric?.label ?? key} value={metric ? metricDisplay(metric) : '加载中'} /></MetricCell> })}</MetricGrid>
    {overview.data && <Typography.Text type="secondary" style={{ display: 'block', margin: '-5px 0 14px' }}>生成时间：{new Date(overview.data.generatedAt).toLocaleString('zh-CN', { hour12: false })} · 指标来源均为平台治理事实</Typography.Text>}
    <Row gutter={14}><Col xs={24} lg={9}><Section><SectionTitle><span>开放问题分布</span><Tag>{overview.data?.openIssueCount ?? 0} 个开放问题</Tag></SectionTitle><Table rowKey="key" size="small" pagination={false} locale={{ emptyText: <Empty description="暂无问题" /> }} columns={issueColumns} dataSource={overview.data?.issuesByType ?? []} /></Section></Col><Col xs={24} lg={15}><Section><SectionTitle><span>逾期任务</span><Tag color={overview.data?.overdueTaskCount ? 'warning' : 'success'}>{overview.data?.overdueTaskCount ?? 0}</Tag></SectionTitle><Table rowKey="taskId" size="small" pagination={false} scroll={{ x: 560 }} locale={{ emptyText: <Empty description="暂无逾期任务" /> }} columns={riskColumns} dataSource={overview.data?.overdueTasks ?? []} /></Section></Col></Row>
    <Section><SectionTitle><span>治理节奏</span><Typography.Text type="secondary">每日扫描、每周分派、每月复盘、季度评审</Typography.Text></SectionTitle><Table rowKey="key" size="small" pagination={false} scroll={{ x: 760 }} columns={cadenceColumns} dataSource={overview.data?.cadences ?? []} /></Section>
    {overview.data?.metrics.find(metric => metric.key === 'issueClosureCycle' && !metric.available) && <Alert type="info" showIcon message="平均问题关闭周期暂不可用" description={overview.data.metrics.find(metric => metric.key === 'issueClosureCycle')?.source} />}
  </section>
}

export default GovernanceOperationsPage
