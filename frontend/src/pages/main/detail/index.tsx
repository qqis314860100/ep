import { ArrowLeftOutlined, FileZipOutlined, LinkOutlined, StarFilled, StarOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Space, Spin, Table, Tabs, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import styled from 'styled-components'
import { useAsset, useAssetRelations, useFavorite } from '../../../hooks/useAssets'
import { setFavorite } from '../../../services/assetService'
import type { AssetFile, AssetRelation } from '../../../types/asset'
import { AssetRelationMap } from '../../../features/assets/AssetRelationMap'
import { AssetStatusTag } from '../../../features/assets/AssetTags'
import { DrawingGallery } from './components/DrawingGallery'
import { DrawingInfoPanel } from './components/DrawingInfoPanel'
import { CommentSection } from './components/CommentSection'
import { EquipmentInterconnectionPanel } from './components/EquipmentInterconnectionPanel'

const Page = styled.div`
  max-width: 1180px;
  margin: 0 auto;
`

const Header = styled.header`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 22px;
`

const AssetNumber = styled.div`
  margin-bottom: 5px;
  color: #718078;
  font-size: 13px;
`

const Title = styled.h1`
  margin: 0;
  color: #21322c;
  font-size: 28px;
  font-weight: 650;
`

const SectionTitle = styled.h2`
  margin: 26px 0 12px;
  color: #21322c;
  font-size: 17px;
`

const relationColumns: ColumnsType<AssetRelation> = [
  { title: '关系', dataIndex: 'directionLabel', width: 90, render: (value: string) => <Tag icon={<LinkOutlined />}>{value}</Tag> },
  { title: '关联资产', dataIndex: 'targetAssetName', render: (value: string, record) => <div><Typography.Text strong>{value}</Typography.Text><Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>{record.targetAssetNumber}</Typography.Text></div> },
  { title: '适用范围', dataIndex: 'primaryScope' },
  { title: '状态', dataIndex: 'targetAssetStatus', width: 110, render: (value) => <AssetStatusTag status={value} /> },
]

export default function DrawingDetailPage() {
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const params = useParams()
  const assetId = Number(params.id)
  const assetQuery = useAsset(assetId)
  const relationsQuery = useAssetRelations(assetId)
  const favoriteQuery = useFavorite(assetId)
  const [favoriteSaving, setFavoriteSaving] = useState(false)
  const asset = assetQuery.data

  const toggleFavorite = async () => {
    if (!asset) return
    setFavoriteSaving(true)
    try {
      await setFavorite(asset.id, !favoriteQuery.data)
      await favoriteQuery.refetch()
      message.success(favoriteQuery.data ? '已取消收藏' : '已收藏')
    } finally {
      setFavoriteSaving(false)
    }
  }

  const openFile = (file: AssetFile, preview: boolean) => {
    if (!file.storageKey) return message.info('演示数据未绑定实际文件对象')
    window.open(`/api/v1/assets/${assetId}/files/${file.id}?preview=${preview}`, '_blank', 'noopener,noreferrer')
  }

  if (assetQuery.isLoading) return <Spin />
  if (!asset) return <Empty description="未找到该资产"><Button onClick={() => navigate('/')}>返回检索</Button></Empty>

  return (
    <Page>
      <Header>
        <div>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)} style={{ paddingLeft: 0, marginBottom: 10 }}>返回检索</Button>
          <AssetNumber>{asset.assetNumber}</AssetNumber>
          <Title>{asset.name}</Title>
        </div>
        <Space wrap>
          <Button type={favoriteQuery.data ? 'primary' : 'default'} icon={favoriteQuery.data ? <StarFilled /> : <StarOutlined />} loading={favoriteSaving} onClick={() => void toggleFavorite()}>{favoriteQuery.data ? '已收藏' : '收藏'}</Button>
          <Button icon={<FileZipOutlined />} onClick={() => message.info('资产包打包下载将在对象存储适配器启用后提供')}>打包下载</Button>
        </Space>
      </Header>

      <DrawingInfoPanel asset={asset} />
      <EquipmentInterconnectionPanel equipmentCode={asset.equipmentInterconnectCode} />
      <SectionTitle>图纸与数模文件 <Typography.Text type="secondary" style={{ fontSize: 13 }}>{asset.files.length} 个</Typography.Text></SectionTitle>
      <DrawingGallery files={asset.files} onPreview={(file) => openFile(file, true)} onDownload={(file) => openFile(file, false)} />

      <Tabs
        style={{ marginTop: 28 }}
        items={[
          {
            key: 'comments',
            label: '使用评论',
            children: <CommentSection assetId={asset.id} />,
          },
          {
            key: 'relations',
            label: `关联资产 ${relationsQuery.data?.length ?? 0}`,
            children: relationsQuery.data?.length ? <Space direction="vertical" size={16} style={{ width: '100%' }}><AssetRelationMap asset={asset} relations={relationsQuery.data} /><Table rowKey="id" columns={relationColumns} dataSource={relationsQuery.data} pagination={false} size="small" /></Space> : <Empty description="暂无关联资产" />,
          },
        ]}
      />
    </Page>
  )
}
