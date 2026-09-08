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

const toneColor: Record<GovernanceStatTone, string> = {
  default: railTheme.brand,
  warn: railTheme.amber,
  alert: railTheme.red,
  success: railTheme.green,
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
  padding: 13px 16px 12px;
  overflow: hidden;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};

  &::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: ${props => toneColor[props.$tone]};
  }
`

const Label = styled.div`
  color: ${railTheme.text2};
  font-size: 12.5px;
`

const ValueRow = styled.div`
  display: flex;
  align-items: baseline;
  gap: 5px;
  margin-top: 2px;
`

const Value = styled.span<{ $tone: GovernanceStatTone }>`
  color: ${props => (props.$tone === 'default' ? railTheme.text : toneColor[props.$tone])};
  font-size: 26px;
  font-weight: 700;
  line-height: 1.2;
  letter-spacing: 0.3px;
`

const Unit = styled.span`
  color: ${railTheme.text3};
  font-size: 12px;
`

const Footnote = styled.div`
  min-height: 16px;
  margin-top: 1px;
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
      {cards.map(card => (
        <Card key={card.key} $tone={card.tone ?? 'default'}>
          <Label>{card.label}</Label>
          <ValueRow>
            <Value $tone={card.tone ?? 'default'}>{card.value === null ? '—' : card.value.toLocaleString('zh-CN')}</Value>
            {card.value !== null && card.unit && <Unit>{card.unit}</Unit>}
          </ValueRow>
          <Footnote title={card.footnote}>{card.value === null ? '数据暂不可用' : card.footnote}</Footnote>
        </Card>
      ))}
    </Grid>
  )
}
