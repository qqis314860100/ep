import { Input, Radio, Table, Typography } from 'antd'
import type { AcceptanceSample } from '../types'

export function AcceptanceSampleTable({ samples, selectedIds, onSelect, onChange }: { samples: AcceptanceSample[]; selectedIds: number[]; onSelect: (ids: number[]) => void; onChange: (sample: AcceptanceSample, passed: boolean, issueDescription: string) => void }) {
  return <Table rowKey="itemId" size="small" pagination={false} dataSource={samples} rowSelection={{ selectedRowKeys: selectedIds, onChange: keys => onSelect(keys.map(Number)) }} columns={[
    { title: '治理项', dataIndex: 'itemId', width: 100 },
    { title: '抽样结果', dataIndex: 'passed', width: 180, render: (_, sample) => <Radio.Group value={sample.passed} onChange={event => onChange(sample, event.target.value, sample.issueDescription)}><Radio value={true}>通过</Radio><Radio value={false}>不通过</Radio></Radio.Group> },
    { title: '问题说明', render: (_, sample) => sample.passed === false ? <Input aria-label={`问题说明 ${sample.itemId}`} value={sample.issueDescription} onChange={event => onChange(sample, false, event.target.value)} /> : <Typography.Text type="secondary">{sample.issueDescription || '无'}</Typography.Text> },
  ]} />
}
