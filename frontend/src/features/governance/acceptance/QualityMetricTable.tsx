import { Table, Tag } from 'antd'
import type { AcceptanceMetricResult, GovernanceQualityMetric } from '../types'

const labels: Record<GovernanceQualityMetric, string> = { REQUIRED_FIELD_COMPLETENESS: '必填字段完整率', ASSET_SCOPE_VALIDITY: '适用范围有效率', STANDARD_DICTIONARY_HIT_RATE: '标准字典命中率', OWNER_COVERAGE: '负责人覆盖率', SAMPLE_ACCURACY: '抽样准确率' }

export function QualityMetricTable({ metrics }: { metrics: AcceptanceMetricResult[] }) {
  return <Table rowKey="id" size="small" pagination={false} dataSource={metrics} columns={[
    { title: '质量指标', dataIndex: 'metric', render: (value: GovernanceQualityMetric) => labels[value] },
    { title: '分子 / 分母', render: (_, item) => `${item.numerator} / ${item.denominator}` },
    { title: '阈值', dataIndex: 'threshold', render: value => `${Math.round(Number(value) * 100)}%` },
    { title: '适用性', dataIndex: 'applicability', render: value => value === 'APPLICABLE' ? '适用' : '不适用' },
    { title: '结果', dataIndex: 'passed', render: value => <Tag color={value ? 'success' : 'error'}>{value ? '通过' : '未通过'}</Tag> },
  ]} />
}
