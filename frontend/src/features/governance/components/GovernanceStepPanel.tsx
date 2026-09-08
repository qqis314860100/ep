import { Button, Empty, List, Tooltip, Typography } from 'antd'
import styled from 'styled-components'
import { railTheme } from './railTheme'

export type GovernanceTodoRowKind = 'task' | 'issue' | 'scan'
export type GovernancePillTone = 'danger' | 'warning' | 'success' | 'info' | 'plain'

export interface GovernanceTodoPill {
  label: string
  tone: GovernancePillTone
}

export interface GovernanceTodoRow {
  key: string
  kind: GovernanceTodoRowKind
  title: string
  description: string
  pill?: GovernanceTodoPill
  owner?: string
  /** 已格式化的到期日文本 */
  due?: string
  dueTone?: 'overdue' | 'soon'
  overdue?: boolean
  onClick?: () => void
}

interface GovernanceTodoPanelProps {
  title: string
  hint?: string
  rows: GovernanceTodoRow[]
  loading?: boolean
  emptyText: string
  emptyAction?: { label: string; onClick: () => void }
  footer?: string
  /** 只看逾期过滤是否生效 */
  filtered?: boolean
}

const Panel = styled.section`
  min-width: 0;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};
`

const PanelHeader = styled.header`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  padding: 16px 18px 8px;
`

const PanelTitle = styled.h3`
  margin: 0;
  color: ${railTheme.text};
  font-size: 15px;
  font-weight: 650;
  letter-spacing: 0.1px;
`

const PanelBody = styled.div`
  padding: 0 10px 10px;
`

const RowButton = styled.button<{ $clickable: boolean }>`
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  width: 100%;
  padding: 9px 8px;
  color: inherit;
  font: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  border-radius: ${railTheme.radiusSmall}px;
  cursor: ${props => (props.$clickable ? 'pointer' : 'default')};

  &:focus-visible {
    outline: 2px solid ${railTheme.blue};
    outline-offset: -1px;
  }

  &:hover {
    background: ${props => (props.$clickable ? 'rgba(47, 117, 103, 0.06)' : 'transparent')};
  }
`

const RowIcon = styled.span`
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: ${railTheme.blue};
  font-size: 15px;
  background: ${railTheme.blueWeak};
  border-radius: 9px;
`

const RowInfo = styled.span`
  min-width: 0;
`

const RowTitle = styled.span`
  display: block;
  overflow: hidden;
  color: ${railTheme.text};
  font-size: 13.5px;
  font-weight: 550;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const RowDescription = styled.span`
  display: block;
  margin-top: 1px;
  overflow: hidden;
  color: ${railTheme.text3};
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const pillTone: Record<GovernancePillTone, { color: string; background: string }> = {
  danger: { color: railTheme.red, background: railTheme.redWeak },
  warning: { color: railTheme.amber, background: railTheme.amberWeak },
  success: { color: railTheme.green, background: railTheme.greenWeak },
  info: { color: railTheme.blue, background: railTheme.blueWeak },
  plain: { color: railTheme.text2, background: 'rgba(91, 101, 115, 0.1)' },
}

const Pill = styled.span<{ $tone: GovernancePillTone }>`
  flex: none;
  padding: 2px 8px;
  color: ${props => pillTone[props.$tone].color};
  font-size: 11.5px;
  background: ${props => pillTone[props.$tone].background};
  border-radius: 6px;
`

const Owner = styled.span`
  display: flex;
  flex: none;
  align-items: center;
  gap: 6px;
  color: ${railTheme.text2};
  font-size: 12px;
`

const OwnerMini = styled.span`
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  color: ${railTheme.brand};
  font-size: 10px;
  font-weight: 650;
  background: ${railTheme.brandWeak};
  border-radius: 50%;
`

const DueText = styled.span<{ $tone?: 'overdue' | 'soon' }>`
  flex: none;
  color: ${props => (props.$tone === 'overdue' ? railTheme.red : props.$tone === 'soon' ? railTheme.amber : railTheme.text3)};
  font-size: 12px;
  font-weight: ${props => (props.$tone === 'overdue' ? 600 : 400)};
`

const PanelFooter = styled.div`
  padding: 2px 16px 12px;
  color: ${railTheme.text3};
  font-size: 12px;
`

const kindIcon: Record<GovernanceTodoRowKind, string> = {
  task: '任',
  issue: '问',
  scan: '扫',
}

/** "该步待办"摘要列表（AntD List 承载，行为与样式贴合原型行）。 */
export function GovernanceTodoPanel({
  title,
  hint,
  rows,
  loading,
  emptyText,
  emptyAction,
  footer,
  filtered,
}: GovernanceTodoPanelProps) {
  return (
    <Panel aria-busy={loading}>
      <PanelHeader>
        <PanelTitle>{title}</PanelTitle>
        {hint && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{hint}</Typography.Text>}
      </PanelHeader>
      <PanelBody>
        <List
          size="small"
          loading={loading}
          dataSource={rows}
          locale={{
            emptyText: (
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description={filtered ? '筛选下没有匹配项' : emptyText}
                style={{ padding: '20px 0' }}
              >
                {emptyAction && !filtered && <Button type="primary" size="small" onClick={emptyAction.onClick}>{emptyAction.label}</Button>}
              </Empty>
            ),
          }}
          renderItem={row => {
            const clickable = Boolean(row.onClick)
            return (
              <List.Item style={{ padding: 0, borderBlockEnd: 'none' }}>
                <RowButton type="button" $clickable={clickable} onClick={row.onClick} aria-label={`${row.title}${row.owner ? `，责任人 ${row.owner}` : ''}`}>
                  <RowIcon aria-hidden>{kindIcon[row.kind]}</RowIcon>
                  <RowInfo>
                    <RowTitle>{row.title}</RowTitle>
                    <RowDescription>{row.description}</RowDescription>
                  </RowInfo>
                  {row.pill && <Pill $tone={row.pill.tone}>{row.pill.label}</Pill>}
                  {(row.owner || row.due) && (
                    <Owner>
                      {row.owner && <OwnerMini>{row.owner.trim().charAt(0)}</OwnerMini>}
                      {row.due && <DueText $tone={row.dueTone}>{row.due}</DueText>}
                    </Owner>
                  )}
                </RowButton>
              </List.Item>
            )
          }}
        />
      </PanelBody>
      {footer && <PanelFooter>{footer}</PanelFooter>}
    </Panel>
  )
}

/* ------------------------- "下一步建议"卡 ------------------------- */

interface GovernanceNextSuggestionProps {
  text: string
  primaryLabel: string
  onPrimary: () => void
  primaryDisabled?: boolean
  secondaryLabel?: string
  secondaryActive?: boolean
  onSecondary?: () => void
  note: string
}

const SuggestionCard = styled.aside`
  min-width: 0;
  padding: 18px;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};
`

const SuggestionTitle = styled.h3`
  margin: 0;
  color: ${railTheme.text};
  font-size: 15px;
  font-weight: 650;
`

const SuggestionText = styled.p`
  margin: 9px 0 14px;
  color: ${railTheme.text2};
  font-size: 13px;
  line-height: 1.6;
`

const SuggestionNote = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 14px;
  padding-top: 12px;
  color: ${railTheme.text2};
  font-size: 12px;
  border-top: 1px solid ${railTheme.line};
`

const NoteTick = styled.span`
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: none;
  color: ${railTheme.brand};
  font-size: 12px;
  font-weight: 700;
  background: ${railTheme.brandWeak};
  border-radius: 50%;
`

export function GovernanceNextSuggestion({
  text,
  primaryLabel,
  onPrimary,
  primaryDisabled,
  secondaryLabel,
  secondaryActive,
  onSecondary,
  note,
}: GovernanceNextSuggestionProps) {
  return (
    <SuggestionCard>
      <SuggestionTitle>下一步建议</SuggestionTitle>
      <SuggestionText>{text}</SuggestionText>
      <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
        <Button type="primary" disabled={primaryDisabled} onClick={onPrimary}>{primaryLabel}</Button>
        {secondaryLabel && (
          <Button type={secondaryActive ? 'default' : 'dashed'} onClick={onSecondary} aria-pressed={secondaryActive}>{secondaryLabel}</Button>
        )}
        <Tooltip title="开发中，依赖登录（D1）">
          <span style={{ display: 'inline-flex' }}>
            <Button disabled>移交责任人</Button>
          </span>
        </Tooltip>
      </div>
      <SuggestionNote>
        <NoteTick aria-hidden>✓</NoteTick>
        <span>{note}</span>
      </SuggestionNote>
    </SuggestionCard>
  )
}
