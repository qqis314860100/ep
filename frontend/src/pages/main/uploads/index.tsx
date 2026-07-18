import { CloudUploadOutlined, FileOutlined } from '@ant-design/icons'
import { Button, Empty, List, Space, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getMyUploads } from '../../../services/assetService'
import { scopeLabel } from '../../../features/assets/assetPresentation'
import { AssetStatusTag, AssetTypeTag } from '../../../features/assets/AssetTags'

const Header = styled.header`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 22px;
`

const Title = styled.h1`
  margin: 0 0 6px;
  color: #21322c;
  font-size: 26px;
  font-weight: 650;
`

const Surface = styled.div`
  padding: 4px 18px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 8px;
`

export default function MyUploadsPage() {
  const navigate = useNavigate()
  const query = useQuery({ queryKey: ['my-uploads'], queryFn: () => getMyUploads() })
  return (
    <>
      <Header><div><Title>我的上传</Title><Typography.Text type="secondary">查看自己提交的草稿、待整理和已标准化资料</Typography.Text></div><Button type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传新资料</Button></Header>
      <Surface>
        {query.isLoading ? <Spin style={{ display: 'block', padding: 56 }} /> : query.isError ? <Empty description="上传列表加载失败"><Button type="primary" onClick={() => void query.refetch()}>重试</Button></Empty> : <List dataSource={query.data?.data ?? []} locale={{ emptyText: '暂无上传资料' }} renderItem={(asset) => <List.Item key={asset.id} onClick={() => navigate(`/assets/${asset.id}`)} style={{ cursor: 'pointer' }}><List.Item.Meta avatar={<FileOutlined style={{ marginTop: 5, color: '#2f7567', fontSize: 18 }} />} title={asset.name} description={<Space wrap size={6}><Typography.Text type="secondary">{asset.assetNumber}</Typography.Text><AssetStatusTag status={asset.status} /><AssetTypeTag type={asset.assetType} /><Tag>{scopeLabel(asset.scopes[0] ?? { platform: '', productLine: '', base: '', productionLine: '', processSection: '' })}</Tag></Space>} /></List.Item>} />}
      </Surface>
    </>
  )
}
