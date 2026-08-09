import { Alert, Empty, Typography } from 'antd'
import styled from 'styled-components'
import type { GovernanceEmployee, GovernancePlan } from '../types'
import { GovernanceDependencyLayer, GANTT_DAY_WIDTH, GANTT_ROW_HEIGHT } from './GovernanceDependencyLayer'
import { buildGanttModel, type GanttRowState } from './governanceGanttModel'

const INFO_WIDTH = 260
const HEADER_HEIGHT = 40

const stateLabels: Record<GanttRowState, string> = {
  NOT_STARTED: '未开始',
  IN_PROGRESS: '进行中',
  BLOCKED: '已阻塞',
  OVERDUE: '已逾期',
  DONE: '已完成',
}

const Root = styled.div`
  min-width: 0;
`

const Notices = styled.div`
  display: grid;
  gap: 8px;
  margin: 12px 0;
`

const Frame = styled.div`
  display: grid;
  grid-template-columns: ${INFO_WIDTH}px minmax(0, 1fr);
  width: 100%;
  min-width: 0;
  border: 1px solid #dfe5e2;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
`

const InfoColumn = styled.div`
  position: relative;
  z-index: 2;
  border-right: 1px solid #dfe5e2;
  background: #fff;
`

const InfoHeader = styled.div`
  height: ${HEADER_HEIGHT}px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  color: #59645f;
  font-size: 12px;
  font-weight: 600;
  border-bottom: 1px solid #dfe5e2;
`

const InfoRow = styled.div`
  height: ${GANTT_ROW_HEIGHT}px;
  padding: 7px 14px;
  border-bottom: 1px solid #edf0ee;
  overflow: hidden;
`

const Title = styled.div`
  overflow: hidden;
  color: #24312b;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Meta = styled.div`
  margin-top: 3px;
  overflow: hidden;
  color: #69756f;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const ErrorMeta = styled.div`
  overflow: hidden;
  color: #b42318;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const TimelineScroll = styled.div`
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
`

const Timeline = styled.div`
  position: relative;
`

const TimelineHeader = styled.div`
  position: relative;
  height: ${HEADER_HEIGHT}px;
  border-bottom: 1px solid #dfe5e2;
  background: #f7f9f8;
`

const Tick = styled.div`
  position: absolute;
  top: 0;
  bottom: 0;
  padding: 11px 0 0 6px;
  border-left: 1px solid #e4e9e6;
  color: #68736e;
  font-size: 11px;
  white-space: nowrap;
`

const TimelineBody = styled.div`
  position: relative;
  background-image: linear-gradient(to right, #edf0ee 1px, transparent 1px);
  background-size: ${GANTT_DAY_WIDTH}px 100%;
`

const TimelineRow = styled.div`
  position: relative;
  height: ${GANTT_ROW_HEIGHT}px;
  border-bottom: 1px solid #edf0ee;
`

const Bar = styled.div<{ $state: GanttRowState }>`
  position: absolute;
  top: 14px;
  height: 28px;
  min-width: ${GANTT_DAY_WIDTH}px;
  overflow: hidden;
  border: 1px solid ${({ $state }) => $state === 'OVERDUE' ? '#c44236' : $state === 'BLOCKED' ? '#b7791f' : $state === 'DONE' ? '#3f7d5c' : '#3f6f88'};
  border-radius: 4px;
  background: ${({ $state }) => $state === 'OVERDUE' ? '#fff1f0' : $state === 'BLOCKED' ? '#fff7e6' : $state === 'DONE' ? '#e8f5ed' : '#eaf3f7'};
  color: #1f2d27;
`

const Fill = styled.div`
  position: absolute;
  inset: 0 auto 0 0;
  background: rgb(52 117 83 / 24%);
`

const BarText = styled.span`
  position: relative;
  z-index: 1;
  display: block;
  padding: 4px 6px;
  overflow: hidden;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const TodayLine = styled.div`
  position: absolute;
  top: 0;
  bottom: 0;
  z-index: 3;
  width: 1px;
  background: #cf3f35;
  pointer-events: none;
`

function localToday(): string {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function GovernanceGanttView({ plans, employees, today = localToday() }: {
  plans: GovernancePlan[]
  employees: GovernanceEmployee[]
  today?: string
}) {
  const model = buildGanttModel(plans, today)
  const employeeById = new Map(employees.map(employee => [employee.id, employee.name]))

  if (plans.length === 0) {
    return <Root><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未编排计划项" /></Root>
  }

  const timelineWidth = model.range.totalDays * GANTT_DAY_WIDTH

  return <Root>
    {model.invalidPlans.length > 0 && <Notices>
      <Alert
        type="warning"
        showIcon
        message={`以下计划缺少有效排期：${model.invalidPlans.map(plan => plan.title).join('、')}`}
      />
    </Notices>}
    {model.rows.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有可展示的有效排期" /> : <Frame>
      <InfoColumn>
        <InfoHeader>计划项 / 责任人</InfoHeader>
        {model.rows.map(row => {
          const ownerId = row.plan.responsibleUserId ?? row.plan.assigneeId
          const ownerName = ownerId ? employeeById.get(ownerId) ?? ownerId : '未分配'
          const actualDates = row.plan.actualStart || row.plan.actualEnd
            ? `实际：${row.plan.actualStart ?? '-'} 至 ${row.plan.actualEnd ?? '-'}`
            : `${stateLabels[row.state]} · ${row.plan.completedQuantity}/${row.plan.plannedQuantity} ${row.plan.quantityUnit}`
          return <InfoRow key={row.plan.id}>
            <Title title={row.plan.title}>{row.plan.title}</Title>
            <Meta title={`${ownerName} · ${actualDates}`}><span>{ownerName}</span> · <span>{actualDates}</span></Meta>
            {row.invalidDependencyIds.length > 0 && <ErrorMeta>依赖数据异常：{row.invalidDependencyIds.join(', ')}</ErrorMeta>}
          </InfoRow>
        })}
      </InfoColumn>
      <TimelineScroll>
        <Timeline style={{ width: timelineWidth }}>
          <TimelineHeader>
            {model.ticks.map(tick => <Tick key={tick.date} style={{ left: tick.offsetDays * GANTT_DAY_WIDTH }}>{tick.label}</Tick>)}
          </TimelineHeader>
          <TimelineBody style={{ width: timelineWidth, height: model.rows.length * GANTT_ROW_HEIGHT }}>
            {model.rows.map(row => {
              const ownerId = row.plan.responsibleUserId ?? row.plan.assigneeId
              const ownerName = ownerId ? employeeById.get(ownerId) ?? ownerId : '未分配'
              const label = `${row.plan.title}，${row.plan.plannedStart} 至 ${row.plan.plannedEnd}，${ownerName}，完成 ${row.progressPercent}%，${stateLabels[row.state]}`
              return <TimelineRow key={row.plan.id}>
                <Bar
                  role="img"
                  aria-label={label}
                  title={label}
                  $state={row.state}
                  style={{ left: row.offsetDays * GANTT_DAY_WIDTH, width: Math.max(GANTT_DAY_WIDTH, row.durationDays * GANTT_DAY_WIDTH) }}
                >
                  <Fill style={{ width: `${row.progressPercent}%` }} />
                  <BarText>{row.progressPercent}%</BarText>
                </Bar>
              </TimelineRow>
            })}
            <GovernanceDependencyLayer model={model} />
            {model.todayOffset !== null && <TodayLine aria-hidden style={{ left: model.todayOffset * GANTT_DAY_WIDTH + GANTT_DAY_WIDTH / 2 }} />}
          </TimelineBody>
        </Timeline>
      </TimelineScroll>
    </Frame>}
    <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8, fontSize: 12 }}>
      计划条按计划日期展示，实际日期仅作记录。
    </Typography.Text>
  </Root>
}
