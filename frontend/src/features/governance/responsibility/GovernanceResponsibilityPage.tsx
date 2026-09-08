import { ReloadOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Select, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getGovernanceResponsibilityBoard, getGovernanceTasks } from '../api'
import type {
  GovernanceResponsibilityRow,
  GovernanceTask,
} from '../types'
import { dueDayDiff } from '../components/governanceRailModel'
import { GovernanceStatusTag } from '../shared/GovernanceStatusTag'
import { StatCards } from '../components/StatCards'
import type { GovernanceStatCardData } from '../components/StatCards'
import { railTheme } from '../components/railTheme'

const Page = styled.div`
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
`

const Header = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
`

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
`

const Section = styled.section`
  padding: 18px 20px 20px;
  background: ${railTheme.card};
  border: 1px solid ${railTheme.line};
  border-radius: ${railTheme.radius}px;
  box-shadow: ${railTheme.shadow};
`

const SectionTitle = styled.h3`
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 12px;
  color: ${railTheme.text};
  font-size: 15px;
  font-weight: 650;

  .hint {
    color: ${railTheme.text3};
    font-size: 12px;
    font-weight: 400;
  }
`

const Danger = styled.span`
  color: ${railTheme.red};
  font-weight: 650;
`

const Warn = styled.span`
  color: ${railTheme.amber};
  font-weight: 650;
`

const SubLine = styled.div`
  margin-top: 2px;
  color: ${railTheme.text3};
  font-size: 12px;
`

const TaskRow = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 2px;
  border-bottom: 1px dashed ${railTheme.line};

  &:last-child {
    border-bottom: 0;
  }
`

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function monthKey(date: Date, offsetMonths: number): string {
  const copy = new Date(date.getFullYear(), date.getMonth() - offsetMonths, 1)
  return `${copy.getFullYear()}-${pad(copy.getMonth() + 1)}`
}

/** 展开区：某责任人名下的未完成任务（逾期优先、按截止日升序）。 */
function PersonOpenTasks({ userId, name }: { userId: string; name: string }) {
  const navigate = useNavigate()
  const tasksQuery = useQuery({
    queryKey: ['governance-tasks', 'owner', userId],
    queryFn: () => getGovernanceTasks({ ownerUserId: userId }),
    enabled: Boolean(userId),
  })
  const openTasks = useMemo(() => {
    const today = new Date()
    const tasks = (tasksQuery.data ?? []).filter(task => task.status !== 'COMPLETED')
    return [...tasks].sort((a, b) => {
      const aOverdue = dueDayDiff(a.dueDate, today)
      const bOverdue = dueDayDiff(b.dueDate, today)
      const aLate = aOverdue !== null && aOverdue < 0 ? 1 : 0
      const bLate = bOverdue !== null && bOverdue < 0 ? 1 : 0
      return bLate - aLate || (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999')
        || a.name.localeCompare(b.name, 'zh-CN')
    })
  }, [tasksQuery.data])

  if (tasksQuery.isLoading) return <Typography.Text type="secondary">正在加载 {name} 名下任务...</Typography.Text>
  if (openTasks.length === 0) return <Typography.Text type="secondary">{name} 名下暂无未完成任务</Typography.Text>
  return (
    <div>
      {openTasks.map(task => <PersonTaskLine key={task.id} task={task} onOpen={() => navigate(`/sys/drawing/tasks/${task.id}`)} />)}
    </div>
  )
}

function PersonTaskLine({ task, onOpen }: { task: GovernanceTask; onOpen: () => void }) {
  const today = new Date()
  const diff = dueDayDiff(task.dueDate, today)
  const overdueDays = diff !== null && diff < 0 ? -diff : null
  const escalated = overdueDays !== null && overdueDays >= 3 && task.workflowVersion !== 'LEGACY_PROGRESS'
  return (
    <TaskRow>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
        <Typography.Text strong style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{task.name}</Typography.Text>
        <GovernanceStatusTag status={task.status} />
        {overdueDays !== null
          ? <Tag color="red">已逾期 {overdueDays} 天</Tag>
          : task.dueDate ? <Tag>{task.dueDate}</Tag> : null}
        {escalated && <Tag color="volcano">已升级</Tag>}
      </div>
      <Button type="link" size="small" onClick={onOpen}>进入任务</Button>
    </TaskRow>
  )
}

/** R4 组织与责任看板：部门视角的责任分桶 + 月度治理复盘。 */
export function GovernanceResponsibilityPage() {
  const [month, setMonth] = useState<string>(() => monthKey(new Date(), 0))
  const monthOptions = useMemo(
    () => Array.from({ length: 6 }, (_, index) => monthKey(new Date(), index)),
    [],
  )
  const boardQuery = useQuery({
    queryKey: ['responsibility-board', month],
    queryFn: () => getGovernanceResponsibilityBoard(month),
  })
  const board = boardQuery.data

  const columns: ColumnsType<GovernanceResponsibilityRow> = [
    {
      title: '责任人',
      key: 'person',
      width: 180,
      render: (_, row) => (
        <span>
          <Typography.Text strong>{row.name}</Typography.Text>
          <SubLine>{row.department || '—'}</SubLine>
        </span>
      ),
    },
    { title: '整改中', dataIndex: 'executing', width: 88, align: 'center' },
    { title: '待确认', dataIndex: 'confirming', width: 88, align: 'center' },
    { title: '待验收', dataIndex: 'accepting', width: 88, align: 'center' },
    {
      title: '已逾期',
      key: 'overdue',
      width: 120,
      align: 'center',
      render: (_, row) => row.overdue > 0
        ? <><Danger>{row.overdue}</Danger>{row.escalated > 0 && <SubLine><Warn>升级 {row.escalated}</Warn></SubLine>}</>
        : <span>0</span>,
    },
    { title: '已完成', dataIndex: 'completed', width: 88, align: 'center' },
    { title: '累计负责', dataIndex: 'totalAssigned', width: 96, align: 'center' },
  ]

  const statCards: GovernanceStatCardData[] = [
    {
      key: 'scan', label: `本月扫描（${board?.monthly.month ?? ''}）`, value: board?.monthly.scanRuns ?? null,
      unit: '次', tone: 'default',
      footnote: board ? `核验 ${board.monthly.scannedAssets} 条 · 上月 ${board.monthly.prevScannedAssets} 条` : '加载中…',
    },
    {
      key: 'issues', label: '本月新发现问题', value: board?.monthly.newIssues ?? null,
      unit: '项', tone: 'warn',
      footnote: board ? `上月 ${board.monthly.prevNewIssues} 项 · 环比口径一致` : '加载中…',
    },
    {
      key: 'closed', label: '本月闭环完成', value: board?.monthly.closedTasks ?? null,
      unit: '个', tone: 'success',
      footnote: board ? `上月 ${board.monthly.prevClosedTasks} 个 · 按验收通过时间` : '加载中…',
    },
    {
      key: 'open', label: '当前开放问题', value: board?.monthly.openIssues ?? null,
      unit: '项', tone: 'default',
      footnote: '仍未分派的开放问题存量（随扫描变化）',
    },
    {
      key: 'overdueStock', label: '当前逾期任务', value: board?.monthly.overdueTasks ?? null,
      unit: '个', tone: 'alert',
      footnote: board ? `其中已升级 ${board.monthly.escalatedTasks} 个，管理员待跟进` : '加载中…',
    },
    {
      key: 'escalated', label: '逾期升级中', value: board?.monthly.escalatedTasks ?? null,
      unit: '个', tone: 'alert',
      footnote: '闭环任务逾期满 3 天进入升级提醒',
    },
  ]

  return (
    <Page>
      <Header>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>组织与责任看板</Typography.Title>
          <Typography.Text type="secondary">部门视角看「谁名下多少待办/逾期」——责任到人，复盘有数</Typography.Text>
        </div>
        <Toolbar>
          <Select
            aria-label="复盘月份"
            style={{ width: 180 }}
            value={month}
            onChange={setMonth}
            options={monthOptions.map(key => ({ value: key, label: `${key} 复盘` }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void boardQuery.refetch()}>刷新</Button>
        </Toolbar>
      </Header>

      {boardQuery.isError && (
        <Alert type="error" showIcon message="看板数据加载失败"
          description={boardQuery.error instanceof Error ? boardQuery.error.message : undefined}
          action={<Button size="small" type="primary" onClick={() => void boardQuery.refetch()}>重试</Button>} />
      )}

      <Section>
        <SectionTitle>本月治理复盘 <span className="hint">月份可切换；活动量按月统计，存量项为当前快照</span></SectionTitle>
        <StatCards cards={statCards} />
      </Section>

      <Section>
        <SectionTitle>员工责任一览 <span className="hint">点击任意一行展开该责任人的未完成任务（逾期优先）</span></SectionTitle>
        <Table<GovernanceResponsibilityRow>
          rowKey="userId"
          size="small"
          loading={boardQuery.isLoading}
          columns={columns}
          dataSource={board?.employees ?? []}
          pagination={false}
          expandable={{
            expandRowByClick: true,
            expandedRowRender: record => <PersonOpenTasks userId={record.userId} name={record.name} />,
          }}
          summary={board ? () => {
            const t = board.totals
            return (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0}><Typography.Text strong>部门合计</Typography.Text></Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="center"><Typography.Text strong>{t.executing}</Typography.Text></Table.Summary.Cell>
                <Table.Summary.Cell index={2} align="center"><Typography.Text strong>{t.confirming}</Typography.Text></Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="center"><Typography.Text strong>{t.accepting}</Typography.Text></Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="center">{t.overdue > 0
                  ? <Danger>{t.overdue}</Danger> : <span>0</span>}</Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="center"><Typography.Text strong>{t.completed}</Typography.Text></Table.Summary.Cell>
                <Table.Summary.Cell index={6} align="center"><Typography.Text strong>{t.totalAssigned}</Typography.Text></Table.Summary.Cell>
              </Table.Summary.Row>
            )
          } : undefined}
          locale={{ emptyText: '暂无责任数据' }}
        />
      </Section>

      <Typography.Paragraph type="secondary" style={{ fontSize: 12, marginBottom: 0 }}>
        口径说明：①「已逾期」与整改中/待确认/待验收重叠统计；②「升级 N」仅统计闭环任务逾期满 3 天（与通知升级一致）；
        ③历史只读任务仍计入责任人名下；④点击「进入任务」可查看详情并移交改派。
        {board && <> · 生成时间 {new Date(board.generatedAt).toLocaleString('zh-CN', { hour12: false })}</>}
      </Typography.Paragraph>
    </Page>
  )
}
