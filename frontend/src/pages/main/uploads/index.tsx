import { CloudUploadOutlined, FileOutlined, WarningOutlined } from '@ant-design/icons'
import { Button, Empty, List, Segmented, Space, Spin, Tag, Tooltip, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getMyUploads } from '../../../services/assetService'
import type { AssetStatus } from '../../../types/asset'
import { getGovernanceIssues } from '../../../features/governance/api'
import { assetStatusLabels, scopeLabel } from '../../../features/assets/assetPresentation'
import { AssetStatusTag, AssetTypeTag } from '../../../features/assets/AssetTags'

const Header = styled.header`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-bottom: 10px;
  padding: 0 2px;
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #21322c;
  font-size: 18px;
  font-weight: 650;
`

const Surface = styled.div`
  padding: 0 12px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 5px;

  .ant-list-item {
    min-height: 58px;
    padding: 10px 0;
  }
`

const statusOptions = [
  { value: '', label: '全部状态' },
  ...Object.entries(assetStatusLabels).map(([value, label]) => ({ value, label })),
]

export default function MyUploadsPage() {
  const navigate = useNavigate()
  const [status, setStatus] = useState<AssetStatus | ''>('')
  const query = useQuery({
    queryKey: ['my-uploads', status],
    queryFn: () => getMyUploads(status || undefined),
  })
  const openIssuesQuery = useQuery({
    queryKey: ['open-issues'],
    queryFn: () => getGovernanceIssues({ status: 'OPEN' }),
    staleTime: 60_000,
  })
  const issuesByAsset = useMemo(() => {
    const grouped = new Map<number, string[]>()
    for (const issue of openIssuesQuery.data ?? []) {
      const types = grouped.get(issue.assetId) ?? []
      types.push(issue.issueType)
      grouped.set(issue.assetId, types)
    }
    return grouped
  }, [openIssuesQuery.data])
  const pendingCount = (query.data?.data ?? []).filter((asset) => issuesByAsset.has(asset.id)).length
  return (
    <>
      <Header>
        <div>
          <Title>我的上传</Title>
          <Typography.Text type="secondary">查看自己提交的草稿、待整理和已标准化资料</Typography.Text>
        </div>
        <Space wrap>
          {pendingCount > 0 && (
            <Tag icon={<WarningOutlined />} color="orange">需补充信息 {pendingCount} 项</Tag>
          )}
          <Segmented<AssetStatus | ''> size="small" value={status} onChange={setStatus} options={statusOptions} aria-label="按状态筛选" />
          <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传新资料</Button>
        </Space>
      </Header>
      <Surface>
        {query.isLoading ? <Spin style={{ display: 'block', padding: 56 }} /> : query.isError ? <Empty description="上传列表加载失败"><Button type="primary" onClick={() => void query.refetch()}>重试</Button></Empty> : <List dataSource={query.data?.data ?? []} locale={{ emptyText: '暂无上传资料' }} renderItem={(asset) => {
          const pendingTypes = issuesByAsset.get(asset.id) ?? []
          return <List.Item key={asset.id} onClick={() => navigate(`/assets/${asset.id}`)} style={{ cursor: 'pointer' }}>
            <List.Item.Meta
              avatar={<FileOutlined style={{ marginTop: 5, color: '#2f7567', fontSize: 18 }} />}
              title={asset.name}
              description={<Space wrap size={6}>
                <Typography.Text type="secondary">{asset.assetNumber}</Typography.Text>
                <AssetStatusTag status={asset.status} />
                <AssetTypeTag type={asset.assetType} />
                <Tag>{scopeLabel(asset.scopes[0] ?? { platform: '', productLine: '', base: '', productionLine: '', processSection: '' })}</Tag>
                {pendingTypes.length > 0 && (
                  <Tooltip title={`补充要求 ${pendingTypes.length} 项，点击进入详情查看`}>
                    <Tag color="orange" icon={<WarningOutlined />}>补充要求 {pendingTypes.length} 项</Tag>
                  </Tooltip>
                )}
              </Space>}
            />
          </List.Item>
        }} />}
      </Surface>
    </>
  )
}
