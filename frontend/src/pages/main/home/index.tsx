import {
  ApartmentOutlined,
  ArrowRightOutlined,
  BookOutlined,
  CloudUploadOutlined,
  DatabaseOutlined,
  FileSearchOutlined,
  HeartOutlined,
  HistoryOutlined,
  InboxOutlined,
  ScanOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Button, Empty, Space, Tag } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getFavoriteAssets, getMyUploads, searchAssets } from '../../../services/assetService'
import { getGovernanceIssues, getGovernanceScanRuns, getGovernanceTasks } from '../../../features/governance/api'
import { readRecentAssetViews } from '../../../services/recentViews'

const Page = styled.div`
  min-width: 0;
`

const GridTexture = styled.div`
  position: relative;
  padding: 2px 2px 18px;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0.5;
    background-image:
      linear-gradient(to right, rgba(47, 117, 103, 0.05) 1px, transparent 1px),
      linear-gradient(to bottom, rgba(47, 117, 103, 0.05) 1px, transparent 1px);
    background-size: 22px 22px;
    mask-image: linear-gradient(to bottom, rgba(0, 0, 0, 0.7), transparent 85%);
  }
`

const WelcomeBar = styled.header`
  position: relative;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  justify-content: space-between;
  padding: 14px 2px 16px;
`

const WelcomeTitle = styled.h1`
  margin: 0;
  color: #1c2f28;
  font-size: 24px;
  font-weight: 720;
  letter-spacing: 0.2px;
`

const WelcomeMeta = styled.div`
  margin-top: 4px;
  color: #7c8882;
  font-size: 12px;
`

const WelcomeActions = styled(Space)``

const MetricStrip = styled.div`
  position: relative;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
`

const MetricCard = styled.button<{ $tone: string }>`
  position: relative;
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 92px;
  padding: 14px 16px 14px 18px;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #e2e8e4;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(26, 51, 42, 0.04);
  cursor: pointer;
  overflow: hidden;

  &::before {
    content: '';
    position: absolute;
    inset: 0 auto 0 0;
    width: 3px;
    background: ${(props) => props.$tone};
  }

  &:hover,
  &:focus-visible {
    background: #f7faf8;
    border-color: #a9c6bc;
    outline: none;
    transform: translateY(-1px);
    box-shadow: 0 4px 10px rgba(26, 51, 42, 0.08);
    transition: transform 140ms ease, box-shadow 140ms ease;
  }
`

const MetricIcon = styled.span<{ $tone: string; $bg: string }>`
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  color: ${(props) => props.$tone};
  background: ${(props) => props.$bg};
  border-radius: 8px;
  font-size: 18px;
`

const MetricBody = styled.div`
  min-width: 0;
`

const MetricValue = styled.div`
  display: flex;
  gap: 6px;
  align-items: baseline;
`

const MetricNumber = styled.span`
  color: #1f322a;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
`

const MetricUnit = styled.span`
  color: #8b9590;
  font-size: 10px;
`

const MetricLabel = styled.div`
  margin-top: 3px;
  color: #4a5a52;
  font-size: 12px;
  font-weight: 650;
`

const MetricHint = styled.div`
  margin-top: 2px;
  overflow: hidden;
  color: #8b9590;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.55fr) minmax(280px, 0.75fr);
  gap: 12px;
  align-items: start;

  @media (max-width: 1080px) {
    grid-template-columns: 1fr;
  }
`

const Panel = styled.section`
  background: #fff;
  border: 1px solid #e2e8e4;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(26, 51, 42, 0.04);
`

const PanelHeader = styled.header`
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 10px;
  border-bottom: 1px solid #edf1ee;
`

const PanelTitle = styled.h2`
  margin: 0;
  color: #2a3b34;
  font-size: 13px;
  font-weight: 700;
`

const PanelMore = styled(Button)`
  font-size: 11px;
`

const PanelBody = styled.div`
  padding: 6px 16px 12px;
`

const TodoList = styled.ul`
  margin: 0;
  padding: 0;
  list-style: none;
`

const TodoItem = styled.li`
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 11px 0;
  border-bottom: 1px dashed #edf0ee;
  cursor: pointer;

  &:last-child {
    border-bottom: 0;
  }

  &:hover .todo-title {
    color: #2f7567;
  }
`

const TodoIcon = styled.span<{ $tone: string; $bg: string }>`
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: ${(props) => props.$tone};
  background: ${(props) => props.$bg};
  border-radius: 7px;
  font-size: 15px;
`

const TodoTitle = styled.div`
  color: #34453d;
  font-size: 13px;
  font-weight: 650;
  transition: color 140ms ease;
`

const TodoDesc = styled.div`
  margin-top: 2px;
  color: #8b9590;
  font-size: 11px;
`

const TodoBadge = styled.span<{ $tone: string }>`
  display: grid;
  place-items: center;
  min-width: 26px;
  height: 26px;
  padding: 0 8px;
  color: ${(props) => props.$tone};
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
  background: #f2f6f4;
  border-radius: 13px;
`

const RecentItem = styled.div`
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 9px 0;
  border-bottom: 1px dashed #edf0ee;
  cursor: pointer;

  &:last-child {
    border-bottom: 0;
  }

  &:hover .recent-name {
    color: #2f7567;
  }
`

const RecentName = styled.div`
  overflow: hidden;
  flex: 1;
  color: #34453d;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color 140ms ease;
`

const RecentNumber = styled.div`
  color: #8b9590;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
`

const EntryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 8px;
`

const Entry = styled.button`
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  padding: 12px;
  color: inherit;
  text-align: left;
  background: #fbfdfc;
  border: 1px solid #e6ebe8;
  border-radius: 7px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #f2f8f5;
    border-color: #a9c6bc;
    outline: none;
  }
`

const EntryIcon = styled.span`
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #2f7567;
  background: #e8f1ee;
  border-radius: 6px;
  font-size: 14px;
`

const EntryTitle = styled.span`
  color: #26372f;
  font-size: 12px;
  font-weight: 650;
`

const EntryMeta = styled.span`
  color: #8b9590;
  font-size: 10px;
`

const StatRow = styled.div`
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 9px 0;
  border-bottom: 1px dashed #edf0ee;

  &:last-child {
    border-bottom: 0;
  }
`

const StatLabel = styled.span`
  color: #5e6b65;
  font-size: 12px;
`

const StatValue = styled.span`
  color: #22342c;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
`

function formatDate() {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())
}

export default function HomePage() {
  const navigate = useNavigate()
  const favoritesQuery = useQuery({ queryKey: ['favorites'], queryFn: getFavoriteAssets })
  const uploadsQuery = useQuery({ queryKey: ['my-uploads'], queryFn: () => getMyUploads() })
  const pendingQuery = useQuery({
    queryKey: ['home-pending-count'],
    queryFn: () => searchAssets({ query: '', status: 'PENDING_CURATION', page: 1, perPage: 1 }),
  })
  const issuesQuery = useQuery({
    queryKey: ['open-issues'],
    queryFn: () => getGovernanceIssues({ status: 'OPEN' }),
    staleTime: 60_000,
  })
  const tasksQuery = useQuery({
    queryKey: ['home-tasks'],
    queryFn: () => getGovernanceTasks({}),
    staleTime: 60_000,
  })
  const scansQuery = useQuery({
    queryKey: ['home-scan-runs'],
    queryFn: getGovernanceScanRuns,
    staleTime: 60_000,
  })
  const recentViews = readRecentAssetViews()
  const uploadIds = new Set((uploadsQuery.data?.data ?? []).map((asset) => asset.id))
  const myPendingCount = (issuesQuery.data ?? []).filter((issue) => uploadIds.has(issue.assetId)).length
  const anomalyCount = (issuesQuery.data ?? []).filter((issue) => issue.issueType === 'ANOMALOUS_FILE').length
  const pendingTotal = pendingQuery.data?.meta.total ?? 0
  const openIssues = (issuesQuery.data ?? []).length
  const today = new Date()
  const upcomingTasks = (tasksQuery.data ?? []).filter((task) => {
    if (!task.dueDate || task.status === 'COMPLETED' || task.status === 'CLOSED') return false
    const due = new Date(`${task.dueDate}T23:59:59`)
    const days = (due.getTime() - today.getTime()) / 86_400_000
    return days <= 7
  })
  const latestScan = scansQuery.data?.[0]

  return (
    <Page>
      <GridTexture>
        <WelcomeBar>
          <div>
            <WelcomeTitle>工作台</WelcomeTitle>
            <WelcomeMeta>{formatDate()} · 从待办开始，让资料与治理保持有序</WelcomeMeta>
          </div>
          <WelcomeActions wrap>
            <Button icon={<FileSearchOutlined />} onClick={() => navigate('/')}>检索资料</Button>
            <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传资料</Button>
          </WelcomeActions>
        </WelcomeBar>

        <MetricStrip>
          <MetricCard $tone="#b3562b" onClick={() => navigate('/?status=PENDING_CURATION')}>
            <MetricIcon $tone="#b3562b" $bg="#faf0ea"><InboxOutlined /></MetricIcon>
            <MetricBody>
              <MetricValue><MetricNumber>{pendingTotal}</MetricNumber><MetricUnit>份</MetricUnit></MetricValue>
              <MetricLabel>待整理资料</MetricLabel>
              <MetricHint>待补充标准化信息的资料</MetricHint>
            </MetricBody>
          </MetricCard>
          <MetricCard $tone="#2f7567" onClick={() => navigate('/my-uploads')}>
            <MetricIcon $tone="#2f7567" $bg="#e8f1ee"><WarningOutlined /></MetricIcon>
            <MetricBody>
              <MetricValue><MetricNumber>{myPendingCount}</MetricNumber><MetricUnit>项</MetricUnit></MetricValue>
              <MetricLabel>需补充信息</MetricLabel>
              <MetricHint>我的上传中带开放治理问题</MetricHint>
            </MetricBody>
          </MetricCard>
          <MetricCard $tone="#c2452f" onClick={() => navigate('/sys/drawing/issues')}>
            <MetricIcon $tone="#c2452f" $bg="#fbece9"><ScanOutlined /></MetricIcon>
            <MetricBody>
              <MetricValue><MetricNumber>{anomalyCount}</MetricNumber><MetricUnit>项</MetricUnit></MetricValue>
              <MetricLabel>异常文件</MetricLabel>
              <MetricHint>文件缺失或格式异常的问题</MetricHint>
            </MetricBody>
          </MetricCard>
          <MetricCard $tone="#3578a8" onClick={() => navigate('/sys/drawing/operations')}>
            <MetricIcon $tone="#3578a8" $bg="#eaf1f6"><DatabaseOutlined /></MetricIcon>
            <MetricBody>
              <MetricValue><MetricNumber>{openIssues}</MetricNumber><MetricUnit>项</MetricUnit></MetricValue>
              <MetricLabel>开放治理问题</MetricLabel>
              <MetricHint>{upcomingTasks.length > 0 ? `${upcomingTasks.length} 个任务临近或逾期` : '暂无临近到期的任务'}</MetricHint>
            </MetricBody>
          </MetricCard>
        </MetricStrip>

        <MainGrid>
          <Space direction="vertical" size={12} style={{ display: 'flex' }}>
            <Panel>
              <PanelHeader>
                <PanelTitle>待办清单</PanelTitle>
                <PanelMore type="link" size="small" onClick={() => navigate('/sys/drawing/operations')}>全部治理入口 <ArrowRightOutlined /></PanelMore>
              </PanelHeader>
              <PanelBody>
                <TodoList>
                  <TodoItem onClick={() => navigate('/my-uploads')}>
                    <TodoIcon $tone="#2f7567" $bg="#e8f1ee"><WarningOutlined /></TodoIcon>
                    <div>
                      <TodoTitle className="todo-title">补充资料信息</TodoTitle>
                      <TodoDesc>我的上传中有 {myPendingCount} 份资料存在开放治理问题</TodoDesc>
                    </div>
                    <TodoBadge $tone="#2f7567">{myPendingCount}</TodoBadge>
                  </TodoItem>
                  <TodoItem onClick={() => navigate('/?status=PENDING_CURATION')}>
                    <TodoIcon $tone="#b3562b" $bg="#faf0ea"><InboxOutlined /></TodoIcon>
                    <div>
                      <TodoTitle className="todo-title">整理待标准化资料</TodoTitle>
                      <TodoDesc>共 {pendingTotal} 份资料等待补充基地、拉线与功能说明</TodoDesc>
                    </div>
                    <TodoBadge $tone="#b3562b">{pendingTotal}</TodoBadge>
                  </TodoItem>
                  <TodoItem onClick={() => navigate('/sys/drawing/issues')}>
                    <TodoIcon $tone="#c2452f" $bg="#fbece9"><ScanOutlined /></TodoIcon>
                    <div>
                      <TodoTitle className="todo-title">处理异常与问题</TodoTitle>
                      <TodoDesc>{anomalyCount} 个异常文件、{openIssues} 个开放问题待跟进</TodoDesc>
                    </div>
                    <TodoBadge $tone="#c2452f">{openIssues}</TodoBadge>
                  </TodoItem>
                  {upcomingTasks.length > 0 && (
                    <TodoItem onClick={() => navigate('/sys/drawing/operations')}>
                      <TodoIcon $tone="#3578a8" $bg="#eaf1f6"><DatabaseOutlined /></TodoIcon>
                      <div>
                        <TodoTitle className="todo-title">治理任务临近到期</TodoTitle>
                        <TodoDesc>{upcomingTasks.length} 个任务在未来 7 天内到期或已逾期</TodoDesc>
                      </div>
                      <TodoBadge $tone="#3578a8">{upcomingTasks.length}</TodoBadge>
                    </TodoItem>
                  )}
                  {myPendingCount === 0 && pendingTotal === 0 && openIssues === 0 && upcomingTasks.length === 0 && (
                    <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待办，工作已清空" style={{ padding: '18px 0' }} />
                  )}
                </TodoList>
              </PanelBody>
            </Panel>

            <Panel>
              <PanelHeader>
                <PanelTitle>最近查看</PanelTitle>
                <PanelMore type="link" size="small" onClick={() => navigate('/')}>去检索 <ArrowRightOutlined /></PanelMore>
              </PanelHeader>
              <PanelBody>
                {recentViews.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近查看记录" style={{ padding: '18px 0' }} />
                  : recentViews.map((view) => (
                    <RecentItem key={view.assetId} onClick={() => navigate(`/assets/${view.assetId}`)}>
                      <HistoryOutlined style={{ color: '#8b9590' }} />
                      <RecentName className="recent-name">{view.name}</RecentName>
                      <RecentNumber>{view.assetNumber}</RecentNumber>
                    </RecentItem>
                  ))}
              </PanelBody>
            </Panel>
          </Space>

          <Space direction="vertical" size={12} style={{ display: 'flex' }}>
            <Panel>
              <PanelHeader>
                <PanelTitle>快捷入口</PanelTitle>
              </PanelHeader>
              <PanelBody>
                <EntryGrid>
                  <Entry type="button" onClick={() => navigate('/favorites')}>
                    <EntryIcon><HeartOutlined /></EntryIcon>
                    <EntryTitle>我的收藏</EntryTitle>
                    <EntryMeta>{favoritesQuery.data?.length ?? 0} 份资料</EntryMeta>
                  </Entry>
                  <Entry type="button" onClick={() => navigate('/my-uploads')}>
                    <EntryIcon><BookOutlined /></EntryIcon>
                    <EntryTitle>我的上传</EntryTitle>
                    <EntryMeta>{uploadsQuery.data?.meta.total ?? 0} 份资料</EntryMeta>
                  </Entry>
                  <Entry type="button" onClick={() => navigate('/documents')}>
                    <EntryIcon><FileSearchOutlined /></EntryIcon>
                    <EntryTitle>文档中心</EntryTitle>
                    <EntryMeta>检索与维护知识文档</EntryMeta>
                  </Entry>
                  <Entry type="button" onClick={() => navigate('/sys/drawing')}>
                    <EntryIcon><ApartmentOutlined /></EntryIcon>
                    <EntryTitle>数据治理</EntryTitle>
                    <EntryMeta>任务编排与治理工作台</EntryMeta>
                  </Entry>
                  <Entry type="button" onClick={() => navigate('/sys/dictionaries')}>
                    <EntryIcon><BookOutlined /></EntryIcon>
                    <EntryTitle>基础数据</EntryTitle>
                    <EntryMeta>平台、基地与字典维护</EntryMeta>
                  </Entry>
                  <Entry type="button" onClick={() => navigate('/sys/settings')}>
                    <EntryIcon><DatabaseOutlined /></EntryIcon>
                    <EntryTitle>系统管理</EntryTitle>
                    <EntryMeta>用户、角色与操作记录</EntryMeta>
                  </Entry>
                </EntryGrid>
              </PanelBody>
            </Panel>

            <Panel>
              <PanelHeader>
                <PanelTitle>治理概览</PanelTitle>
                <Tag color={latestScan?.status === 'SUCCEEDED' ? 'success' : latestScan?.status === 'FAILED' ? 'error' : 'default'} style={{ margin: 0 }}>
                  {latestScan?.status === 'SUCCEEDED' ? '最近扫描成功' : latestScan?.status === 'FAILED' ? '最近扫描失败' : '尚未运行扫描'}
                </Tag>
              </PanelHeader>
              <PanelBody>
                <StatRow><StatLabel>开放治理问题</StatLabel><StatValue>{openIssues}</StatValue></StatRow>
                <StatRow><StatLabel>累计扫描资产</StatLabel><StatValue>{(scansQuery.data ?? []).reduce((sum, run) => sum + run.scannedAssetCount, 0)}</StatValue></StatRow>
                <StatRow><StatLabel>治理任务</StatLabel><StatValue>{(tasksQuery.data ?? []).length}</StatValue></StatRow>
                <StatRow><StatLabel>临近到期任务</StatLabel><StatValue>{upcomingTasks.length}</StatValue></StatRow>
                <Button block size="small" style={{ marginTop: 10 }} icon={<DatabaseOutlined />} onClick={() => navigate('/sys/drawing/operations')}>查看治理运营</Button>
              </PanelBody>
            </Panel>
          </Space>
        </MainGrid>
      </GridTexture>
    </Page>
  )
}
