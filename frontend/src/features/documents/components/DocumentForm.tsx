import { InboxOutlined } from '@ant-design/icons'
import { Alert, Form, Input, Select, Upload } from 'antd'
import type { FormInstance, UploadProps } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import { uploadDocumentFile } from '../../../services/documentService'
import type { DictionaryItem } from '../../../types/dictionary'
import type { DocumentFile } from '../../../types/document'
import { DocumentFileList } from './DocumentFileList'

const { Dragger } = Upload

const FormGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.75fr);
  gap: 12px;

  @media (max-width: 1080px) {
    grid-template-columns: minmax(0, 1fr);
  }
`

const Section = styled.section`
  min-width: 0;
  padding: 16px 18px 6px;
  background: #fff;
  border: 1px solid #dde4e0;
  border-radius: 5px;
`

const SectionTitle = styled.h2`
  margin: 0 0 14px;
  color: #304038;
  font-size: 14px;
  font-weight: 680;
`

const Pair = styled.div`
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
`

const UploadArea = styled.div`
  .ant-upload-wrapper .ant-upload-drag {
    min-height: 126px;
    background: #fafcfb;
    border-color: #cdd9d4;
    border-radius: 4px;
  }

  .ant-upload-wrapper .ant-upload-drag:hover {
    border-color: #6a9b8e;
  }

  .ant-upload-drag-icon { margin-bottom: 5px !important; }
  .ant-upload-drag-icon .anticon { color: #2f7567 !important; font-size: 28px !important; }
  .ant-upload-text { margin: 0 !important; color: #3d4c45 !important; font-size: 12px !important; }
  .ant-upload-hint { margin-top: 4px !important; color: #8b9691 !important; font-size: 10px !important; }
`

export interface DocumentFormValues {
  documentNumber: string
  title: string
  summary: string
  categoryCode: string
  maintainerId: string
  maintainerName: string
  maintainerDepartment: string
  versionNumber: string
  changeSummary: string
  files: DocumentFile[]
}

interface DocumentUploadProps {
  value?: DocumentFile[]
  onChange?: (files: DocumentFile[]) => void
  disabled?: boolean
}

function DocumentUpload({ value = [], onChange, disabled }: DocumentUploadProps) {
  const [uploadError, setUploadError] = useState('')
  const [uploading, setUploading] = useState(false)

  const upload: NonNullable<UploadProps['customRequest']> = async (options) => {
    setUploading(true)
    setUploadError('')
    try {
      const uploaded = await uploadDocumentFile(options.file as File)
      onChange?.([...value, uploaded])
      options.onSuccess?.(uploaded)
    } catch (error) {
      const message = error instanceof Error ? error.message : '文件上传失败'
      setUploadError(message)
      options.onError?.(new Error(message))
    } finally {
      setUploading(false)
    }
  }

  return (
    <UploadArea>
      {uploadError && <Alert type="error" showIcon message={uploadError} closable onClose={() => setUploadError('')} />}
      <Dragger
        multiple
        disabled={disabled || uploading}
        fileList={[]}
        customRequest={upload}
        showUploadList={false}
      >
        <p className="ant-upload-drag-icon"><InboxOutlined /></p>
        <p className="ant-upload-text">拖放文件到此处，或点击选择</p>
        <p className="ant-upload-hint">系统自动识别格式、大小、摘要和预览能力</p>
      </Dragger>
      <DocumentFileList
        files={value}
        disabled={disabled}
        onRemove={(index) => onChange?.(value.filter((_, current) => current !== index))}
      />
    </UploadArea>
  )
}

interface DocumentFormProps {
  form: FormInstance<DocumentFormValues>
  categories: DictionaryItem[]
  disabled?: boolean
  onChange: () => void
}

export function DocumentForm({ form, categories, disabled, onChange }: DocumentFormProps) {
  return (
    <Form<DocumentFormValues>
      form={form}
      layout="vertical"
      disabled={disabled}
      initialValues={{
        documentNumber: '',
        title: '',
        summary: '',
        categoryCode: undefined,
        maintainerId: 'demo-user',
        maintainerName: '陈工',
        maintainerDepartment: '',
        versionNumber: 'V1.0',
        changeSummary: '首次发布',
        files: [],
      }}
      onValuesChange={onChange}
    >
      <FormGrid>
        <Section>
          <SectionTitle>文档信息</SectionTitle>
          <Form.Item name="title" label="文档标题" rules={[{ required: true, message: '请输入文档标题' }]}>
            <Input maxLength={200} placeholder="输入可被准确检索的文档标题" />
          </Form.Item>
          <Pair>
            <Form.Item name="categoryCode" label="文档分类" rules={[{ required: true, message: '请选择文档分类' }]}>
              <Select
                placeholder="选择分类"
                options={categories.map((category) => ({ value: category.code, label: category.name }))}
              />
            </Form.Item>
            <Form.Item name="documentNumber" label="文档编号" extra="留空时由系统生成 DOC-六位编号">
              <Input maxLength={64} placeholder="可选" />
            </Form.Item>
          </Pair>
          <Form.Item name="summary" label="文档摘要" rules={[{ required: true, message: '请输入文档摘要' }]}>
            <Input.TextArea rows={3} maxLength={1000} showCount placeholder="说明用途和适用对象" />
          </Form.Item>
          <Pair>
            <Form.Item name="maintainerName" label="维护人" rules={[{ required: true, message: '请选择维护人' }]}>
              <Input maxLength={100} />
            </Form.Item>
            <Form.Item name="maintainerDepartment" label="所属部门">
              <Input maxLength={100} placeholder="系统可自动补充" />
            </Form.Item>
          </Pair>
          <Form.Item
            name="files"
            label="版本文件"
            rules={[{ type: 'array', min: 1, message: '请至少上传一个文件' }]}
          >
            <DocumentUpload disabled={disabled} />
          </Form.Item>
        </Section>
        <Section>
          <SectionTitle>首次版本</SectionTitle>
          <Form.Item name="versionNumber" label="版本号" rules={[{ required: true, message: '请输入版本号' }]}>
            <Input maxLength={40} />
          </Form.Item>
          <Form.Item name="changeSummary" label="变更说明" rules={[{ required: true, message: '请输入变更说明' }]}>
            <Input.TextArea rows={4} maxLength={1000} />
          </Form.Item>
        </Section>
      </FormGrid>
    </Form>
  )
}
