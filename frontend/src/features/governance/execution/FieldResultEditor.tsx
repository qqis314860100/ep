import { Cascader, Input, Select, Typography } from 'antd'
import type { DefaultOptionType } from 'antd/es/cascader'

import type { GovernanceEmployee, GovernanceField, JsonValue } from '../types'
import type { DictionaryItem } from '../../../types/dictionary'

const fieldLabels: Record<GovernanceField, string> = { DESCRIPTION: '功能说明', SPECIALTIES: '专业类别', OWNER: '责任人', SCOPE: '适用范围' }

function numberList(value: JsonValue): number[] {
  return Array.isArray(value) ? value.filter((item): item is number => typeof item === 'number') : []
}

export function FieldResultEditor({ field, originalValue, value, employees, dictionaryItems, ruleContext, disabled, onChange }: {
  field: GovernanceField
  originalValue: string
  value: JsonValue
  employees: GovernanceEmployee[]
  dictionaryItems: DictionaryItem[]
  ruleContext: JsonValue
  disabled?: boolean
  onChange: (value: JsonValue) => void
}) {
  const label = fieldLabels[field]
  const context = ruleContext && typeof ruleContext === 'object' && !Array.isArray(ruleContext) ? ruleContext : {}
  const scopeOptions = Array.isArray(context.scopeOptions) ? context.scopeOptions as DefaultOptionType[] : []
  return <section>
    <Typography.Title level={5}>原始{label}</Typography.Title>
    <Typography.Paragraph code style={{ whiteSpace: 'pre-wrap' }}>{originalValue || '（空）'}</Typography.Paragraph>
    <Typography.Title level={5}>拟变更{label}</Typography.Title>
    {field === 'DESCRIPTION' && <Input.TextArea aria-label="拟变更功能说明" autoSize={{ minRows: 5, maxRows: 12 }} value={typeof value === 'string' ? value : ''} disabled={disabled} onChange={event => onChange(event.target.value)} />}
    {field === 'SPECIALTIES' && <Select aria-label="拟变更专业类别" mode="multiple" style={{ width: '100%' }} value={numberList(value)} options={dictionaryItems.filter(item => item.category === 'SPECIALTY' && item.status === 'ENABLED').map(item => ({ label: item.name, value: item.id }))} disabled={disabled} onChange={onChange} />}
    {field === 'OWNER' && <Select aria-label="拟变更责任人" showSearch optionFilterProp="label" style={{ width: '100%' }} value={typeof value === 'string' ? value : undefined} options={employees.map(item => ({ label: `${item.name} · ${item.department}`, value: item.id }))} disabled={disabled} onChange={onChange} />}
    {field === 'SCOPE' && <Cascader aria-label="拟变更适用范围" multiple style={{ width: '100%' }} value={Array.isArray(value) ? value as string[][] : []} options={scopeOptions} disabled={disabled} onChange={next => onChange(next as JsonValue)} />}
  </section>
}
