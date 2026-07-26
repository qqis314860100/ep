import { Cascader, Input, Select, Typography } from 'antd'

import type { GovernanceEmployee, GovernanceField, JsonValue } from '../types'

const fieldLabels: Record<GovernanceField, string> = { DESCRIPTION: '功能说明', SPECIALTIES: '专业类别', OWNER: '责任人', SCOPE: '适用范围' }

function stringList(value: JsonValue): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : typeof value === 'string' && value ? [value] : []
}

export function FieldResultEditor({ field, originalValue, value, employees, ruleContext, disabled, onChange }: {
  field: GovernanceField
  originalValue: string
  value: JsonValue
  employees: GovernanceEmployee[]
  ruleContext: JsonValue
  disabled?: boolean
  onChange: (value: JsonValue) => void
}) {
  const label = fieldLabels[field]
  const context = ruleContext && typeof ruleContext === 'object' && !Array.isArray(ruleContext) ? ruleContext : {}
  const dictionaryValues = Array.isArray(context.dictionaryValues) ? context.dictionaryValues.filter((item): item is string => typeof item === 'string') : stringList(value)
  const scopeOptions = Array.isArray(context.scopeOptions) ? context.scopeOptions : []
  return <section>
    <Typography.Title level={5}>原始{label}</Typography.Title>
    <Typography.Paragraph code style={{ whiteSpace: 'pre-wrap' }}>{originalValue || '（空）'}</Typography.Paragraph>
    <Typography.Title level={5}>拟变更{label}</Typography.Title>
    {field === 'DESCRIPTION' && <Input.TextArea aria-label="拟变更功能说明" autoSize={{ minRows: 5, maxRows: 12 }} value={typeof value === 'string' ? value : ''} disabled={disabled} onChange={event => onChange(event.target.value)} />}
    {field === 'SPECIALTIES' && <Select aria-label="拟变更专业类别" mode="multiple" style={{ width: '100%' }} value={stringList(value)} options={dictionaryValues.map(item => ({ label: item, value: item }))} disabled={disabled} onChange={onChange} />}
    {field === 'OWNER' && <Select aria-label="拟变更责任人" showSearch optionFilterProp="label" style={{ width: '100%' }} value={typeof value === 'string' ? value : undefined} options={employees.map(item => ({ label: `${item.name} · ${item.department}`, value: item.id }))} disabled={disabled} onChange={onChange} />}
    {field === 'SCOPE' && <Cascader aria-label="拟变更适用范围" multiple style={{ width: '100%' }} value={Array.isArray(value) ? value as string[][] : []} options={scopeOptions as never[]} disabled={disabled} onChange={next => onChange(next as JsonValue)} />}
  </section>
}
