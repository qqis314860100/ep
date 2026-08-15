import {
  ApartmentOutlined,
  BookOutlined,
  CloudUploadOutlined,
  FileSearchOutlined,
  HeartOutlined,
  HistoryOutlined,
  InboxOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { Button, Card, Empty, Space, Spin, Statistic, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getFavoriteAssets, getMyUploads, searchAssets } from '../../../services/assetService'
import { getGovernanceIssues } from '../../../features/governance/api'
import { readRecentAssetViews } from '../../../services/recentViews'

const Page = styled.div`
  min-width: 0;
`

const Header = styled.header`
  margin-bottom: 14px;
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #21322c;
  font-size: 18px;
  font-weight: 650;
`

const TodoRow = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
`

const TodoCard = styled(Card)`
  .ant-card-body {
    padding: 14px 16px;
  }
`

const EntryGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
`

const Entry = styled.button`
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  padding: 14px;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #e0e6e2;
  border-radius: 6px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #f4f8f6;
    border-color: #a9c6bc;
    outline: none;
  }
`

const EntryIcon = styled.span`
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #2f7567;
  background: #e8f1ee;
  border-radius: 6px;
  font-size: 16px;
`

const EntryTitle = styled.span`
  color: #26372f;
  font-size: 13px;
  font-weight: 650;
`

const EntryMeta = styled.span`
  color: #7a8781;
  font-size: 11px;
`

const RecentList = styled.ul`
  margin: 0;
  padding: 0;
  list-style: none;
`

const RecentItem = styled.li`
  padding: 8px 0;
  border-bottom: 1px dashed #edf0ee;

  &:last-child {
    border-bottom: 0;
  }
`

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
  const recentViews = readRecentAssetViews()
  const uploadIds = new Set((uploadsQuery.data?.data ?? []).map((asset) => asset.id))
  const myPendingCount = (issuesQuery.data ?? []).filter((issue) => uploadIds.has(issue.assetId)).length
  const anomalyCount = (issuesQuery.data ?? []).filter((issue) => issue.issueType === 'ANOMALOUS_FILE').length

  return (
    <Page>
      <Header>
        <Title>首页</Title>
        <Typography.Text type="secondary">快捷入口与待办提示，帮助快速进入常用工作</Typography.Text>
      </Header>

      <TodoRow>
        <TodoCard size="small">
          <Statistic title="需补充信息" value={myPendingCount} prefix={<WarningOutlined />} valueStyle={{ color: '#b3562b' }} />
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>我的上传中带开放治理问题的资料数</Typography.Text>
        </TodoCard>
        <TodoCard size="small">
          <Statistic title="待整理资料" value={pendingQuery.data?.meta.total ?? 0} prefix={<InboxOutlined />} />
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>待补充标准化信息的资料总数</Typography.Text>
        </TodoCard>
        <TodoCard size="small">
          <Statistic title="异常文件" value={anomalyCount} prefix={<WarningOutlined />} />
          <Typography.Text type="secondary" style={{ fontSize: 11 }}>文件缺失或异常的问题数</Typography.Text>
        </TodoCard>
      </TodoRow>

      <Typography.Title level={5} style={{ marginTop: 0 }}>快捷入口</Typography.Title>
      <EntryGrid>
        <Entry type="button" onClick={() => navigate('/favorites')}>
          <EntryIcon><HeartOutlined /></EntryIcon>
          <EntryTitle>我的收藏</EntryTitle>
          <EntryMeta>{favoritesQuery.data?.length ?? 0} 份收藏资料</EntryMeta>
        </Entry>
        <Entry type="button" onClick={() => navigate('/my-uploads')}>
          <EntryIcon><BookOutlined /></EntryIcon>
          <EntryTitle>我的上传</EntryTitle>
          <EntryMeta>{uploadsQuery.data?.meta.total ?? 0} 份上传资料</EntryMeta>
        </Entry>
        <Entry type="button" onClick={() => navigate('/upload')}>
          <EntryIcon><CloudUploadOutlined /></EntryIcon>
          <EntryTitle>批量上传</EntryTitle>
          <EntryMeta>多文件选择、角色调整与草稿提交</EntryMeta>
        </Entry>
        <Entry type="button" onClick={() => navigate('/sys/drawing/issues')}>
          <EntryIcon><ApartmentOutlined /></EntryIcon>
          <EntryTitle>历史资料治理</EntryTitle>
          <EntryMeta>问题池、扫描与治理任务入口</EntryMeta>
        </Entry>
        <Entry type="button" onClick={() => navigate('/?status=PENDING_CURATION')}>
          <EntryIcon><FileSearchOutlined /></EntryIcon>
          <EntryTitle>待整理资料</EntryTitle>
          <EntryMeta>检索待补充标准化信息的资料</EntryMeta>
        </Entry>
      </EntryGrid>

      <Typography.Title level={5} style={{ marginTop: 20 }}>最近查看</Typography.Title>
      <Card size="small">
        {recentViews.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无最近查看记录" />
          : <RecentList>
            {recentViews.map((view) => (
              <RecentItem key={view.assetId}>
                <Button type="link" style={{ padding: 0 }} icon={<HistoryOutlined />} onClick={() => navigate(`/assets/${view.assetId}`)}>
                  {view.name} <Typography.Text type="secondary" style={{ fontSize: 11 }}>· {view.assetNumber}</Typography.Text>
                </Button>
              </RecentItem>
            ))}
          </RecentList>}
      </Card>

      {issuesQuery.isLoading && <Spin size="small" />}
      <Space style={{ marginTop: 16 }}><Button type="primary" icon={<FileSearchOutlined />} onClick={() => navigate('/')}>去检索资料</Button></Space>
    </Page>
  )
}
