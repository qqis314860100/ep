import { ArrowLeftOutlined, CheckOutlined, SaveOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Descriptions, Form, Modal, Tag } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getDictionaryItems } from '../../services/dictionaryService'
import { createDocumentDraft, publishDocument } from '../../services/documentService'
import type { CreateDocumentDraftInput, KnowledgeDocument } from '../../types/document'
import { DocumentForm, type DocumentFormValues } from './components/DocumentForm'

const Page = styled.div`
  width: min(1180px, 100%);
  margin: 0 auto;
`

const PageBar = styled.header`
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding-bottom: 10px;
`

const BackButton = styled(Button)`
  flex: 0 0 auto;
`

const Heading = styled.h1`
  margin: 0;
  color: #26352f;
  font-size: 18px;
  font-weight: 680;
`

const Meta = styled.div`
  margin-top: 2px;
  color: #7f8a85;
  font-size: 11px;
`

const Spacer = styled.div`
  flex: 1;
`

const ActionBar = styled.div`
  position: sticky;
  bottom: 0;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  min-height: 58px;
  margin-top: 12px;
  padding: 9px 12px;
  background: rgba(244, 246, 245, 0.96);
  border-top: 1px solid #d9e1dd;
  backdrop-filter: blur(8px);
`

const DraftState = styled.div`
  margin-right: auto;
  color: #56645e;
  font-size: 11px;
`

function toInput(values: DocumentFormValues): CreateDocumentDraftInput {
  return {
    ...values,
    maintainerId: values.maintainerId || 'demo-user',
    scopes: values.scopeMode === 'GLOBAL' ? [] : values.scopes,
  }
}

export default function DocumentCreatePage() {
  const navigate = useNavigate()
  const [form] = Form.useForm<DocumentFormValues>()
  const [dirty, setDirty] = useState(false)
  const [savedDraft, setSavedDraft] = useState<KnowledgeDocument>()
  const [confirmValues, setConfirmValues] = useState<DocumentFormValues>()
  const [saving, setSaving] = useState(false)
  const [publishing, setPublishing] = useState(false)
  const [error, setError] = useState('')
  const categoryQuery = useQuery({ queryKey: ['document-categories'], queryFn: getDictionaryItems })
  const categories = useMemo(() => (categoryQuery.data ?? [])
    .filter((item) => item.category === 'DOCUMENT_CATEGORY' && item.status === 'ENABLED')
    .sort((left, right) => left.sortOrder - right.sortOrder), [categoryQuery.data])

  useEffect(() => {
    const guard = (event: BeforeUnloadEvent) => {
      if (!dirty) return
      event.preventDefault()
    }
    window.addEventListener('beforeunload', guard)
    return () => window.removeEventListener('beforeunload', guard)
  }, [dirty])

  const leave = () => {
    if (!dirty || window.confirm('当前内容尚未保存，确认离开吗？')) navigate('/documents')
  }

  const saveDraft = async () => {
    setError('')
    let values: DocumentFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setSaving(true)
    try {
      const saved = await createDocumentDraft(toInput(values))
      setSavedDraft(saved)
      setDirty(false)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '草稿保存失败')
    } finally {
      setSaving(false)
    }
  }

  const preparePublish = async () => {
    setError('')
    let values: DocumentFormValues
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    setConfirmValues(values)
  }

  const confirmPublish = async () => {
    if (!confirmValues) return
    setPublishing(true)
    setError('')
    try {
      const draft = savedDraft ?? await createDocumentDraft(toInput(confirmValues))
      const published = await publishDocument(draft.id)
      setDirty(false)
      setConfirmValues(undefined)
      navigate(`/documents/${published.id}`)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : '文档发布失败')
      setConfirmValues(undefined)
    } finally {
      setPublishing(false)
    }
  }

  return (
    <Page>
      <PageBar>
        <BackButton type="text" icon={<ArrowLeftOutlined />} aria-label="返回文档中心" onClick={leave} />
        <div>
          <Heading>新建文档</Heading>
          <Meta>录入最少必要信息并上传首个版本文件</Meta>
        </div>
        <Spacer />
        {savedDraft && <Tag color="gold">草稿 {savedDraft.documentNumber}</Tag>}
      </PageBar>
      {error && <Alert type="error" showIcon message={error} closable onClose={() => setError('')} style={{ marginBottom: 10 }} />}
      <DocumentForm form={form} categories={categories} scopeItems={categoryQuery.data ?? []} disabled={Boolean(savedDraft)} onChange={() => setDirty(true)} />
      <ActionBar>
        <DraftState>{savedDraft ? '草稿已保存' : '尚未保存'}</DraftState>
        <Button onClick={leave}>取消</Button>
        <Button
          icon={<SaveOutlined />}
          aria-label="保存草稿"
          disabled={Boolean(savedDraft)}
          loading={saving}
          onClick={saveDraft}
        >保存草稿</Button>
        <Button
          type="primary"
          icon={<CheckOutlined />}
          aria-label="发布文档"
          loading={publishing}
          onClick={preparePublish}
        >发布文档</Button>
      </ActionBar>
      <Modal
        title="确认首次发布"
        open={Boolean(confirmValues)}
        okText="确认发布"
        cancelText="返回检查"
        okButtonProps={{ 'aria-label': '确认发布' }}
        confirmLoading={publishing}
        onOk={confirmPublish}
        onCancel={() => setConfirmValues(undefined)}
      >
        {confirmValues && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="文档编号">{confirmValues.documentNumber || '系统自动生成'}</Descriptions.Item>
            <Descriptions.Item label="版本号">{confirmValues.versionNumber}</Descriptions.Item>
            <Descriptions.Item label="文件清单">{confirmValues.files.map((file) => file.name).join('、')}</Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </Page>
  )
}
