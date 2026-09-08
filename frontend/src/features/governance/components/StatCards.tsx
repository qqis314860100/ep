import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  ExclamationCircleOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons'
import type { ReactNode } from 'react'
import styled from 'styled-components'
import { railTheme } from './railTheme'

export type GovernanceStatTone = 'default' | 'warn' | 'alert' | 'success'

export interface GovernanceStatCardData {
  key: string
  label: string
  /** 数字为空（接口不可用）时显示 '—' */
  value: number | null
  unit?: string
  tone?: GovernanceStatTone
  footnote: string
}

interface StatCardsProps {
  cards: GovernanceStatCardData[]
}

const toneMeta: Record<GovernanceStatTone, { color: string; weak: string; icon: ReactNode }> = {
  default: { color: railTheme.brand, weak: railTheme.brandWeak, icon: <FolderOpenOutlined /> },
  warn: { color: railTheme.amber, weak: railTheme.amberWeak, icon: <ClockCircleOutlined /> },
  alert: { color: railTheme.red, weak: railTheme.redWeak, icon: <ExclamationCircleOutlined /> },
  success: { color: railTheme.green, weak: railTheme.greenWeak, icon: <CheckCircleOutlined /> },
}

const Grid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;

  @media (max-width: 1180px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @media (max-width: 560px) {
    grid-template-columns: 1fr;
  }
`

const Card = styled.div<{ $tone: GovernanceStatTone }>`
  position: relative;
  min-width: 0;
  padding: 14px 16px 13px;
  overflow: hidden;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};
  transition: transform 160ms ease, box-shadow 160ms ease;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 2px 4px rgba(20, 30, 50, 0.06), 0 10px 24px -12px rgba(20, 30, 50, 0.16);
  }

  &::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: ${props => toneMeta[props.$tone].color};
  }
`

const TopRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
`

const Label = styled.div`
  min-width: 0;
  overflow: hidden;
  color: ${railTheme.text2};
  font-size: 12.5px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const IconChip = styled.span<{ $tone: GovernanceStatTone }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex: none;
  color: ${props => toneMeta[props.$tone].color};
  font-size: 15px;
  background: ${props => toneMeta[props.$tone].weak};
  border-radius: 9px;
`

const ValueRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 8px;
`

const Value = styled.span<{ $tone: GovernanceStatTone }>`
  color: ${props => (props.$tone === 'default' ? railTheme.text : toneMeta[props.$tone].color)};
  font-size: 27px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 0.2px;
  font-variant-numeric: tabular-nums;
`

const Unit = styled.span`
  color: ${railTheme.text3};
  font-size: 12px;
`

const Footnote = styled.div`
  min-height: 16px;
  margin-top: 3px;
  overflow: hidden;
  color: ${railTheme.text3};
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

/** 顶部 4 张指标卡（待整理 / 本周到期 / 已逾期 / 已标准化）。数值不可用时降级为 '—'。 */
export function StatCards({ cards }: StatCardsProps) {
  return (
    <Grid>
      {cards.map(card => {
        const tone = card.tone ?? 'default'
        return (
          <Card key={card.key} $tone={tone}>
            <TopRow>
              <Label title={card.label}>{card.label}</Label>
              <IconChip $tone={tone} aria-hidden="true">{toneMeta[tone].icon}</IconChip>
            </TopRow>
            <ValueRow>
              <Value $tone={tone}>{card.value === null ? '—' : card.value.toLocaleString('zh-CN')}</Value>
              {card.value !== null && card.unit && <Unit>{card.unit}</Unit>}
            </ValueRow>
            <Footnote title={card.footnote}>{card.value === null ? '数据暂不可用' : card.footnote}</Footnote>
          </Card>
        )
      })}
    </Grid>
  )
}
