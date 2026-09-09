import { Badge } from 'antd'
import styled, { css, keyframes } from 'styled-components'
import type { GovernanceRailNodeModel, GovernanceRailNodeState, GovernanceRailStageKey } from './governanceRailModel'
import { railAvatarChar, railAvatarPalette, railTheme } from './railTheme'

interface GovernanceRailProps {
  nodes: GovernanceRailNodeModel[]
  selectedKey: GovernanceRailStageKey
  onSelect: (key: GovernanceRailStageKey) => void
  hint?: string
}

const pulse = keyframes`
  0%, 100% { box-shadow: 0 0 0 4px ${railTheme.blueWeak}; }
  50% { box-shadow: 0 0 0 9px ${railTheme.blueWeak}; }
`

const Legend = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: center;
  margin: 2px 0 10px;
  color: ${railTheme.text2};
  font-size: 12px;

  .item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .dot {
    width: 9px;
    height: 9px;
    border-radius: 50%;
  }
`

const StageSummary = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: center;
  gap: 5px;
  margin: 0 0 6px;
  color: ${railTheme.text2};
  font-size: 12.5px;

  strong {
    color: ${railTheme.blue};
    font-size: 13.5px;
    font-weight: 700;
    font-variant-numeric: tabular-nums;
  }

  em {
    color: ${railTheme.text};
    font-style: normal;
    font-weight: 650;
  }
`

const RailScroll = styled.div`
  overflow-x: auto;
  padding: 6px 2px 4px;
  scrollbar-width: thin;
`

const RailRow = styled.ol`
  display: flex;
  align-items: flex-start;
  gap: 0;
  min-width: 720px;
  margin: 0;
  padding: 0;
  list-style: none;
`

const stateColor: Record<GovernanceRailNodeState, string> = {
  done: railTheme.green,
  current: railTheme.blue,
  overdue: railTheme.red,
  wait: railTheme.text3,
}

const Step = styled.li<{ $previousDone: boolean }>`
  position: relative;
  flex: 1 1 0;
  min-width: 118px;

  &::before {
    content: '';
    position: absolute;
    top: 23px;
    left: calc(50% - 118px + 21px);
    width: calc(100% - 42px);
    height: 2px;
    z-index: 0;
    background: ${props => (props.$previousDone ? railTheme.brandWeak : '#eef1f4')};
  }
`

const StepButton = styled.button<{ $state: GovernanceRailNodeState; $selected: boolean }>`
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  min-width: 0;
  min-height: 108px;
  flex-direction: column;
  align-items: center;
  gap: 5px;
  padding: 8px 4px 10px;
  color: inherit;
  font: inherit;
  text-align: center;
  background: transparent;
  border: 0;
  border-radius: ${railTheme.radiusSmall}px;
  cursor: pointer;
  transition: background 160ms ease, transform 160ms ease;

  &:hover {
    background: ${railTheme.bg};
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: 2px solid ${railTheme.blue};
    outline-offset: 1px;
  }

  ${props => props.$selected && css`
    background: linear-gradient(180deg, ${railTheme.blueWeak} 0%, rgba(255, 255, 255, 0) 88%);
  `}
`

const DotWrap = styled.span`
  position: relative;
  display: inline-flex;
  margin-bottom: 2px;
`

const Dot = styled.span<{ $state: GovernanceRailNodeState }>`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
  border-radius: 50%;
  color: ${railTheme.text3};
  transition: transform 160ms ease, box-shadow 160ms ease, background 160ms ease;

  ${StepButton}:hover & {
    transform: translateY(-2px) scale(1.04);
  }

  ${props => props.$state === 'done' && css`
    color: #fff;
    background: ${railTheme.green};
    box-shadow: 0 2px 8px -2px rgba(46, 158, 107, 0.55);
  `}

  ${props => props.$state === 'current' && css`
    color: ${railTheme.blue};
    background: ${railTheme.blueWeak};
    border: 2px solid ${railTheme.blue};
    animation: ${pulse} 2.2s ease-in-out infinite;

    @media (prefers-reduced-motion: reduce) {
      animation: none;
    }
  `}

  ${props => props.$state === 'overdue' && css`
    color: ${railTheme.red};
    background: ${railTheme.redWeak};
    border: 1.5px solid ${railTheme.red};
  `}

  ${props => props.$state === 'wait' && css`
    color: ${railTheme.text2};
    background: #f2f4f6;
    border: 1px solid #e4e8ec;
  `}
`

const StepName = styled.span<{ $state: GovernanceRailNodeState }>`
  max-width: 100%;
  overflow: hidden;
  color: ${props => (props.$state === 'current' ? railTheme.blue : props.$state === 'overdue' ? railTheme.red : railTheme.text)};
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const StepCaption = styled.span`
  display: flex;
  align-items: center;
  gap: 6px;
  max-width: 112px;
  min-height: 16px;
  overflow: hidden;
  color: ${railTheme.text2};
  font-size: 11.5px;

  > span {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
`

const StateChip = styled.span<{ $state: GovernanceRailNodeState; $count: number }>`
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  color: #fff;
  font-size: 10.5px;
  font-weight: 700;
  background: ${props => stateColor[props.$state]};
  border-radius: 9px;
`

const Avatars = styled.span`
  display: flex;
  align-items: center;
`

const Avatar = styled.span<{ $color: string }>`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-left: -7px;
  color: #fff;
  font-size: 9.5px;
  font-weight: 600;
  background: ${props => props.$color};
  border: 2px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px rgba(20, 30, 50, 0.06);

  &:first-child {
    margin-left: 0;
  }
`

/** 六节点治理轨道：点击节点进入对应工作页面；状态同时以文字与图例说明呈现。 */
export function GovernanceRail({ nodes, selectedKey, onSelect, hint }: GovernanceRailProps) {
  const current = nodes.find(node => node.state === 'current') ?? nodes.find(node => node.state === 'overdue')
  const currentIndex = Math.max(0, current ? nodes.indexOf(current) : 0)
  const currentLabel = current?.label ?? '进行中'
  return (
    <div>
      <StageSummary aria-live="polite">
        治理进度 <strong>{nodes.length > 0 ? `第 ${currentIndex + 1} 步` : '—'} / {nodes.length} 步</strong>
        {current && <> · 当前阶段 <em>{currentLabel}</em></>}
      </StageSummary>
      <Legend aria-hidden="true">
        <span className="item"><span className="dot" style={{ background: railTheme.green }} />已完成</span>
        <span className="item"><span className="dot" style={{ background: railTheme.blue }} />当前阶段</span>
        <span className="item"><span className="dot" style={{ background: railTheme.red }} />该步有逾期</span>
        <span className="item"><span className="dot" style={{ background: railTheme.text3 }} />未开始</span>
      </Legend>
      <RailScroll>
        <RailRow aria-label="治理轨道：扫描入库 → 问题池 → 整改分派 → 业务确认 → 质量验收 → 正式应用">
          {nodes.map((node, index) => {
            const previous = nodes[index - 1]
            const selected = selectedKey === node.key
            const hasCount = node.badge > 0
            return (
              <Step key={node.key} $previousDone={previous?.state === 'done'}>
                <StepButton
                  type="button"
                  $state={node.state}
                  $selected={selected}
                  aria-current={selected ? 'step' : undefined}
                  aria-label={`${node.label}：${node.caption}`}
                  onClick={() => onSelect(node.key)}
                >
                  <DotWrap>
                    <Dot $state={node.state}>
                      {node.state === 'done' ? '✓' : node.state === 'current' ? '→' : node.state === 'overdue' ? '!' : ''}
                    </Dot>
                    {hasCount && node.state === 'wait' && (
                      <Badge count={node.badge} overflowCount={999} color={railTheme.text2} style={{ position: 'absolute', top: -4, insetInlineEnd: -8 }} />
                    )}
                  </DotWrap>
                  <StepName $state={node.state}>{node.label}</StepName>
                  <StepCaption>
                    {node.state !== 'wait' && hasCount ? <StateChip $state={node.state} $count={node.badge}>{node.badge}</StateChip> : null}
                    <span>{node.caption}</span>
                  </StepCaption>
                  {node.owners.length > 0 && (
                    <Avatars aria-label={`责任人 ${node.owners.join('、')}`}>
                      {node.owners.slice(0, 4).map((name, ownerIndex) => (
                        <Avatar key={`${name}-${ownerIndex}`} $color={railAvatarPalette[ownerIndex % railAvatarPalette.length]}>{railAvatarChar(name)}</Avatar>
                      ))}
                      {node.owners.length > 4 && <Avatar $color="#5b6573">+{node.owners.length - 4}</Avatar>}
                    </Avatars>
                  )}
                </StepButton>
              </Step>
            )
          })}
        </RailRow>
      </RailScroll>
      {hint && (
        <div style={{ marginTop: 6, color: railTheme.text3, fontSize: 11.5, textAlign: 'center' }}>{hint}</div>
      )}
    </div>
  )
}
