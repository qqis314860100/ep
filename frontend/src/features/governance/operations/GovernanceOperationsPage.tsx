import { FilterOutlined, ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Col, Empty, Form, Input, Row, Select, Space, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import styled from 'styled-components'
import { FilterGrid } from '../../../components/FilterGrid'
import { getGovernanceEmployees, getGovernanceOperationsOverview, getGovernanceStandards } from '../api'
import type { GovernanceAssetType, GovernanceOperationsFilter, GovernanceOperationsMetric } from '../types'
import {
  GovernanceMetric,
  GovernanceMetricGrid,
  GovernancePage,
  GovernancePageHeader,
  GovernancePanel,
  GovernancePanelSection,
} from '../components/GovernanceLayout'

const FilterActions = styled.div`align-self:end;`

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
  return <GovernancePage>
    <GovernancePageHeader
      title="治理运营"
      subtitle="从问题发现到验收应用，按责任、标准和风险推动常态治理"
      actions={<Button icon={<ReloadOutlined />} onClick={() => void overview.refetch()}>刷新</Button>}
    />
    <GovernancePanel>
      <Form form={form} layout="vertical" onFinish={submit}>
        <FilterGrid>
          <Form.Item name="standardCode" label="数据标准"><Select allowClear placeholder="全部标准" options={(standards.data ?? []).map(item => ({ value: item.standardCode, label: `${item.standardCode} · V${item.standardVersion}` }))} /></Form.Item>
          <Form.Item name="issueType" label="问题类型"><Select allowClear placeholder="全部问题" options={(overview.data?.issuesByType ?? []).map(item => ({ value: item.key, label: item.key }))} /></Form.Item>
          <Form.Item name="ownerUserId" label="责任人"><Select allowClear placeholder="全部责任人" options={(employees.data ?? []).map(item => ({ value: item.id, label: item.name }))} /></Form.Item>
          <Form.Item name="assetType" label="资产类型"><Select allowClear placeholder="全部类型" options={Object.entries(assetTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="base" label="基地"><Input allowClear placeholder="输入基地" /></Form.Item>
          <Form.Item name="fromDate" label="开始日期"><Input type="date" /></Form.Item>
          <Form.Item name="toDate" label="结束日期"><Input type="date" /></Form.Item>
          <FilterActions><Space><Button type="primary" icon={<FilterOutlined />} htmlType="submit">应用筛选</Button><Button onClick={() => { form.resetFields(); setFilters({}) }}>重置</Button></Space></FilterActions>
        </FilterGrid>
      </Form>
    </GovernancePanel>
    {overview.isError && <Alert type="error" showIcon message="运营指标加载失败" />}
    <GovernanceMetricGrid>
      {metricOrder.map(key => {
        const metric = metrics.get(key)
        return <GovernanceMetric key={key} label={metric?.label ?? key} value={metric ? metricDisplay(metric) : '加载中'} />
      })}
    </GovernanceMetricGrid>
    {overview.data && <Typography.Text type="secondary">生成时间：{new Date(overview.data.generatedAt).toLocaleString('zh-CN', { hour12: false })} · 指标来源均为平台治理事实</Typography.Text>}
    <Row gutter={[16, 16]}>
      <Col xs={24} lg={9}><GovernancePanelSection title="开放问题分布" extra={<Tag>{overview.data?.openIssueCount ?? 0} 个开放问题</Tag>}><Table rowKey="key" size="small" pagination={false} locale={{ emptyText: <Empty description="暂无问题" /> }} columns={issueColumns} dataSource={overview.data?.issuesByType ?? []} /></GovernancePanelSection></Col>
      <Col xs={24} lg={15}><GovernancePanelSection title="逾期任务" extra={<Tag color={overview.data?.overdueTaskCount ? 'warning' : 'success'}>{overview.data?.overdueTaskCount ?? 0}</Tag>}><Table rowKey="taskId" size="small" pagination={false} scroll={{ x: 560 }} locale={{ emptyText: <Empty description="暂无逾期任务" /> }} columns={riskColumns} dataSource={overview.data?.overdueTasks ?? []} /></GovernancePanelSection></Col>
    </Row>
    <GovernancePanelSection title="治理节奏" extra={<Typography.Text type="secondary">每日扫描、每周分派、每月复盘、季度评审</Typography.Text>}><Table rowKey="key" size="small" pagination={false} scroll={{ x: 760 }} columns={cadenceColumns} dataSource={overview.data?.cadences ?? []} /></GovernancePanelSection>
    {overview.data?.metrics.find(metric => metric.key === 'issueClosureCycle' && !metric.available) && <Alert type="info" showIcon message="平均问题关闭周期暂不可用" description={overview.data.metrics.find(metric => metric.key === 'issueClosureCycle')?.source} />}
  </GovernancePage>
}

export default GovernanceOperationsPage
