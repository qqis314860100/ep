import { DeleteOutlined, FileOutlined, SearchOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Empty, List, Space, Spin, Tag, Typography } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getFavoriteAssets, setFavorite } from '../../../services/assetService'
import type { Asset } from '../../../types/asset'
import { scopeLabel } from '../../../features/assets/assetPresentation'
import { AssetStatusTag, AssetTypeTag } from '../../../features/assets/AssetTags'

const Header = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
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
`

const Item = styled(List.Item)`
  min-height: 58px;
  padding: 10px 0 !important;
  cursor: pointer;

  &:hover .favorite-title {
    color: #2f7567;
  }
`

const AssetTitle = styled.div`
  margin-bottom: 4px;
  color: #26372f;
  font-size: 15px;
  font-weight: 600;
  transition: color 160ms ease;
`

interface FavoriteRowProps {
  asset: Asset
  onOpen: () => void
  onRemove: () => void
}

function FavoriteRow({ asset, onOpen, onRemove }: FavoriteRowProps) {
  return (
    <Item
      actions={[<Button key="remove" type="text" icon={<DeleteOutlined />} aria-label={`取消收藏 ${asset.name}`} onClick={(event) => { event.stopPropagation(); onRemove() }}>取消收藏</Button>]}
      onClick={onOpen}
    >
      <List.Item.Meta
        avatar={<FileOutlined style={{ marginTop: 5, color: '#2f7567', fontSize: 18 }} />}
        title={<AssetTitle className="favorite-title">{asset.name}</AssetTitle>}
        description={(
          <Space direction="vertical" size={6}>
            <Typography.Text type="secondary">{asset.assetNumber} · {scopeLabel(asset.scopes[0] ?? { platform: '', productLine: '', base: '', productionLine: '', processSection: '' })}</Typography.Text>
            <Space wrap size={5}>
              <AssetStatusTag status={asset.status} />
              <AssetTypeTag type={asset.assetType} />
              {asset.specialties.slice(0, 3).map((specialty) => <Tag key={specialty}>{specialty}</Tag>)}
            </Space>
          </Space>
        )}
      />
    </Item>
  )
}

export default function FavoritesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = AntdApp.useApp()
  const favoritesQuery = useQuery({ queryKey: ['favorites'], queryFn: getFavoriteAssets })

  const removeFavorite = async (assetId: number) => {
    try {
      await setFavorite(assetId, false)
      await queryClient.invalidateQueries({ queryKey: ['favorites'] })
      await queryClient.invalidateQueries({ queryKey: ['asset-favorite', assetId] })
      message.success('已取消收藏')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '取消收藏失败')
    }
  }

  return (
    <>
      <Header>
        <div>
          <Title>我的收藏</Title>
          <Typography.Text type="secondary">集中查看经常使用的图纸、数模和模块资产</Typography.Text>
        </div>
        <Button icon={<SearchOutlined />} onClick={() => navigate('/')}>继续检索</Button>
      </Header>
      <Surface>
        {favoritesQuery.isLoading ? <Spin style={{ display: 'block', padding: 56 }} /> : favoritesQuery.isError ? <Empty description="收藏列表加载失败"><Button type="primary" onClick={() => void favoritesQuery.refetch()}>重试</Button></Empty> : <List dataSource={favoritesQuery.data ?? []} locale={{ emptyText: <Empty description="还没有收藏资料"><Button type="link" onClick={() => navigate('/')}>去检索资料</Button></Empty> }} renderItem={(asset) => <FavoriteRow key={asset.id} asset={asset} onOpen={() => navigate(`/assets/${asset.id}`)} onRemove={() => void removeFavorite(asset.id)} />} />}
      </Surface>
    </>
  )
}
