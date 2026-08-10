import { InboxOutlined } from '@ant-design/icons'
import { Alert, Button, Form, Input, Radio, Select, Space, Upload } from 'antd'
import type { FormInstance, UploadProps } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import { uploadDocumentFile } from '../../../services/documentService'
import type { DictionaryItem } from '../../../types/dictionary'
import type { DocumentFile, DocumentScope, DocumentScopeMode } from '../../../types/document'
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
  scopeMode: Exclude<DocumentScopeMode, 'UNCLASSIFIED'>
  scopes: DocumentScope[]
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
  scopeItems: DictionaryItem[]
  disabled?: boolean
  onChange: () => void
}

export function DocumentForm({ form, categories, scopeItems, disabled, onChange }: DocumentFormProps) {
  const scopeMode = Form.useWatch('scopeMode', form)
  const scopes = Form.useWatch('scopes', form) ?? []
  const enabledItems = scopeItems.filter((item) => item.status === 'ENABLED')
  const categoryItems = (category: string) => enabledItems
    .filter((item) => item.category === category)
    .sort((left, right) => left.sortOrder - right.sortOrder)
  const itemByName = (category: string, name?: string) => categoryItems(category).find((item) => item.name === name)
  const dependentItems = (category: string, parent?: DictionaryItem) => {
    const items = categoryItems(category)
    const hasHierarchy = items.some((item) => item.parentId !== undefined)
    if (!hasHierarchy) return items
    return parent ? items.filter((item) => item.parentId === parent.id) : []
  }
  const options = (items: DictionaryItem[]) => items.map((item) => ({ value: item.name, label: item.name }))
  const clearScopeFields = (index: number, names: Array<keyof DocumentScope>) => {
    names.forEach((name) => form.setFieldValue(['scopes', index, name], undefined))
  }
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
        scopeMode: 'GLOBAL',
        scopes: [],
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
          <Form.Item name="scopeMode" label="适用方式" rules={[{ required: true, message: '请选择文档适用方式' }]}>
            <Radio.Group>
              <Radio value="GLOBAL">全局通用</Radio>
              <Radio value="SPECIFIED">指定范围</Radio>
            </Radio.Group>
          </Form.Item>
          {scopeMode === 'SPECIFIED' && <Form.List name="scopes" rules={[{ validator: async (_, values) => {
            if (!values?.length) throw new Error('请至少添加一组适用范围')
          } }]}>
            {(fields, { add, remove }, { errors }) => <>
              {fields.map((field, index) => {
                const row = scopes[field.name] ?? {}
                const selectedPlatform = itemByName('PLATFORM_FAMILY', row.platformFamily)
                const selectedVariant = itemByName('PLATFORM_VARIANT', row.platformVariant)
                const selectedBase = itemByName('BASE', row.baseName)
                const selectedProductionLine = itemByName('PRODUCTION_LINE', row.productionLine)
                return <Section key={field.key} style={{ marginBottom: 10, padding: 12 }}>
                  <Space style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}><span>适用范围 {index + 1}</span><Button type="link" danger size="small" onClick={() => remove(field.name)}>移除</Button></Space>
                  <Pair>
                    <Form.Item {...field} name={[field.name, 'platformFamily']} label="平台族" rules={[{ required: true, message: '请选择平台族' }]}><Select options={options(categoryItems('PLATFORM_FAMILY'))} onChange={() => clearScopeFields(field.name, ['platformVariant', 'productLine'])} /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'platformVariant']} label="平台子类"><Select allowClear disabled={!selectedPlatform && dependentItems('PLATFORM_VARIANT').some((item) => item.parentId !== undefined)} options={options(dependentItems('PLATFORM_VARIANT', selectedPlatform))} onChange={() => clearScopeFields(field.name, ['productLine'])} /></Form.Item>
                  </Pair>
                  <Pair>
                    <Form.Item {...field} name={[field.name, 'productLine']} label="蓝本" rules={[{ required: true, message: '请选择蓝本' }]}><Select disabled={!selectedVariant && dependentItems('PRODUCT_LINE').some((item) => item.parentId !== undefined)} options={options(dependentItems('PRODUCT_LINE', selectedVariant))} /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'baseName']} label="基地" rules={[{ required: true, message: '请选择基地' }]}><Select options={options(categoryItems('BASE'))} onChange={() => clearScopeFields(field.name, ['productionLine', 'processSection'])} /></Form.Item>
                  </Pair>
                  <Pair>
                    <Form.Item {...field} name={[field.name, 'productionLine']} label="拉线" rules={[{ required: true, message: '请选择拉线' }]}><Select disabled={!selectedBase && dependentItems('PRODUCTION_LINE').some((item) => item.parentId !== undefined)} options={options(dependentItems('PRODUCTION_LINE', selectedBase))} onChange={() => clearScopeFields(field.name, ['processSection'])} /></Form.Item>
                    <Form.Item {...field} name={[field.name, 'processSection']} label="工序段"><Select allowClear disabled={!selectedProductionLine && dependentItems('PROCESS_SECTION').some((item) => item.parentId !== undefined)} options={options(dependentItems('PROCESS_SECTION', selectedProductionLine))} /></Form.Item>
                  </Pair>
                </Section>
              })}
              <Button onClick={() => add({ platformFamily: '', platformVariant: '', productLine: '', baseName: '', productionLine: '', processSection: '' })}>添加适用范围</Button>
              <Form.ErrorList errors={errors} />
            </>}
          </Form.List>}
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
