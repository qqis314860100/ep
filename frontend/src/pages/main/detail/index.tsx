import {
  ArrowLeftOutlined,
  DeleteOutlined,
  EditOutlined,
  FileZipOutlined,
  LinkOutlined,
  RightOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons'
import { App as AntdApp, Button, Empty, Select, Space, Spin, Tag, Tooltip } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import styled from 'styled-components'
import { AssetStatusTag } from '../../../features/assets/AssetTags'
import { AssetDocumentRelationModal } from '../../../features/documents/components/AssetDocumentRelationModal'
import { assetDocumentRelationLabels } from '../../../features/documents/assetDocumentRelationPresentation'
import { useAsset, useAssetRelations, useFavorite } from '../../../hooks/useAssets'
import { getAssetDocuments, getAssetFileUrl, getAssetPackageUrl, removeAssetRelation, setFavorite } from '../../../services/assetService'
import {
  changeAssetDocumentRelationType,
  createAssetDocumentRelation,
  removeAssetDocumentRelation,
} from '../../../services/assetDocumentRelationService'
import type { AssetFile, AssetRelation } from '../../../types/asset'
import type { AssetDocumentRelation, AssetDocumentRelationType } from '../../../types/document'
import { AssetRelationDialog } from './components/AssetRelationDialog'
import { CommentSection } from './components/CommentSection'
import { DrawingGallery } from './components/DrawingGallery'
import { DrawingInfoPanel } from './components/DrawingInfoPanel'

const Page = styled.div`
  width: 100%;
`

const Header = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 10px;
  padding: 0 2px 10px;
  border-bottom: 1px solid #dfe5e1;
`

const HeaderMain = styled.div`
  min-width: 0;
`

const BackButton = styled(Button)`
  margin: 0 0 3px -8px;
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
  font-size: 18px;
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
  grid-template-columns: minmax(0, 1fr) 304px;
  gap: 10px;
  align-items: start;
`

const InfoColumn = styled.div`
  position: sticky;
  top: 64px;
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
  margin-top: 12px;
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

const ManagedRelationCard = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
  min-height: 74px;
  padding: 11px 12px;
  background: #f8faf8;
  border: 1px solid #e0e6e2;
  border-radius: 5px;
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

const RelationNote = styled.div`
  margin-top: 4px;
  display: -webkit-box;
  overflow: hidden;
  color: #8b9590;
  font-size: 10px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
`

const RelationActions = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;

  .ant-select { width: 74px; }
`

const CommentArea = styled.div`
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #dfe5e1;
`

const LoadingPage = styled.div`
  display: grid;
  min-height: 420px;
  place-items: center;
`

export default function DrawingDetailPage() {
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const params = useParams()
  const assetId = Number(params.id)
  const assetQuery = useAsset(assetId)
  const relationsQuery = useAssetRelations(assetId)
  const documentRelationsQuery = useQuery({
    queryKey: ['asset-documents', assetId],
    queryFn: () => getAssetDocuments(assetId),
    enabled: Number.isFinite(assetId),
  })
  const favoriteQuery = useFavorite(assetId)
  const [favoriteSaving, setFavoriteSaving] = useState(false)
  const [documentRelationDialogOpen, setDocumentRelationDialogOpen] = useState(false)
  const [relationDialogOpen, setRelationDialogOpen] = useState(false)
  const [editingRelation, setEditingRelation] = useState<AssetRelation>()
  const asset = assetQuery.data

  const removeAssetRelationMutation = useMutation({
    mutationFn: (relation: AssetRelation) => removeAssetRelation(assetId, relation.id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['asset-relations'] })
      void message.success('关系已解除')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '解除关系失败'),
  })
  const confirmRemoveRelation = (relation: AssetRelation) => {
    modal.confirm({
      title: '解除资产关系',
      content: `将解除「${relation.directionLabel}」关系（仅删除关系，不删除任何资产或文件），是否继续？`,
      okText: '解除',
      okButtonProps: { danger: true },
      onOk: () => removeAssetRelationMutation.mutate(relation),
    })
  }

  const refreshDocumentRelations = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['asset-documents'] }),
      queryClient.invalidateQueries({ queryKey: ['document-asset-relations'] }),
    ])
  }
  const createRelation = useMutation({
    mutationFn: createAssetDocumentRelation,
    onSuccess: async () => {
      await refreshDocumentRelations()
      setDocumentRelationDialogOpen(false)
      void message.success('关联已建立')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '建立关联失败'),
  })
  const changeRelation = useMutation({
    mutationFn: ({ relation, relationType }: { relation: AssetDocumentRelation; relationType: AssetDocumentRelationType }) =>
      changeAssetDocumentRelationType(relation.id, { relationType, version: relation.version }),
    onSuccess: async () => {
      await refreshDocumentRelations()
      void message.success('关联类型已更新')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '更新关联失败'),
  })
  const removeRelation = useMutation({
    mutationFn: (relation: AssetDocumentRelation) => removeAssetDocumentRelation(relation.id, relation.version),
    onSuccess: async () => {
      await refreshDocumentRelations()
      void message.success('关联已解除')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '解除关联失败'),
  })
  const confirmRemove = (relation: AssetDocumentRelation) => {
    modal.confirm({
      title: '解除关联？',
      content: '解除后不会删除资产、文档或任何版本文件。',
      okText: '解除关联',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: () => removeRelation.mutateAsync(relation),
    })
  }

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
    window.open(getAssetFileUrl(assetId, file, preview), '_blank', 'noopener,noreferrer')
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
          <Button icon={<FileZipOutlined />} onClick={() => {
            const packageUrl = asset && getAssetPackageUrl(asset.id)
            if (packageUrl) window.open(packageUrl, '_blank', 'noopener,noreferrer')
            else message.info('演示模式暂不支持资产包打包下载')
          }}>打包下载</Button>
        </Space>
      </Header>

      <PrimaryGrid>
        <DrawingGallery assetId={asset.id} files={asset.files} onPreview={(file) => openFile(file, true)} onDownload={(file) => openFile(file, false)} />
        <InfoColumn><DrawingInfoPanel asset={asset} /></InfoColumn>
      </PrimaryGrid>

      <RelationSection>
        <SectionHeader>
          <SectionTitle>关联资料</SectionTitle>
          <Space size={8}><SectionMeta>{relations.length} 项业务关系</SectionMeta><Button size="small" icon={<LinkOutlined />} onClick={() => { setEditingRelation(undefined); setRelationDialogOpen(true) }}>新增关系</Button></Space>
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
                  {relation.description && <RelationNote>{relation.description}</RelationNote>}
                  <RelationActions>
                    <Button size="small" type="link" icon={<EditOutlined />} onClick={(event) => { event.stopPropagation(); setEditingRelation(relation); setRelationDialogOpen(true) }}>修改</Button>
                    <Button size="small" type="link" danger icon={<DeleteOutlined />} onClick={(event) => { event.stopPropagation(); confirmRemoveRelation(relation) }}>解除</Button>
                  </RelationActions>
                </RelationBody>
                <RightOutlined style={{ color: '#819089' }} />
              </RelationCard>
            ))}
          </RelationGrid>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联资料" style={{ margin: '8px 0' }} />}
      </RelationSection>

      <RelationSection>
        <SectionHeader>
          <SectionTitle>关联文档</SectionTitle>
          <Space size={8}><SectionMeta>{documentRelationsQuery.data?.length ?? 0} 项知识文档</SectionMeta><Button size="small" icon={<LinkOutlined />} onClick={() => setDocumentRelationDialogOpen(true)}>关联文档</Button></Space>
        </SectionHeader>
        {documentRelationsQuery.isLoading ? <Spin /> : (documentRelationsQuery.data?.length ?? 0) > 0 ? (
          <RelationGrid>
            {documentRelationsQuery.data?.map(({ relation, document }) => (
              <ManagedRelationCard key={relation.id}>
                <RelationBody>
                  <RelationTop><Tag icon={<LinkOutlined />}>{assetDocumentRelationLabels[relation.relationType]}文档</Tag></RelationTop>
                  <Button type="link" size="small" style={{ height: 'auto', padding: 0, fontSize: 13, fontWeight: 650 }} onClick={() => navigate(`/documents/${document.id}`)}>{document.title}</Button>
                  <RelationMeta>{document.documentNumber} · 当前版本 {document.currentVersion.versionNumber}</RelationMeta>
                </RelationBody>
                <RelationActions>
                  <Select aria-label={`${document.title}关联类型`} size="small" value={relation.relationType} loading={changeRelation.isPending} options={(Object.keys(assetDocumentRelationLabels) as AssetDocumentRelationType[]).map((type) => ({ value: type, label: assetDocumentRelationLabels[type] }))} onChange={(relationType: AssetDocumentRelationType) => changeRelation.mutate({ relation, relationType })} />
                  <Tooltip title="解除关联"><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`解除与${document.title}的关联`} loading={removeRelation.isPending} onClick={() => confirmRemove(relation)} /></Tooltip>
                  <Tooltip title="打开文档"><Button type="text" size="small" icon={<RightOutlined />} aria-label={`打开${document.title}`} onClick={() => navigate(`/documents/${document.id}`)} /></Tooltip>
                </RelationActions>
              </ManagedRelationCard>
            ))}
          </RelationGrid>
        ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联文档" style={{ margin: '8px 0' }} />}
      </RelationSection>

      <CommentArea><CommentSection assetId={asset.id} /></CommentArea>
      <AssetRelationDialog
        open={relationDialogOpen}
        assetId={asset.id}
        relation={editingRelation}
        onClose={() => setDocumentRelationDialogOpen(false)}
        onSaved={() => void message.success(editingRelation ? '关系已更新' : '关系已建立')}
      />
      <AssetDocumentRelationModal
        subject={{ kind: 'asset', id: asset.id }}
        open={documentRelationDialogOpen}
        saving={createRelation.isPending}
        onClose={() => setDocumentRelationDialogOpen(false)}
        onSubmit={(input) => createRelation.mutate(input)}
      />
    </Page>
  )
}
