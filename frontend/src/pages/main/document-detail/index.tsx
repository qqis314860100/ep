import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FileImageOutlined,
  FileOutlined,
  FilePdfOutlined,
  LinkOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Select, Skeleton, Tag, Tooltip } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import styled from 'styled-components'
import { DocumentPreview } from '../../../features/documents/components/DocumentPreview'
import {
  documentCategoryName,
  formatDocumentTime,
  formatFileSize,
} from '../../../features/documents/documentPresentation'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { getDocument, getDocumentAssetRelations } from '../../../services/documentService'
import {
  changeAssetDocumentRelationType,
  createAssetDocumentRelation,
  removeAssetDocumentRelation,
} from '../../../services/assetDocumentRelationService'
import type { AssetDocumentRelation, AssetDocumentRelationType, DocumentFile } from '../../../types/document'
import { AssetDocumentRelationModal } from '../../../features/documents/components/AssetDocumentRelationModal'
import { assetDocumentRelationLabels } from '../../../features/documents/assetDocumentRelationPresentation'

const Page = styled.div`
  min-width: 0;
`

const PageBar = styled.header`
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding-bottom: 10px;
`

const Heading = styled.h1`
  margin: 0;
  overflow: hidden;
  color: #26352f;
  font-size: 17px;
  font-weight: 680;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const DocumentNumber = styled.div`
  margin-top: 2px;
  color: #84908a;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
`

const Spacer = styled.div`
  flex: 1;
`

const Workspace = styled.div`
  display: grid;
  grid-template-columns: 224px minmax(420px, 1fr) 282px;
  height: calc(100vh - 122px);
  min-height: 560px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dbe2de;
  border-radius: 5px;

  @media (max-width: 1180px) {
    grid-template-columns: 204px minmax(360px, 1fr) 252px;
  }
`

const FilePanel = styled.aside`
  min-width: 0;
  overflow: auto;
  background: #fafcfb;
  border-right: 1px solid #dbe2de;
`

const PanelHeader = styled.div`
  display: flex;
  align-items: center;
  min-height: 42px;
  padding: 0 12px;
  color: #45534d;
  border-bottom: 1px solid #e3e8e5;
  font-size: 12px;
  font-weight: 650;
`

const FileButton = styled.button<{ $active: boolean }>`
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr);
  align-items: center;
  width: calc(100% - 12px);
  min-height: 54px;
  margin: 6px;
  padding: 5px 8px;
  color: ${({ $active }) => ($active ? '#245f54' : '#4b5953')};
  text-align: left;
  background: ${({ $active }) => ($active ? '#e8f1ee' : 'transparent')};
  border: 1px solid ${({ $active }) => ($active ? '#cbded7' : 'transparent')};
  border-radius: 4px;
  cursor: pointer;

  &:hover,
  &:focus-visible { background: #eef4f1; outline: none; }

  .anticon { justify-self: center; color: #467c6f; font-size: 16px; }
`

const FileName = styled.div`
  overflow: hidden;
  font-size: 11px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const FileMeta = styled.div`
  margin-top: 3px;
  color: #89948f;
  font-size: 10px;
`

const InfoPanel = styled.aside`
  min-width: 0;
  overflow: auto;
  background: #fff;
  border-left: 1px solid #dbe2de;
`

const InfoBody = styled.div`
  padding: 12px 14px;
`

const InfoTitle = styled.div`
  color: #2e3d36;
  font-size: 14px;
  font-weight: 680;
  line-height: 1.45;
`

const Summary = styled.div`
  margin-top: 8px;
  color: #69756f;
  font-size: 11px;
  line-height: 1.65;
`

const InfoGroup = styled.dl`
  margin: 14px 0 0;
  padding-top: 6px;
  border-top: 1px solid #e9edeb;
`

const InfoRow = styled.div`
  display: grid;
  grid-template-columns: 68px minmax(0, 1fr);
  gap: 8px;
  padding: 7px 0;
  border-bottom: 1px solid #f0f2f1;
  font-size: 11px;

  dt { color: #8a9590; }
  dd { margin: 0; color: #46534d; overflow-wrap: anywhere; }
`

const State = styled.div`
  padding: 24px;
  background: #fff;
  border: 1px solid #dbe2de;
`

const RelationActions = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 4px;

  .ant-select { min-width: 74px; }
`

function icon(file: DocumentFile) {
  if (file.format === 'PDF') return <FilePdfOutlined />
  if (['PNG', 'JPG', 'JPEG', 'TIFF'].includes(file.format)) return <FileImageOutlined />
  return <FileOutlined />
}

export default function DocumentDetailPage() {
  const navigate = useNavigate()
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const { id } = useParams()
  const documentId = Number(id)
  const documentQuery = useQuery({
    queryKey: ['document', documentId],
    queryFn: () => getDocument(documentId),
    enabled: Number.isFinite(documentId),
  })
  const relationsQuery = useQuery({
    queryKey: ['document-asset-relations', documentId],
    queryFn: () => getDocumentAssetRelations(documentId),
    enabled: Number.isFinite(documentId),
  })
  const categoryQuery = useQuery({ queryKey: ['document-categories'], queryFn: getDictionaryItems })
  const categories = useMemo(() => (categoryQuery.data ?? [])
    .filter((item) => item.category === 'DOCUMENT_CATEGORY'), [categoryQuery.data])
  const [selectedFileId, setSelectedFileId] = useState<number>()
  const [relationDialogOpen, setRelationDialogOpen] = useState(false)
  const document = documentQuery.data
  useEffect(() => {
    if (document && selectedFileId === undefined) setSelectedFileId(document.currentVersion.files[0]?.id)
  }, [document, selectedFileId])
  const selectedFile = document?.currentVersion.files.find((file) => file.id === selectedFileId)
  const refreshRelations = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['document-asset-relations'] }),
      queryClient.invalidateQueries({ queryKey: ['asset-documents'] }),
    ])
  }
  const createRelation = useMutation({
    mutationFn: createAssetDocumentRelation,
    onSuccess: async () => {
      await refreshRelations()
      setRelationDialogOpen(false)
      void message.success('关联已建立')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '建立关联失败'),
  })
  const changeRelation = useMutation({
    mutationFn: ({ relation, relationType }: { relation: AssetDocumentRelation; relationType: AssetDocumentRelationType }) =>
      changeAssetDocumentRelationType(relation.id, { relationType, version: relation.version }),
    onSuccess: async () => {
      await refreshRelations()
      void message.success('关联类型已更新')
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '更新关联失败'),
  })
  const removeRelation = useMutation({
    mutationFn: (relation: AssetDocumentRelation) => removeAssetDocumentRelation(relation.id, relation.version),
    onSuccess: async () => {
      await refreshRelations()
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

  if (documentQuery.isLoading) return <State><Skeleton active paragraph={{ rows: 10 }} /></State>
  if (documentQuery.isError || !document) {
    const message = documentQuery.error instanceof Error ? documentQuery.error.message : '文档不存在或不可访问'
    return (
      <State>
        <Alert
          type="error"
          showIcon
          message="无法打开文档"
          description={message}
          action={<Button icon={<ReloadOutlined />} onClick={() => documentQuery.refetch()}>重新加载</Button>}
        />
      </State>
    )
  }

  return (
    <Page>
      <PageBar>
        <Button type="text" icon={<ArrowLeftOutlined />} aria-label="返回文档中心" onClick={() => navigate('/documents')} />
        <div style={{ minWidth: 0 }}>
          <Heading>{document.title}</Heading>
          <DocumentNumber>{document.documentNumber}</DocumentNumber>
        </div>
        <Spacer />
        <Button icon={<LinkOutlined />} aria-label="关联资产" onClick={() => setRelationDialogOpen(true)}>关联资产</Button>
        <Tag color="green">已发布</Tag>
        <Tag color="blue">当前版本 {document.currentVersion.versionNumber}</Tag>
      </PageBar>
      <Workspace>
        <FilePanel aria-label="当前版本文件">
          <PanelHeader>当前版本文件 · {document.currentVersion.files.length}</PanelHeader>
          {document.currentVersion.files.map((file) => (
            <FileButton
              key={file.id}
              type="button"
              $active={file.id === selectedFileId}
              aria-pressed={file.id === selectedFileId}
              aria-label={`${file.name} ${file.format}`}
              onClick={() => setSelectedFileId(file.id)}
            >
              {icon(file)}
              <div>
                <FileName>{file.name}</FileName>
                <FileMeta>{file.format} · {formatFileSize(file.sizeBytes)}</FileMeta>
              </div>
            </FileButton>
          ))}
        </FilePanel>
        {selectedFile && (
          <DocumentPreview documentId={document.id} versionId={document.currentVersion.id} file={selectedFile} />
        )}
        <InfoPanel aria-label="文档信息">
          <PanelHeader>文档信息</PanelHeader>
          <InfoBody>
            <InfoTitle>{document.title}</InfoTitle>
            <Summary>{document.summary}</Summary>
            <InfoGroup>
              <InfoRow><dt>分类</dt><dd>{documentCategoryName(document.categoryCode, categories)}</dd></InfoRow>
              <InfoRow><dt>维护人</dt><dd>{document.maintainerName}</dd></InfoRow>
              <InfoRow><dt>所属部门</dt><dd>{document.maintainerDepartment || '-'}</dd></InfoRow>
              <InfoRow><dt>适用范围</dt><dd>{document.scopeMode === 'GLOBAL' ? '全局通用' : document.scopeMode === 'SPECIFIED' ? `${document.scopes.length} 组指定范围` : '范围待补充'}</dd></InfoRow>
              <InfoRow><dt>版本号</dt><dd>{document.currentVersion.versionNumber}</dd></InfoRow>
              <InfoRow><dt>变更说明</dt><dd>{document.currentVersion.changeSummary}</dd></InfoRow>
              <InfoRow><dt>发布时间</dt><dd>{formatDocumentTime(document.currentVersion.publishedAt)}</dd></InfoRow>
              <InfoRow><dt>发布人</dt><dd>{document.currentVersion.publishedBy || '-'}</dd></InfoRow>
              <InfoRow><dt>内容摘要</dt><dd>{selectedFile?.contentSha256.slice(0, 16) || '-'}</dd></InfoRow>
            </InfoGroup>
            <InfoGroup>
              <InfoRow><dt>关联对象</dt><dd>{relationsQuery.isLoading ? '加载中' : `${relationsQuery.data?.length ?? 0} 项`}</dd></InfoRow>
              {(relationsQuery.data ?? []).map(({ relation, asset }) => (
                <InfoRow key={relation.id}>
                  <dt><Tag color="blue">{assetDocumentRelationLabels[relation.relationType]}</Tag></dt>
                  <dd>
                    <Button type="link" size="small" onClick={() => navigate(`/assets/${asset.id}`)}>{asset.name}</Button>
                    <RelationActions>
                      <Select
                        size="small"
                        value={relation.relationType}
                        aria-label={`${asset.name}关联类型`}
                        loading={changeRelation.isPending}
                        options={(Object.keys(assetDocumentRelationLabels) as AssetDocumentRelationType[]).map((type) => ({ value: type, label: assetDocumentRelationLabels[type] }))}
                        onChange={(relationType: AssetDocumentRelationType) => changeRelation.mutate({ relation, relationType })}
                      />
                      <Tooltip title="解除关联"><Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label={`解除与${asset.name}的关联`} loading={removeRelation.isPending} onClick={() => confirmRemove(relation)} /></Tooltip>
                    </RelationActions>
                  </dd>
                </InfoRow>
              ))}
            </InfoGroup>
          </InfoBody>
        </InfoPanel>
      </Workspace>
      <AssetDocumentRelationModal
        subject={{ kind: 'document', id: document.id }}
        open={relationDialogOpen}
        saving={createRelation.isPending}
        onClose={() => setRelationDialogOpen(false)}
        onSubmit={(input) => createRelation.mutate(input)}
      />
    </Page>
  )
}
