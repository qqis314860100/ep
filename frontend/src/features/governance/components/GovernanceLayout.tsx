import type { ReactNode } from 'react'
import styled from 'styled-components'
import { GovernanceWorkspaceBack } from './GovernanceWorkspaceBack'
import { railTheme } from './railTheme'

/**
 * 治理子页统一版式：页面容器 / 页头 / 面板 / 指标卡 / 筛选面板。
 * 所有治理子页复用这里的 token 与间距，避免各页自造边框、圆角与留白。
 */

export const GOVERNANCE_PAGE_GAP = 16

export const GovernancePage = styled.section`
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: ${GOVERNANCE_PAGE_GAP}px;
  padding-bottom: 28px;
`

const HeaderRow = styled.header`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  @media (max-width: 760px) {
    align-items: stretch;
    flex-direction: column;
  }
`

const HeaderCopy = styled.div`
  min-width: 0;

  h1 {
    margin: 0;
    color: ${railTheme.text};
    font-size: 22px;
    font-weight: 700;
    line-height: 1.3;
  }

  p {
    margin: 6px 0 0;
    color: ${railTheme.text2};
    font-size: 13px;
    line-height: 1.6;
  }
`

const HeaderActions = styled.div`
  display: flex;
  flex: none;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;

  @media (max-width: 760px) {
    justify-content: flex-start;
  }
`

/** 统一页头：返回治理工作台 + 标题 + 副标题 + 右侧操作区。 */
export function GovernancePageHeader({ title, subtitle, actions }: {
  title: string
  subtitle?: ReactNode
  actions?: ReactNode
}) {
  return (
    <HeaderRow>
      <HeaderCopy>
        <GovernanceWorkspaceBack />
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </HeaderCopy>
      {actions && <HeaderActions>{actions}</HeaderActions>}
    </HeaderRow>
  )
}

/** 统一内容面板（白底卡片）。 */
export const GovernancePanel = styled.section`
  min-width: 0;
  padding: 16px 18px;
  background: #fff;
  border: 1px solid ${railTheme.line};
  border-radius: 8px;
`

const PanelHead = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
`

const PanelTitle = styled.h2`
  margin: 0;
  color: ${railTheme.text};
  font-size: 15px;
  font-weight: 680;
`

/** 带标题/右侧附加内容的面板。 */
export function GovernancePanelSection({ title, extra, children }: {
  title?: ReactNode
  extra?: ReactNode
  children: ReactNode
}) {
  return (
    <GovernancePanel>
      {(title || extra) && <PanelHead><PanelTitle>{title}</PanelTitle>{extra}</PanelHead>}
      {children}
    </GovernancePanel>
  )
}

export const GovernanceMetricGrid = styled.div`
  display: grid;
  min-width: 0;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
`

const metricTone: Record<'default' | 'warn' | 'alert' | 'success', string> = {
  default: railTheme.text,
  warn: railTheme.amber,
  alert: railTheme.red,
  success: railTheme.green,
}

const MetricCard = styled.div<{ $tone: 'default' | 'warn' | 'alert' | 'success' }>`
  min-width: 0;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid ${railTheme.line};
  border-radius: 8px;

  .label {
    overflow: hidden;
    color: ${railTheme.text2};
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .value {
    margin-top: 6px;
    overflow: hidden;
    color: ${props => metricTone[props.$tone]};
    font-size: 22px;
    font-weight: 700;
    line-height: 1.2;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-variant-numeric: tabular-nums;
  }

  .unit {
    margin-left: 4px;
    color: ${railTheme.text3};
    font-size: 12px;
    font-weight: 400;
  }

  .footnote {
    min-height: 16px;
    margin-top: 4px;
    overflow: hidden;
    color: ${railTheme.text3};
    font-size: 12px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`

/** 全站统一指标卡：治理工作台/责任看板/盘点/运营/扫描共用同一视觉。 */
export function GovernanceMetric({ label, value, unit, footnote, tone = 'default' }: {
  label: ReactNode
  value: ReactNode
  unit?: ReactNode
  footnote?: ReactNode
  tone?: 'default' | 'warn' | 'alert' | 'success'
}) {
  return (
    <MetricCard $tone={tone}>
      <div className="label" title={typeof label === 'string' ? label : undefined}>{label}</div>
      <div className="value">{value}{unit ? <span className="unit">{unit}</span> : null}</div>
      {footnote !== undefined && footnote !== null && (
        <div className="footnote" title={typeof footnote === 'string' ? footnote : undefined}>{footnote}</div>
      )}
    </MetricCard>
  )
}
