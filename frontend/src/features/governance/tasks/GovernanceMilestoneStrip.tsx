import { CheckOutlined, ClockCircleOutlined, ExclamationOutlined } from '@ant-design/icons'
import { Alert, Typography } from 'antd'
import styled from 'styled-components'
import { buildGovernanceWorkflow, type GovernanceWorkflowInput, type GovernanceWorkflowStepState } from './governanceWorkflowModel'

const Root = styled.section`
  min-width: 0;
  margin-bottom: 14px;
`

const Heading = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
`

const Scroller = styled.div`
  overflow-x: auto;
  padding-bottom: 2px;
`

const Flow = styled.div`
  display: grid;
  grid-template-columns: repeat(6, minmax(112px, 1fr));
  min-width: 720px;
`

const Step = styled.div<{ $state: GovernanceWorkflowStepState }>`
  position: relative;
  min-width: 0;
  padding-right: 12px;

  &::after {
    position: absolute;
    top: 14px;
    right: 0;
    left: 30px;
    height: 2px;
    background: ${({ $state }) => $state === 'done' ? '#4f8064' : '#d9dfdc'};
    content: '';
  }

  &:last-child::after { display: none; }
`

const Node = styled.span<{ $state: GovernanceWorkflowStepState }>`
  position: relative;
  z-index: 1;
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 2px solid ${({ $state }) => $state === 'done' ? '#4f8064' : $state === 'active' ? '#276f8a' : $state === 'error' ? '#bd3f36' : '#bdc7c2'};
  border-radius: 50%;
  background: ${({ $state }) => $state === 'done' ? '#4f8064' : '#fff'};
  color: ${({ $state }) => $state === 'done' ? '#fff' : $state === 'active' ? '#276f8a' : $state === 'error' ? '#bd3f36' : '#7b8781'};
`

const StepLabel = styled.div`
  margin-top: 7px;
  color: #24312b;
  font-size: 13px;
  font-weight: 600;
`

const StepDetail = styled.div`
  max-width: 150px;
  margin-top: 2px;
  color: #6b7771;
  font-size: 11px;
  line-height: 1.45;
`

export function GovernanceMilestoneStrip(input: GovernanceWorkflowInput) {
  const model = buildGovernanceWorkflow(input)

  if (model.kind === 'legacy') {
    return <Alert type="info" showIcon message={model.title} description={model.summary} />
  }

  return <Root aria-label="数据资产治理闭环">
    <Heading>
      <Typography.Text strong>{model.title}</Typography.Text>
      <Typography.Text type={input.status === 'REWORK_REQUIRED' ? 'danger' : 'secondary'}>{model.summary}</Typography.Text>
    </Heading>
    <Scroller>
      <Flow role="list">
        {model.steps.map((step, index) => <Step key={step.key} role="listitem" data-state={step.state} $state={step.state}>
          <Node $state={step.state} aria-label={`${step.label}：${step.state}`}>
            {step.state === 'done' ? <CheckOutlined aria-hidden /> : step.state === 'error' ? <ExclamationOutlined aria-hidden /> : step.state === 'active' ? <ClockCircleOutlined aria-hidden /> : index + 1}
          </Node>
          <StepLabel>{step.label}</StepLabel>
          <StepDetail>{step.detail}</StepDetail>
        </Step>)}
      </Flow>
    </Scroller>
  </Root>
}
