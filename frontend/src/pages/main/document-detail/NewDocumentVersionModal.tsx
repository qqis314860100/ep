import { InboxOutlined } from '@ant-design/icons'
import { Alert, Form, Input, Modal, Upload } from 'antd'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { createDocumentVersion, uploadDocumentFile } from '../../../services/documentService'
import type { DocumentFile } from '../../../types/document'

interface NewDocumentVersionModalProps {
  open: boolean
  documentId: number
  currentVersionNumber: string
  onClose: () => void
  onCreated: () => Promise<void>
}

interface FormValues {
  versionNumber: string
  changeSummary: string
  files: DocumentFile[]
}

function nextVersionNumber(current: string): string {
  const match = /^V(\d+)(?:\.(\d+))?$/i.exec(current.trim())
  if (!match) return 'V2.0'
  const major = Number(match[1])
  const minor = match[2] ? Number(match[2]) : 0
  return `V${major}.${minor + 1}`
}

export function NewDocumentVersionModal({ open, documentId, currentVersionNumber, onClose, onCreated }: NewDocumentVersionModalProps) {
  const [form] = Form.useForm<FormValues>()
  const [uploadError, setUploadError] = useState('')
  const [uploading, setUploading] = useState(false)
  const createMutation = useMutation({
    mutationFn: (values: FormValues) => createDocumentVersion(documentId, {
      versionNumber: values.versionNumber.trim(),
      changeSummary: values.changeSummary.trim(),
      files: values.files,
    }),
    onSuccess: async () => {
      await onCreated()
      form.resetFields()
    },
  })

  const upload: NonNullable<Parameters<typeof Upload>[0]['customRequest']> = async (options) => {
    setUploading(true)
    setUploadError('')
    try {
      const uploaded = await uploadDocumentFile(options.file as File)
      const files = form.getFieldValue('files') ?? []
      form.setFieldValue('files', [...files, uploaded])
      options.onSuccess?.(uploaded)
    } catch (error) {
      setUploadError(error instanceof Error ? error.message : '文件上传失败')
      options.onError?.(new Error('文件上传失败'))
    } finally {
      setUploading(false)
    }
  }

  useEffect(() => {
    if (open) {
      form.resetFields()
      form.setFieldsValue({ versionNumber: nextVersionNumber(currentVersionNumber), changeSummary: '', files: [] })
      setUploadError('')
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  return (
    <Modal
      title="创建新版本"
      open={open}
      onOk={() => form.submit()}
      onCancel={onClose}
      okText="创建版本草稿"
      confirmLoading={createMutation.isPending || uploading}
      width={480}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={(values) => createMutation.mutate(values)}>
        {createMutation.error && <Alert type="error" showIcon message={createMutation.error.message} style={{ marginBottom: 12 }} />}
        <Form.Item name="versionNumber" label="版本号" rules={[{ required: true, message: '请填写版本号' }]}>
          <Input placeholder="例如 V2.0" />
        </Form.Item>
        <Form.Item name="changeSummary" label="变更说明" rules={[{ required: true, message: '请填写变更说明' }]}>
          <Input.TextArea rows={2} maxLength={200} placeholder="说明本次版本的变化内容" />
        </Form.Item>
        <Form.Item name="files" label="版本文件" rules={[{ required: true, message: '新版本至少需要一个文件' }]}>
          <Upload multiple disabled={uploading} fileList={[]} customRequest={upload} showUploadList={false}>
            <div style={{ padding: '14px 0', textAlign: 'center' }}>
              <InboxOutlined style={{ fontSize: 26, color: '#2f7567' }} />
              <div style={{ marginTop: 6 }}>点击或拖放文件上传</div>
              <div style={{ color: '#8b9590', fontSize: 11 }}>已上传 {form.getFieldValue('files')?.length ?? 0} 个文件</div>
            </div>
          </Upload>
        </Form.Item>
        {uploadError && <Alert type="error" showIcon message={uploadError} style={{ marginBottom: 12 }} />}
      </Form>
    </Modal>
  )
}
