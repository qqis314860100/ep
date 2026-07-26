import { Alert, Button, Progress, Space, Table, Typography } from 'antd'
import type { OperationJob } from '../types'

export function ApplicationJobProgress({ job, retrying, onRetry }: { job: OperationJob; retrying: boolean; onRetry: () => void }) {
  const finished = job.succeeded + job.failed
  const errors = Object.entries(job.errors).map(([itemId, reason]) => ({ itemId, reason }))
  return <section><Alert type={job.failed ? 'warning' : job.succeeded === job.total ? 'success' : 'info'} showIcon message={job.succeeded === job.total ? '正式应用完成' : '验收通过，正在归档'} style={{ marginBottom: 12 }} />
    <Space style={{ width: '100%', justifyContent: 'space-between' }}><Typography.Text strong>{job.succeeded} / {job.total} 已应用</Typography.Text>{job.retryable && <Button loading={retrying} onClick={onRetry}>重试失败项</Button>}</Space>
    <Progress percent={job.total ? Math.round(finished / job.total * 100) : 0} status={job.failed ? 'exception' : 'active'} />
    {errors.length > 0 && <Table rowKey="itemId" size="small" pagination={false} dataSource={errors} columns={[{ title: '治理项', dataIndex: 'itemId' }, { title: '失败原因', dataIndex: 'reason' }]} />}
  </section>
}
