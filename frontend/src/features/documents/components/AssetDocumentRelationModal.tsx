import { SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Empty, Input, Modal, Radio, Skeleton, Tag } from 'antd'
import { useEffect, useState } from 'react'
import styled from 'styled-components'
import { assetDocumentRelationLabels } from '../assetDocumentRelationPresentation'
import { searchAssets } from '../../../services/assetService'
import { searchDocuments } from '../../../services/documentService'
import type { Asset } from '../../../types/asset'
import type { AssetDocumentRelationType, KnowledgeDocument } from '../../../types/document'

const SearchBar = styled.div`
  margin-bottom: 10px;
`

const CandidateList = styled.div`
  min-height: 174px;
  max-height: 330px;
  overflow: auto;
  border: 1px solid #e1e7e4;
  border-radius: 4px;
`

const Candidate = styled.button<{ $selected: boolean }>`
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 58px;
  padding: 9px 11px;
  color: #3a4c44;
  text-align: left;
  background: ${({ $selected }) => ($selected ? '#eaf3f0' : '#fff')};
  border: 0;
  border-bottom: 1px solid #edf0ee;
  cursor: pointer;

  &:last-child { border-bottom: 0; }
  &:hover, &:focus-visible { background: #f2f7f4; outline: none; }
`

const CandidateTitle = styled.div`
  overflow: hidden;
  color: #304139;
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const CandidateMeta = styled.div`
  margin-top: 4px;
  overflow: hidden;
  color: #7d8983;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const RelationType = styled.div`
  margin-top: 13px;

  .ant-radio-wrapper { font-size: 12px; }
`

type Subject =
  | { kind: 'document'; id: number }
  | { kind: 'asset'; id: number }

interface AssetDocumentRelationModalProps {
  subject: Subject
  open: boolean
  saving?: boolean
  onClose: () => void
  onSubmit: (input: { assetId: number; documentId: number; relationType: AssetDocumentRelationType }) => void
}

function assetMeta(asset: Asset) {
  const scope = asset.scopes[0]
  const scopeText = scope ? [scope.platformFamily ?? scope.platform, scope.productLine, scope.base, scope.productionLine]
    .filter(Boolean).join(' / ') : '范围待补充'
  const status = asset.status === 'STANDARDIZED' ? '已标准化' : asset.status === 'PENDING_CURATION' ? '待整理' : asset.status === 'DISABLED' ? '已停用' : '草稿'
  return `${asset.assetNumber} · ${status} · ${scopeText}`
}

function documentMeta(document: KnowledgeDocument) {
  return `${document.documentNumber} · 当前版本 ${document.currentVersion.versionNumber} · ${document.maintainerName}`
}

export function AssetDocumentRelationModal({ subject, open, saving, onClose, onSubmit }: AssetDocumentRelationModalProps) {
  const [query, setQuery] = useState('')
  const [selectedId, setSelectedId] = useState<number>()
  const [relationType, setRelationType] = useState<AssetDocumentRelationType>('COMPANION')
  const candidateQuery = useQuery({
    queryKey: ['asset-document-relation-candidates', subject.kind, query],
    enabled: open,
    queryFn: async () => {
      if (subject.kind === 'document') {
        const page = await searchAssets({ query, page: 1, perPage: 20, previewable: false })
        return page.data
      }
      const page = await searchDocuments({ query, category: '', page: 1, perPage: 20 })
      return page.data
    },
  })
  const candidates = candidateQuery.data ?? []
  const selected = candidates.find((item) => item.id === selectedId)

  useEffect(() => {
    if (!open) return
    setQuery('')
    setSelectedId(undefined)
    setRelationType('COMPANION')
  }, [open, subject.id, subject.kind])

  const targetName = subject.kind === 'document' ? '资产' : '文档'
  const submit = () => {
    if (!selectedId) return
    onSubmit(subject.kind === 'document'
      ? { assetId: selectedId, documentId: subject.id, relationType }
      : { assetId: subject.id, documentId: selectedId, relationType })
  }

  return (
    <Modal
      title={`关联${targetName}`}
      open={open}
      width={620}
      destroyOnHidden
      okText="保存关联"
      cancelText="取消"
      okButtonProps={{ disabled: !selectedId, loading: saving }}
      onOk={submit}
      onCancel={onClose}
    >
      <SearchBar>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder={subject.kind === 'document' ? '搜索资产编号、名称或功能说明' : '搜索文档编号、标题或关键词'}
          value={query}
          onChange={(event) => { setQuery(event.target.value); setSelectedId(undefined) }}
        />
      </SearchBar>
      <CandidateList aria-label={`可关联${targetName}`}>
        {candidateQuery.isLoading ? <Skeleton active paragraph={{ rows: 3 }} style={{ padding: 12 }} />
          : candidateQuery.isError ? <Empty description={`${targetName}加载失败`} style={{ margin: 24 }}><Button onClick={() => candidateQuery.refetch()}>重试</Button></Empty>
            : candidates.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={`未找到可关联${targetName}`} style={{ margin: 24 }} />
              : candidates.map((candidate) => {
                const isAsset = subject.kind === 'document'
                const title = isAsset ? (candidate as Asset).name : (candidate as KnowledgeDocument).title
                const meta = isAsset ? assetMeta(candidate as Asset) : documentMeta(candidate as KnowledgeDocument)
                return <Candidate key={candidate.id} type="button" $selected={candidate.id === selectedId} onClick={() => setSelectedId(candidate.id)}>
                  <div><CandidateTitle>{title}</CandidateTitle><CandidateMeta>{meta}</CandidateMeta></div>
                  {candidate.id === selectedId && <Tag color="green">已选择</Tag>}
                </Candidate>
              })}
      </CandidateList>
      <RelationType>
        <Radio.Group value={relationType} onChange={(event) => setRelationType(event.target.value)}>
          {(Object.keys(assetDocumentRelationLabels) as AssetDocumentRelationType[]).map((type) => <Radio key={type} value={type}>{assetDocumentRelationLabels[type]}</Radio>)}
        </Radio.Group>
      </RelationType>
      {selected && <CandidateMeta>将关联：{subject.kind === 'document' ? (selected as Asset).name : (selected as KnowledgeDocument).title}</CandidateMeta>}
    </Modal>
  )
}
