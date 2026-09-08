import { Badge, Typography } from 'antd'
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

const RailScroll = styled.div`
  overflow-x: auto;
  padding: 4px 2px 2px;
  scrollbar-width: thin;
`

const RailRow = styled.ol`
  display: flex;
  align-items: stretch;
  gap: 0;
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
  min-width: 128px;

  &::before {
    content: '';
    position: absolute;
    top: 24px;
    left: -50%;
    width: 100%;
    height: 2px;
    z-index: 0;
    background: ${props => (props.$previousDone ? railTheme.green : railTheme.line)};
    transition: background 160ms ease;
  }

  &:first-child::before {
    display: none;
  }
`

const StepButton = styled.button<{ $state: GovernanceRailNodeState; $selected: boolean }>`
  position: relative;
  z-index: 1;
  display: flex;
  width: 100%;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 6px 4px 8px;
  color: inherit;
  font: inherit;
  text-align: center;
  background: transparent;
  border: 0;
  border-radius: ${railTheme.radiusSmall}px;
  cursor: pointer;

  &:focus-visible {
    outline: 2px solid ${railTheme.blue};
    outline-offset: 1px;
  }

  ${props => props.$selected && css`
    background: linear-gradient(180deg, ${railTheme.blueWeak}, transparent 70%);
    box-shadow: inset 0 -2px 0 ${railTheme.blue};
  `}
`

const DotWrap = styled.span`
  position: relative;
  display: inline-flex;
`

const Dot = styled.span<{ $state: GovernanceRailNodeState }>`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 15px;
  border-radius: 50%;
  background: #fff;
  border: 1.5px solid ${railTheme.line};
  color: ${railTheme.text3};
  transition: transform 160ms ease, box-shadow 160ms ease, border-color 160ms ease, background 160ms ease;

  ${StepButton}:hover & {
    transform: translateY(-2px);
  }

  ${props => props.$state === 'done' && css`
    background: ${railTheme.green};
    border-color: ${railTheme.green};
    color: #fff;
  `}

  ${props => props.$state === 'current' && css`
    border: 2px solid ${railTheme.blue};
    color: ${railTheme.blue};
    animation: ${pulse} 2.2s ease-in-out infinite;

    @media (prefers-reduced-motion: reduce) {
      animation: none;
      box-shadow: 0 0 0 5px ${railTheme.blueWeak};
    }
  `}

  ${props => props.$state === 'overdue' && css`
    border-color: ${railTheme.red};
    color: ${railTheme.red};
    box-shadow: 0 0 0 4px ${railTheme.redWeak};
  `}
`

const StepName = styled.span<{ $state: GovernanceRailNodeState }>`
  max-width: 100%;
  overflow: hidden;
  color: ${props => (props.$state === 'current' ? railTheme.blue : props.$state === 'overdue' ? railTheme.red : railTheme.text)};
  font-size: 13px;
  font-weight: ${props => (props.$state === 'current' ? 650 : 550)};
  text-overflow: ellipsis;
  white-space: nowrap;
`

const StepCaption = styled.span<{ $state: GovernanceRailNodeState }>`
  color: ${props => (props.$state === 'done' ? railTheme.text2 : stateColor[props.$state])};
  font-size: 11.5px;
  white-space: nowrap;
`

const Avatars = styled.span`
  display: flex;
  align-items: center;
  margin-top: 1px;
`

const Avatar = styled.span<{ $color: string }>`
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-left: -6px;
  color: #fff;
  font-size: 9px;
  font-weight: 600;
  background: ${props => props.$color};
  border: 2px solid #fff;
  border-radius: 50%;

  &:first-child {
    margin-left: 0;
  }
`

/** 六节点治理轨道：点击/键盘聚焦选中节点并联动详情；状态同时以文字说明呈现。 */
export function GovernanceRail({ nodes, selectedKey, onSelect, hint }: GovernanceRailProps) {
  return (
    <div>
      <RailScroll>
        <RailRow aria-label="治理轨道：扫描入库 → 问题池 → 整改分派 → 业务确认 → 质量验收 → 正式应用">
          {nodes.map((node, index) => {
            const previous = nodes[index - 1]
            const selected = selectedKey === node.key
            return (
              <Step key={node.key} $previousDone={previous?.state === 'done'}>
                <StepButton
                  type="button"
                  $state={node.state}
                  $selected={selected}
                  aria-pressed={selected}
                  aria-label={`${node.label}：${node.caption}`}
                  onClick={() => onSelect(node.key)}
                >
                  <DotWrap>
                    <Dot $state={node.state}>
                      {node.state === 'done' ? '✓' : node.state === 'current' ? '→' : node.state === 'overdue' ? '!' : node.badge > 0 ? node.badge : ''}
                    </Dot>
                    {node.badge > 0 && node.state !== 'wait' && (
                      <Badge count={node.badge} overflowCount={999} color={node.state === 'overdue' ? railTheme.red : railTheme.blue} style={{ position: 'absolute', top: -4, insetInlineEnd: -8 }} />
                    )}
                  </DotWrap>
                  <StepName $state={node.state}>{node.label}</StepName>
                  <StepCaption $state={node.state}>{node.caption}</StepCaption>
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
        <Typography.Text type="secondary" style={{ display: 'block', textAlign: 'center', fontSize: 11.5 }}>
          {hint}
        </Typography.Text>
      )}
    </div>
  )
}
