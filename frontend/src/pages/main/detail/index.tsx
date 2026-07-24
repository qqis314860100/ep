import {
  ArrowLeftOutlined,
  FileZipOutlined,
  LinkOutlined,
  RightOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Space, Spin, Tag } from 'antd'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import styled from 'styled-components'
import { AssetStatusTag } from '../../../features/assets/AssetTags'
import { useAsset, useAssetRelations, useFavorite } from '../../../hooks/useAssets'
import { setFavorite } from '../../../services/assetService'
import type { AssetFile } from '../../../types/asset'
import { CommentSection } from './components/CommentSection'
import { DrawingGallery } from './components/DrawingGallery'
import { DrawingInfoPanel } from './components/DrawingInfoPanel'

const Page = styled.div`
  width: 100%;
`

const Header = styled.header`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid #dfe5e1;
`

const HeaderMain = styled.div`
  min-width: 0;
`

const BackButton = styled(Button)`
  margin: 0 0 6px -8px;
  color: #64736c;
`

const HeadingLine = styled.div`
  display: flex;
  align-items: baseline;
  gap: 12px;
`

const Title = styled.h1`
  margin: 0;
  overflow: hidden;
  color: #20312a;
  font-size: 22px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const AssetNumber = styled.span`
  flex: 0 0 auto;
  color: #77847e;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 12px;
`

const PrimaryGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  gap: 14px;
  align-items: start;
`

const InfoColumn = styled.div`
  position: sticky;
  top: 82px;
  min-width: 0;
`

const SectionHeader = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
`

const SectionTitle = styled.h2`
  margin: 0;
  color: #263831;
  font-size: 15px;
  font-weight: 650;
`

const SectionMeta = styled.span`
  color: #84908a;
  font-size: 11px;
`

const RelationSection = styled.section`
  margin-top: 18px;
`

const RelationGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 8px;
`

const RelationCard = styled.button`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 74px;
  padding: 11px 12px;
  color: inherit;
  text-align: left;
  background: #f8faf8;
  border: 1px solid #e0e6e2;
  border-radius: 5px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #f0f6f3;
    border-color: #a9c5bb;
    outline: none;
  }
`

const RelationBody = styled.div`
  min-width: 0;
`

const RelationTop = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 5px;

  .ant-tag {
    margin: 0;
  }
`

const RelationName = styled.div`
  overflow: hidden;
  color: #2d3e36;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const RelationMeta = styled.div`
  margin-top: 3px;
  overflow: hidden;
  color: #7a8781;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const CommentArea = styled.div`
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid #dfe5e1;
`

const LoadingPage = styled.div`
  display: grid;
  min-height: 420px;
  place-items: center;
`

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
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收藏操作失败')
    } finally {
      setFavoriteSaving(false)
    }
  }

  const openFile = (file: AssetFile, preview: boolean) => {
    if (!file.storageKey) return message.info('该文件尚未绑定可访问的存储对象')
    window.open(`/api/v1/assets/${assetId}/files/${file.id}?preview=${preview}`, '_blank', 'noopener,noreferrer')
  }

  if (assetQuery.isLoading) return <LoadingPage><Spin /></LoadingPage>
  if (!asset) return <Empty description="未找到该资产"><Button onClick={() => navigate('/')}>返回检索</Button></Empty>

  const relations = relationsQuery.data ?? []

  return (
    <Page>
      <Header>
        <HeaderMain>
          <BackButton type="text" size="small" icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回检索</BackButton>
          <HeadingLine>
            <Title>{asset.name}</Title>
            <AssetNumber>{asset.assetNumber}</AssetNumber>
          </HeadingLine>
        </HeaderMain>
        <Space>
          <Button
            type={favoriteQuery.data ? 'primary' : 'default'}
            icon={favoriteQuery.data ? <StarFilled /> : <StarOutlined />}
            loading={favoriteSaving}
            onClick={() => void toggleFavorite()}
          >
            {favoriteQuery.data ? '已收藏' : '收藏'}
          </Button>
          <Button icon={<FileZipOutlined />} onClick={() => message.info('资产包打包下载将在对象存储适配器启用后提供')}>打包下载</Button>
        </Space>
      </Header>

      <PrimaryGrid>
        <DrawingGallery assetId={asset.id} files={asset.files} onPreview={(file) => openFile(file, true)} onDownload={(file) => openFile(file, false)} />
        <InfoColumn><DrawingInfoPanel asset={asset} /></InfoColumn>
      </PrimaryGrid>

      <RelationSection>
        <SectionHeader>
          <SectionTitle>关联资料</SectionTitle>
          <SectionMeta>{relations.length} 项业务关系</SectionMeta>
        </SectionHeader>
        {relationsQuery.isLoading ? <Spin /> : relations.length > 0 ? (
          <RelationGrid>
            {relations.map((relation) => (
              <RelationCard key={relation.id} type="button" onClick={() => navigate(`/assets/${relation.targetAssetId}`)}>
                <RelationBody>
                  <RelationTop>
                    <Tag icon={<LinkOutlined />}>{relation.directionLabel}</Tag>
                    <AssetStatusTag status={relation.targetAssetStatus} />
                  </RelationTop>
                  <RelationName>{relation.targetAssetName}</RelationName>
                  <RelationMeta>{relation.targetAssetNumber} · {relation.primaryScope}</RelationMeta>
                </RelationBody>
                <RightOutlined style={{ color: '#819089' }} />
              </RelationCard>
            ))}
          </RelationGrid>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联资料" style={{ margin: '8px 0' }} />}
      </RelationSection>

      <CommentArea><CommentSection assetId={asset.id} /></CommentArea>
    </Page>
  )
}
