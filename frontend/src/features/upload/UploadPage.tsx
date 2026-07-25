import {
  CheckCircleOutlined,
  CloudUploadOutlined,
  InboxOutlined,
  LinkOutlined,
  LoadingOutlined,
  ReloadOutlined,
  SettingOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  App as AntdApp,
  Alert,
  Button,
  Form,
  Input,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Steps,
  Typography,
  Upload,
} from 'antd'
import type { UploadProps } from 'antd'
import type { UploadChangeParam, UploadFile } from 'antd/es/upload/interface'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import styled from 'styled-components'
import { saveAssetDraft, submitAsset, uploadAssetFile } from '../../services/assetService'
import { getDictionaryItems } from '../../services/dictionaryService'
import type { AssetDraftInput, AssetFile, AssetType } from '../../types/asset'

const { Dragger } = Upload
const emptyUploadFiles: UploadFile[] = []

const UploadFrame = styled.div`
  height: 100%;
  min-height: 0;

  > .ant-form {
    display: flex;
    flex-direction: column;
    height: 100%;
    min-height: 0;
  }
`

const UploadScrollArea = styled.div`
  container-type: size;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 10px;
  align-items: start;
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  padding: 0 4px 0 0;
  scrollbar-gutter: stable;

  @media (max-width: 980px) {
    grid-template-columns: 1fr;
  }
`

type FileStage = '待上传' | '上传中' | '校验中' | '可用' | '失败'
type UploadFormValues = {
  assetNumber: string
  name: string
  description: string
  assetType: AssetType
  platform: string
  platformVariant?: string
  productLine: string
  base: string
  productionLine: string
  processSection?: string
  specialties: string[]
  moduleTags?: string[]
  standardEquipmentModule?: boolean
  linkedModuleAssetIds?: string
  equipmentInterconnectCode?: string
  files: UploadFile[]
}

const Header = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 38px;
  margin-bottom: 8px;

  @media (max-width: 760px) {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #202824;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0;
`

const StepNote = styled.div`
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  color: #53625b;
  font-size: 12px;
  background: #edf5f1;
  border: 1px solid #d6e7df;
  border-radius: 5px;
`

const IntakeSteps = styled(Steps)`
  margin: 0 0 8px;
  padding: 6px 12px;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;

  .ant-steps-item-title {
    font-size: 13px;
  }

  .ant-steps-item-description {
    color: #849089;
    font-size: 12px;
  }
`

const Section = styled.section`
  min-width: 0;
  padding: 10px;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const MetadataPanel = styled(Section)`
  padding: 12px 14px 14px;

  .ant-form-item {
    margin-bottom: 12px;
  }

  .ant-form-item-label {
    padding-bottom: 4px;
  }
`

const UploadPanel = styled(Section)`
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  align-self: start;
  height: 100cqh;
  max-height: 100cqh;
  min-height: 0;
  padding: 10px;

  > .ant-form-item {
    flex: 0 0 auto;
    margin-bottom: 8px;
  }

  > .ant-alert {
    flex: 0 0 auto;
    padding: 6px 8px;
  }

  > .ant-btn-block {
    flex: 0 0 auto;
  }

  @media (max-width: 980px) {
    position: static;
    height: auto;
    max-height: none;
    min-height: 0;
  }
`

const SectionHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
`

const SectionTitle = styled.div`
  color: #20332c;
  font-size: 15px;
  font-weight: 650;
`

const SectionHint = styled.div`
  margin-top: 3px;
  color: #849089;
  font-size: 12px;
  line-height: 18px;
`

const FormSection = styled.div`
  padding-top: 10px;
  border-top: 1px solid #e7ece9;

  & + & {
    margin-top: 6px;
  }
`

const FormSectionTitle = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #20332c;
  font-size: 14px;
  font-weight: 650;
`

const ScopeGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 10px;
`

const FilesTable = styled.div`
  flex: 1 1 auto;
  min-height: 0;
  margin: 8px -6px;
  overflow: hidden;

  .ant-table {
    font-size: 12px;
  }

  .ant-table-thead > tr > th,
  .ant-table-tbody > tr > td {
    padding: 8px 6px;
  }
`

const Dropzone = styled(Dragger)`
  && .ant-upload-drag {
    height: 88px;
    min-height: 88px;
    border-color: #bcd8cd;
    background: #f6faf8;
  }

  && .ant-upload-drag .ant-upload-btn {
    height: 86px;
    padding: 6px 12px;
  }

  .ant-upload-drag:hover {
    border-color: #2f7567;
  }

  &&& .ant-upload-drag p.ant-upload-drag-icon {
    margin: 0 0 2px;
    line-height: 1;
  }

  &&& .ant-upload-drag p.ant-upload-drag-icon .anticon {
    color: #2f7567;
    font-size: 24px;
  }

  &&& .ant-upload-drag p.ant-upload-text {
    margin: 0 0 1px;
    font-size: 13px;
    line-height: 17px;
  }

  &&& .ant-upload-drag p.ant-upload-hint {
    margin: 0;
    font-size: 11px;
    line-height: 15px;
  }
`

const QueueSummary = styled.div`
  display: flex;
  flex: 0 0 auto;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #e7ece9;
`

const QueueLabel = styled.div`
  color: #20332c;
  font-size: 13px;
  font-weight: 650;
`

const QueueHint = styled.div`
  margin-top: 3px;
  color: #849089;
  font-size: 11px;
`

const ActionBar = styled.div`
  position: relative;
  z-index: 4;
  display: flex;
  flex: 0 0 auto;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 0;
  padding: 8px 0 0;
  background: #f4f6f5;
  border-top: 1px solid #dfe7e2;
  box-shadow: 0 -7px 14px rgba(34, 53, 45, 0.06);
`

const fallbackAssetTypeOptions = [
  ['THREE_DIMENSIONAL_MODEL', '三维模型'],
  ['TWO_DIMENSIONAL_DRAWING', '二维图纸'],
  ['MIXED_ASSET', '混合资产'],
  ['OTHER', '其他资料'],
] as const

const fallbackRoleOptions = ['三维源模型', '二维图纸', '预览文件', '说明附件', '其他附件']
const fallbackSpecialtyOptions = ['机械', '电气', '液压', '气动', '工装']
const recognizedFormats = new Set(['X_T', 'STEP', 'STP', 'IGES', 'IGS', 'DWG', 'DXF', 'PDF', 'PNG', 'JPG', 'JPEG', 'TIFF', 'WEBP', 'DOC', 'DOCX', 'ZIP', 'RAR'])
function fileFormat(name: string) {
  const extension = name.split('.').pop()
  return extension ? extension.toUpperCase() : 'OTHER'
}

function defaultRole(name: string) {
  const format = fileFormat(name)
  if (['PNG', 'JPG', 'JPEG', 'TIFF', 'WEBP'].includes(format)) return '预览文件'
  if (format === 'PDF' || format === 'DWG' || format === 'DXF') return '二维图纸'
  if (['X_T', 'STEP', 'STP'].includes(format)) return '三维源模型'
  return '其他附件'
}

function formatLabel(name: string) {
  const format = fileFormat(name)
  return recognizedFormats.has(format) ? format : '未识别'
}

function isRecognizedFormat(name: string) {
  return recognizedFormats.has(fileFormat(name))
}

function normalizeFiles(event: { fileList: UploadFile[] } | UploadFile[]) {
  return Array.isArray(event) ? event : event?.fileList ?? []
}

function stageTag(stage: FileStage) {
  const props: Record<FileStage, { color: string; icon: React.ReactNode }> = {
    待上传: { color: 'default', icon: <CloudUploadOutlined /> },
    上传中: { color: 'processing', icon: <LoadingOutlined spin /> },
    校验中: { color: 'processing', icon: <LoadingOutlined spin /> },
    可用: { color: 'success', icon: <CheckCircleOutlined /> },
    失败: { color: 'error', icon: <WarningOutlined /> },
  }
  const value = props[stage]
  return <Tag icon={value.icon} color={value.color}>{stage}</Tag>
}

export function UploadPage() {
  const { message } = AntdApp.useApp()
  const [form] = Form.useForm<UploadFormValues>()
  const fileList = Form.useWatch('files', form) ?? emptyUploadFiles
  const platform = Form.useWatch('platform', form)
  const platformVariant = Form.useWatch('platformVariant', form)
  const base = Form.useWatch('base', form)
  const productionLine = Form.useWatch('productionLine', form)
  const [roles, setRoles] = useState<Record<string, string>>({})
  const [groups, setGroups] = useState<Record<string, string>>({})
  const [fileStages, setFileStages] = useState<Record<string, FileStage>>({})
  const [batchRole, setBatchRole] = useState('')
  const [draftId, setDraftId] = useState<number>()
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)

  const dictionaryQuery = useQuery({ queryKey: ['dictionary-items'], queryFn: getDictionaryItems })
  const dictionaryItems = (dictionaryQuery.data ?? []).filter((item) => item.status === 'ENABLED')
  const platformFamilies = dictionaryItems.filter((item) => item.category === 'PLATFORM_FAMILY')
  const selectedPlatform = platformFamilies.find((item) => item.name === platform)
  const platformVariants = dictionaryItems.filter((item) => item.category === 'PLATFORM_VARIANT' && item.parentId === selectedPlatform?.id)
  const selectedVariant = platformVariants.find((item) => item.name === platformVariant)
  const blueprints = dictionaryItems.filter((item) => item.category === 'PRODUCT_LINE' && item.parentId === selectedVariant?.id)
  const bases = dictionaryItems.filter((item) => item.category === 'BASE')
  const selectedBase = bases.find((item) => item.name === base)
  const productionLines = dictionaryItems.filter((item) => item.category === 'PRODUCTION_LINE' && item.parentId === selectedBase?.id)
  const selectedProductionLine = productionLines.find((item) => item.name === productionLine)
  const processSections = dictionaryItems.filter((item) => item.category === 'PROCESS_SECTION' && item.parentId === selectedProductionLine?.id)
  const assetTypeOptions = dictionaryQuery.data
    ? dictionaryItems.filter((item) => item.category === 'ASSET_TYPE').map((item) => [item.code, item.name] as const)
    : fallbackAssetTypeOptions
  const roleOptions = dictionaryQuery.data
    ? dictionaryItems.filter((item) => item.category === 'FILE_ROLE').map((item) => item.name)
    : fallbackRoleOptions
  const specialtyOptions = dictionaryQuery.data
    ? dictionaryItems.filter((item) => item.category === 'SPECIALTY').map((item) => item.name)
    : fallbackSpecialtyOptions
  const moduleTagOptions = dictionaryItems.filter((item) => item.category === 'MODULE_TAG').map((item) => ({ value: item.name, label: item.name }))
  const formatSummary = useMemo(() => {
    const formats = [...new Set(fileList.map((file) => formatLabel(file.name)))]
    const unknownCount = fileList.filter((file) => !isRecognizedFormat(file.name)).length
    return { formats, unknownCount }
  }, [fileList])
  const groupOptions = useMemo(
    () => [...new Set(Object.values(groups))].map((value) => ({ value, label: value })),
    [groups],
  )

  const handleFilesChange = (info: UploadChangeParam<UploadFile>) => {
    const nextFiles: UploadFile[] = info.fileList
    setRoles((current) => Object.fromEntries(nextFiles.map((file) => [file.uid, current[file.uid] ?? defaultRole(file.name)])))
    setGroups((current) => Object.fromEntries(nextFiles.map((file, index) => [file.uid, current[file.uid] ?? `资产组 ${Math.floor(index / 2) + 1}`])))
    setFileStages((current) => Object.fromEntries(nextFiles.map((file) => [file.uid, current[file.uid] ?? '待上传'])))
  }

  const uploadProps: UploadProps = {
    multiple: true,
    directory: true,
    beforeUpload: () => false,
    onChange: handleFilesChange,
    showUploadList: false,
    accept: '.x_t,.step,.stp,.iges,.igs,.dwg,.dxf,.pdf,.png,.jpg,.jpeg,.tiff,.webp,.doc,.docx,.zip,.rar',
  }

  const valuesToInput = (values: UploadFormValues, uploadedFiles: AssetFile[]): AssetDraftInput => ({
    assetNumber: values.assetNumber?.trim() ?? '',
    name: values.name?.trim() ?? '',
    description: values.description?.trim() ?? '',
    assetType: values.assetType,
    specialties: values.specialties ?? [],
    tags: [],
    moduleTags: values.moduleTags ?? [],
    standardEquipmentModule: values.standardEquipmentModule ?? false,
    linkedModuleAssetIds: (values.linkedModuleAssetIds ?? '')
      .split(/[,，\s]+/)
      .filter(Boolean)
      .map(Number)
      .filter((id) => Number.isFinite(id)),
    equipmentInterconnectCode: values.equipmentInterconnectCode?.trim() ?? '',
    scopes: values.platform || values.productLine || values.base || values.productionLine
      ? [{
        platform: values.platform ?? '',
        productLine: values.productLine ?? '',
        base: values.base ?? '',
        productionLine: values.productionLine ?? '',
        processSection: values.processSection ?? '',
        platformFamily: values.platform ?? '',
        platformVariant: values.platformVariant ?? '',
      }]
      : [],
    files: uploadedFiles.map((file, index) => ({
      ...file,
      role: roles[fileList[index]?.uid] ?? file.role,
      primary: index === 0,
    })),
    ownerName: '陈工',
    ownerDepartment: '设备工程部',
  })

  const uploadSelectedFiles = async () => {
    setUploading(true)
    try {
      return await Promise.all(fileList.map(async (file) => {
        if (!file.originFileObj) throw new Error(`文件不可读取：${file.name}`)
        setFileStages((current) => ({ ...current, [file.uid]: '上传中' }))
        try {
          const uploaded = await uploadAssetFile(file.originFileObj)
          setFileStages((current) => ({ ...current, [file.uid]: '校验中' }))
          await new Promise((resolve) => window.setTimeout(resolve, 180))
          setFileStages((current) => ({ ...current, [file.uid]: '可用' }))
          return uploaded
        } catch (error) {
          setFileStages((current) => ({ ...current, [file.uid]: '失败' }))
          throw error
        }
      }))
    } finally {
      setUploading(false)
    }
  }

  const saveDraft = async () => {
    setSaving(true)
    try {
      const uploadedFiles = await uploadSelectedFiles()
      const asset = await saveAssetDraft(valuesToInput(form.getFieldsValue(), uploadedFiles))
      setDraftId(asset.id)
      message.success(`草稿已保存：${asset.assetNumber || asset.name || asset.id}`)
    } catch (error) {
      message.error(error instanceof Error ? error.message : '草稿保存失败')
    } finally {
      setSaving(false)
    }
  }

  const submit = async (values: UploadFormValues) => {
    setSaving(true)
    try {
      const asset = draftId
        ? { id: draftId }
        : await saveAssetDraft(valuesToInput(values, await uploadSelectedFiles()))
      const submitted = await submitAsset(asset.id)
      setDraftId(submitted.id)
      message.success('已提交，资产进入待整理状态')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败')
    } finally {
      setSaving(false)
    }
  }

  const autoGroup = () => {
    const nextGroups: Record<string, string> = {}
    const names = [...new Set(fileList.map((file) => file.name.split('.')[0]))]
    fileList.forEach((file) => {
      const prefix = file.name.split('.')[0]
      nextGroups[file.uid] = `资产组 ${names.indexOf(prefix) + 1}`
    })
    setGroups(nextGroups)
  }

  const applyBatchRole = () => {
    if (!batchRole) return
    setRoles(Object.fromEntries(fileList.map((file) => [file.uid, batchRole])))
  }

  const columns: ColumnsType<UploadFile> = [
    {
      title: '文件名 / 后缀',
      width: 126,
      render: (_, record) => (
        <div>
          <Typography.Text ellipsis={{ tooltip: record.name }} style={{ display: 'block', maxWidth: 120 }}>
            {record.name}
          </Typography.Text>
          <Tag color={isRecognizedFormat(record.name) ? 'blue' : 'warning'} style={{ marginTop: 3, marginInlineEnd: 0 }}>
            {formatLabel(record.name)}
          </Tag>
        </div>
      ),
    },
    {
      title: '归组',
      width: 76,
      render: (_, record) => (
        <Select
          value={groups[record.uid]}
          onChange={(value) => setGroups((current) => ({ ...current, [record.uid]: value }))}
          options={groupOptions}
          style={{ width: 68 }}
        />
      ),
    },
    {
      title: '文件角色',
      width: 94,
      render: (_, record) => (
        <Select
          value={roles[record.uid]}
          onChange={(value) => setRoles((current) => ({ ...current, [record.uid]: value }))}
          style={{ width: 88 }}
          options={roleOptions.map((value) => ({ value, label: value }))}
        />
      ),
    },
    { title: '处理状态', width: 86, render: (_, record) => stageTag(fileStages[record.uid] ?? '待上传') },
  ]

  return (
    <UploadFrame>
      <Form<UploadFormValues>
        form={form}
        layout="vertical"
        initialValues={{ assetType: 'MIXED_ASSET', specialties: [], standardEquipmentModule: false, files: [] }}
        onFinish={(values) => void submit(values)}
        requiredMark="optional"
      >
        <Header>
          <div>
            <Title>资料入库</Title>
            <Typography.Text type="secondary">将已确定的产线图纸、数模和预览资料沉淀为可检索资产。</Typography.Text>
          </div>
          <StepNote><CheckCircleOutlined />草稿可随时保存，提交前只需补齐带 * 的字段</StepNote>
        </Header>

        <IntakeSteps
          size="small"
          current={draftId ? 2 : fileList.length > 0 ? 1 : 0}
          items={[
            { title: '选择文件', description: fileList.length > 0 ? `${fileList.length} 个待入库文件` : '支持文件夹批量选择' },
            { title: '补充信息', description: '编号、范围和专业' },
            { title: '提交整理', description: draftId ? `草稿 #${draftId}` : '提交后进入待整理' },
          ]}
        />

        <UploadScrollArea>
        <MetadataPanel>
          <SectionHeader>
            <div><SectionTitle>资产元数据</SectionTitle><SectionHint>先填写工程师最熟悉的信息，后台整理人员可继续补充字典和关联关系。</SectionHint></div>
            <Tag color="gold">带 * 为提交必填</Tag>
          </SectionHeader>

          <FormSection>
            <FormSectionTitle>基础信息 <Tag>必填</Tag></FormSectionTitle>
            <ScopeGrid>
              <Form.Item name="assetNumber" label="资料编号" rules={[{ required: true, whitespace: true, message: '请输入资料编号' }]}>
                <Input placeholder="例如：MOD-ASSY-001" />
              </Form.Item>
              <Form.Item name="name" label="资料名称" rules={[{ required: true, whitespace: true, message: '请输入资料名称' }]}>
                <Input placeholder="填写工程师熟悉的名称" />
              </Form.Item>
            </ScopeGrid>
            <Form.Item name="description" label="功能说明" rules={[{ required: true, whitespace: true, message: '请说明图纸或数模的用途' }]}>
              <Input.TextArea placeholder="一句话说明该资产用于哪个设备或工序" autoSize={{ minRows: 2, maxRows: 3 }} />
            </Form.Item>
            <ScopeGrid>
              <Form.Item name="assetType" label="资产类型" rules={[{ required: true, message: '请选择资产类型' }]}>
                <Select options={assetTypeOptions.map(([value, label]) => ({ value, label }))} />
              </Form.Item>
              <Form.Item name="specialties" label="专业类别" rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一个专业' }]}>
                <Select mode="multiple" placeholder="机械、电气、工装..." options={specialtyOptions.map((value) => ({ value, label: value }))} />
              </Form.Item>
            </ScopeGrid>
          </FormSection>

          <FormSection>
            <FormSectionTitle>适用范围 <Tag>必填</Tag></FormSectionTitle>
            <ScopeGrid>
              <Form.Item name="platform" label="覆盖平台" rules={[{ required: true, message: '请选择覆盖平台' }]}>
                <Select
                  placeholder="八大平台"
                  options={platformFamilies.map((item) => ({ value: item.name, label: item.name }))}
                  onChange={() => form.setFieldsValue({ platformVariant: undefined, productLine: undefined })}
                />
              </Form.Item>
              <Form.Item name="platformVariant" label="平台子类" rules={[{ required: true, message: '请选择平台子类' }]}>
                <Select disabled={!selectedPlatform} placeholder="请选择子类" options={platformVariants.map((item) => ({ value: item.name, label: item.name }))} onChange={() => form.setFieldValue('productLine', undefined)} />
              </Form.Item>
              <Form.Item name="productLine" label="蓝本" rules={[{ required: true, message: '请选择蓝本' }]}>
                <Select disabled={!selectedVariant} placeholder="选择蓝本" options={blueprints.map((item) => ({ value: item.name, label: item.name }))} />
              </Form.Item>
              <Form.Item name="base" label="基地" rules={[{ required: true, message: '请选择基地' }]}>
                <Select placeholder="选择基地" options={bases.map((item) => ({ value: item.name, label: item.name }))} onChange={() => form.setFieldsValue({ productionLine: undefined, processSection: undefined })} />
              </Form.Item>
              <Form.Item name="productionLine" label="拉线" rules={[{ required: true, message: '请选择拉线' }]}>
                <Select disabled={!selectedBase} placeholder="选择拉线" options={productionLines.map((item) => ({ value: item.name, label: item.name }))} onChange={() => form.setFieldValue('processSection', undefined)} />
              </Form.Item>
              <Form.Item name="processSection" label="工序段"><Select allowClear disabled={!selectedProductionLine} placeholder="选择工序段" options={processSections.map((item) => ({ value: item.name, label: item.name }))} /></Form.Item>
            </ScopeGrid>
          </FormSection>

          <FormSection>
            <FormSectionTitle>模块化关联 <Tag color="blue">可选</Tag></FormSectionTitle>
            <Form.Item name="standardEquipmentModule" label="标准设备模块" valuePropName="checked">
              <Switch checkedChildren="是" unCheckedChildren="否" />
            </Form.Item>
            <Form.Item noStyle shouldUpdate={(previous, current) => previous.platform !== current.platform || previous.standardEquipmentModule !== current.standardEquipmentModule}>
              {({ getFieldValue }) => {
                const showModuleFields = getFieldValue('platform') === '模组' || getFieldValue('standardEquipmentModule')
                return showModuleFields ? (
                  <ScopeGrid>
                    <Form.Item name="moduleTags" label="模组标签"><Select mode="multiple" placeholder="选择模组标签" options={moduleTagOptions} /></Form.Item>
                    <Form.Item name="linkedModuleAssetIds" label="关联模块数模 ID"><Input prefix={<LinkOutlined />} placeholder="多个 ID 用逗号分隔" /></Form.Item>
                    <Form.Item name="equipmentInterconnectCode" label="设备互联编码"><Input prefix={<SettingOutlined />} placeholder="可选，用于关联拉线数据" /></Form.Item>
                  </ScopeGrid>
                ) : <Typography.Text type="secondary">选择“模组”平台或开启标准设备模块后，可补充标签、数模超链接和设备互联编码。</Typography.Text>
              }}
            </Form.Item>
          </FormSection>
        </MetadataPanel>

        <UploadPanel>
          <SectionHeader>
            <div><SectionTitle>资产文件</SectionTitle><SectionHint>选择不会立即上传，点击保存或提交时才开始校验。</SectionHint></div>
            <Tag color={fileList.length > 0 ? 'green' : 'default'}>{fileList.length} 个文件</Tag>
          </SectionHeader>
          <Form.Item
            name="files"
            valuePropName="fileList"
            getValueFromEvent={normalizeFiles}
            rules={[{ required: true, type: 'array', min: 1, message: '请至少选择一个资产文件' }]}
          >
            <Dropzone {...uploadProps}>
              <p className="ant-upload-drag-icon"><InboxOutlined /></p>
              <p className="ant-upload-text">拖拽文件或文件夹到此处</p>
              <p className="ant-upload-hint">X_T / STEP / PDF / DWG / 图片 / 说明附件</p>
            </Dropzone>
          </Form.Item>
          <Alert
            type={formatSummary.unknownCount > 0 ? 'warning' : 'info'}
            showIcon
            message={fileList.length === 0
              ? '系统会根据文件后缀预填角色，上传后继续校验格式、大小和内容。'
              : formatSummary.unknownCount > 0
                ? `${formatSummary.unknownCount} 个文件后缀未识别，请确认是否为允许的资产格式。`
                : `已识别格式：${formatSummary.formats.join('、')}`}
          />
          <QueueSummary>
            <div><QueueLabel>文件队列</QueueLabel><QueueHint>可批量调整角色和归组</QueueHint></div>
            <Space>
              <Select value={batchRole || undefined} onChange={setBatchRole} placeholder="批量角色" options={roleOptions.map((value) => ({ value, label: value }))} style={{ width: 120 }} />
              <Button size="small" disabled={!batchRole || fileList.length === 0} onClick={applyBatchRole}>应用</Button>
            </Space>
          </QueueSummary>
          <FilesTable>
            <Table
              rowKey="uid"
              columns={columns}
              dataSource={fileList}
              pagination={false}
              size="small"
              scroll={{ y: 90 }}
              locale={{ emptyText: '选择文件后显示处理队列' }}
            />
          </FilesTable>
          <Button block size="small" disabled={fileList.length === 0} onClick={autoGroup}>自动归组</Button>
        </UploadPanel>
        </UploadScrollArea>

        <ActionBar>
          <Button icon={<ReloadOutlined />} onClick={() => form.resetFields()}>清空</Button>
          <Button loading={saving || uploading} disabled={fileList.length === 0} onClick={() => void saveDraft()}>保存草稿</Button>
          <Button type="primary" htmlType="submit" loading={saving || uploading} icon={<CloudUploadOutlined />}>提交待整理</Button>
        </ActionBar>
      </Form>
    </UploadFrame>
  )
}
