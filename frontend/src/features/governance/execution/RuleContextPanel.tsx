import { Descriptions, Timeline, Typography } from 'antd'

import type { GovernanceItemExecution } from '../types'

function objectValue(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

export function RuleContextPanel({ execution }: { execution: GovernanceItemExecution }) {
  const context = objectValue(execution.ruleContext)
  const standardVersion = execution.currentResult?.standardVersion ?? context.standardVersion ?? '-'
  const dictionaryVersions = execution.currentResult?.dictionaryVersions ?? objectValue(context.dictionaryVersions)
  const history = Array.isArray(context.history) ? context.history.map(String) : []
  return <aside>
    <Typography.Title level={5}>规则与范围</Typography.Title>
    <Descriptions size="small" column={1} items={[
      { key: 'standard', label: '标准版本', children: String(standardVersion) },
      { key: 'dictionary', label: '字典版本', children: Object.keys(dictionaryVersions).length ? JSON.stringify(dictionaryVersions) : '-' },
      { key: 'scope', label: '同一范围', children: String(context.scope ?? execution.item.scopeFingerprint) },
    ]} />
    <Typography.Title level={5}>历史结果</Typography.Title>
    {history.length ? <Timeline items={history.map(item => ({ children: item }))} /> : <Typography.Text type="secondary">暂无历史结果</Typography.Text>}
    {(execution.currentResult?.reworkReason || execution.blockReason) && <><Typography.Title level={5}>退回意见</Typography.Title><Typography.Paragraph type="danger">{execution.currentResult?.reworkReason || execution.blockReason}</Typography.Paragraph></>}
  </aside>
}
