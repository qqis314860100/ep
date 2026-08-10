import {
  CheckCircleOutlined,
  FileProtectOutlined,
  PauseCircleOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useState } from 'react'
import styled from 'styled-components'
import {
  createGovernanceStandard,
  createGovernanceStandardVersion,
  disableGovernanceStandard,
  enableGovernanceStandard,
  getGovernanceStandardImpactReviews,
  getGovernanceStandards,
} from '../api'
import type {
  CreateGovernanceStandardInput,
  GovernanceAssetType,
  GovernanceDataStandard,
  GovernanceStandardRule,
  GovernanceStandardRuleType,
} from '../types'

const Workspace = styled.section`
  min-width: 0;
`

const PageHeader = styled.header`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 14px;

  @media (max-width: 720px) {
    align-items: stretch;
    flex-direction: column;
  }
`

const ContextBar = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(120px, 1fr)) minmax(240px, 2fr);
  margin-bottom: 14px;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-radius: 4px;

  > div {
    min-height: 72px;
    padding: 12px 16px;
    border-right: 1px solid #e4e9e6;
  }

  > div:last-child { border-right: 0; }
  .ant-statistic-title { margin-bottom: 2px; font-size: 11px; }
  .ant-statistic-content { font-size: 21px; }

  @media (max-width: 900px) {
    grid-template-columns: repeat(3, 1fr);
    > div:nth-child(3) { border-right: 0; }
    > div:last-child { grid-column: 1 / -1; border-top: 1px solid #e4e9e6; }
  }

  @media (max-width: 560px) {
    grid-template-columns: 1fr;
    > div { border-right: 0; border-bottom: 1px solid #e4e9e6; }
    > div:last-child { grid-column: auto; border-top: 0; border-bottom: 0; }
  }
`

const MainGrid = styled.div`
  display: grid;
  grid-template-columns: minmax(320px, 0.72fr) minmax(0, 1.28fr);
  min-height: 520px;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-radius: 4px;

  @media (max-width: 980px) { grid-template-columns: 1fr; }
`

const VersionPane = styled.div`
  min-width: 0;
  border-right: 1px solid #dfe5e2;
  @media (max-width: 980px) { border-right: 0; border-bottom: 1px solid #dfe5e2; }
`

const PaneHeading = styled.div`
  padding: 12px 14px;
  background: #f7f9f8;
  border-bottom: 1px solid #e2e7e4;
  color: #34423c;
  font-size: 12px;
  font-weight: 650;
`

const DetailPane = styled.div`
  min-width: 0;
`

const DetailHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border-bottom: 1px solid #e2e7e4;

  @media (max-width: 640px) {
    flex-direction: column;
  }
`

const DetailBody = styled.div`
  padding: 16px 18px 22px;
`

const SectionTitle = styled.div`
  margin: 20px 0 9px;
  color: #34423c;
  font-size: 12px;
  font-weight: 700;
`

const StandardCell = styled.button<{ $selected: boolean }>`
  width: 100%;
  padding: 0;
  color: inherit;
  text-align: left;
  background: transparent;
  border: 0;
  cursor: pointer;

  strong { color: ${({ $selected }) => ($selected ? '#245f54' : '#34423c')}; }
  &:focus-visible { outline: 2px solid #3e897a; outline-offset: 2px; }
`

const RuleEditor = styled.div`
  padding: 10px 12px 2px;
  background: #f7f9f8;
  border: 1px solid #e1e7e4;
  border-radius: 4px;
`

const statusMeta = {
  DRAFT: { label: '草稿', color: 'default' },
  ENABLED: { label: '已启用', color: 'success' },
  DISABLED: { label: '已停用', color: 'warning' },
} as const

const assetTypeLabels: Record<GovernanceAssetType, string> = {
  THREE_DIMENSIONAL_MODEL: '三维模型',
  TWO_DIMENSIONAL_DRAWING: '二维图纸',
  MIXED_ASSET: '混合资产',
  OTHER: '其他资料',
}

const ruleTypeLabels: Record<GovernanceStandardRuleType, string> = {
  REQUIRED: '必填条件',
  NAMING: '命名规则',
  CONTROLLED_VALUE: '受控值',
  FILE_ROLE: '文件角色',
  QUALITY_THRESHOLD: '质量门槛',
}

type FormValues = CreateGovernanceStandardInput

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '尚未生效'
}

export function GovernanceStandardsPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [selectedId, setSelectedId] = useState<number>()
  const [drawerMode, setDrawerMode] = useState<'new' | 'version' | null>(null)
  const [form] = Form.useForm<FormValues>()
  const standardsQuery = useQuery({ queryKey: ['governance-standards'], queryFn: getGovernanceStandards })
  const selected = useMemo(
    () => standardsQuery.data?.find(item => item.id === selectedId) ?? standardsQuery.data?.[0],
    [selectedId, standardsQuery.data],
  )
  const impactQuery = useQuery({
    queryKey: ['governance-standard-impact', selected?.id],
    queryFn: () => getGovernanceStandardImpactReviews(selected!.id),
    enabled: Boolean(selected),
  })

  useEffect(() => {
    if (!selectedId && standardsQuery.data?.[0]) setSelectedId(standardsQuery.data[0].id)
  }, [selectedId, standardsQuery.data])

  const refresh = async (nextId?: number) => {
    await queryClient.invalidateQueries({ queryKey: ['governance-standards'] })
    await queryClient.invalidateQueries({ queryKey: ['governance-standard-impact'] })
    if (nextId) setSelectedId(nextId)
  }

  const createMutation = useMutation({
    mutationFn: async (values: FormValues) => drawerMode === 'new'
      ? createGovernanceStandard(values)
      : createGovernanceStandardVersion(selected!.id, values),
    onSuccess: async created => {
      setDrawerMode(null)
      form.resetFields()
      await refresh(created.id)
      void message.success('标准草稿已创建')
    },
  })
  const enableMutation = useMutation({
    mutationFn: (standard: GovernanceDataStandard) => enableGovernanceStandard(standard.id, standard.version),
    onSuccess: async result => {
      await refresh(result.standard.id)
      void message.success(`标准已启用，已生成 ${result.impactReview.affectedAssetCount} 项复核清单`)
    },
  })
  const disableMutation = useMutation({
    mutationFn: (standard: GovernanceDataStandard) => disableGovernanceStandard(standard.id, standard.version),
    onSuccess: async disabled => {
      await refresh(disabled.id)
      void message.success('标准已停用，新任务将不能使用该版本')
    },
  })

  const openDrawer = (mode: 'new' | 'version') => {
    setDrawerMode(mode)
    if (mode === 'new') {
      form.setFieldsValue({
        standardCode: '', standardVersion: 1, name: '', applicableAssetTypes: [],
        ownerUserId: '', ownerName: '', changeSummary: '', rules: [],
      })
      return
    }
    if (!selected) return
    form.setFieldsValue({
      standardCode: selected.standardCode,
      standardVersion: selected.standardVersion + 1,
      name: selected.name,
      applicableAssetTypes: selected.applicableAssetTypes,
      ownerUserId: selected.ownerUserId,
      ownerName: selected.ownerName,
      changeSummary: '',
      rules: selected.rules,
    })
  }

  const confirmEnable = () => selected && Modal.confirm({
    title: `启用 ${selected.standardCode} V${selected.standardVersion}`,
    icon: <SafetyCertificateOutlined />,
    content: '启用后，同编码的旧版本自动停用；执行中的治理任务继续使用启动时冻结版本，系统同时生成受影响资产复核清单。',
    okText: '确认启用',
    cancelText: '取消',
    onOk: () => enableMutation.mutateAsync(selected),
  })

  const confirmDisable = () => selected && Modal.confirm({
    title: `停用 ${selected.standardCode} V${selected.standardVersion}`,
    content: '停用后不能启动新的治理任务，已冻结该版本的执行中任务不受影响。',
    okText: '确认停用',
    cancelText: '取消',
    okButtonProps: { danger: true },
    onOk: () => disableMutation.mutateAsync(selected),
  })

  const columns: ColumnsType<GovernanceDataStandard> = [
    {
      title: '标准 / 版本', dataIndex: 'name',
      render: (_, item) => <StandardCell $selected={selected?.id === item.id} onClick={() => setSelectedId(item.id)}>
        <strong>{item.name}</strong><br />
        <Typography.Text type="secondary">{item.standardCode} · V{item.standardVersion}</Typography.Text>
      </StandardCell>,
    },
    { title: '状态', dataIndex: 'status', width: 82, render: value => <Tag color={statusMeta[value as keyof typeof statusMeta].color}>{statusMeta[value as keyof typeof statusMeta].label}</Tag> },
    { title: '负责人', dataIndex: 'ownerName', width: 82 },
  ]

  const enabledCount = standardsQuery.data?.filter(item => item.status === 'ENABLED').length ?? 0
  const draftCount = standardsQuery.data?.filter(item => item.status === 'DRAFT').length ?? 0
  const impactedCount = standardsQuery.data?.filter(item => item.status === 'ENABLED').reduce((total, item) => total + item.affectedAssetCount, 0) ?? 0

  return <Workspace>
    <PageHeader>
      <div><Typography.Title level={3} style={{ margin: 0 }}>数据标准中心</Typography.Title><Typography.Text type="secondary">统一维护分类、字段、命名、文件角色和质量门槛的版本基线</Typography.Text></div>
      <Space><Button aria-label="新建标准" icon={<PlusOutlined aria-hidden />} onClick={() => openDrawer('new')}>新建标准</Button><Button aria-label="新建版本" type="primary" icon={<FileProtectOutlined aria-hidden />} disabled={!selected} onClick={() => openDrawer('version')}>新建版本</Button></Space>
    </PageHeader>

    <ContextBar>
      <div><Statistic title="启用标准" value={enabledCount} suffix="个" /></div>
      <div><Statistic title="待完善草稿" value={draftCount} suffix="个" /></div>
      <div><Statistic title="影响资产" value={impactedCount} suffix="项" /></div>
      <div><Typography.Text strong>版本规则</Typography.Text><br /><Typography.Text type="secondary">已启用和已停用内容永久保留。标准内容变化必须创建新版本，新任务启动时冻结当时的启用版本。</Typography.Text></div>
    </ContextBar>

    {standardsQuery.isError && <Alert type="error" showIcon message="标准中心加载失败" description={standardsQuery.error.message} style={{ marginBottom: 14 }} />}
    <MainGrid>
      <VersionPane><PaneHeading>标准版本</PaneHeading><Table rowKey="id" size="small" columns={columns} dataSource={standardsQuery.data ?? []} loading={standardsQuery.isLoading} pagination={false} scroll={{ x: 420 }} rowClassName={item => selected?.id === item.id ? 'ant-table-row-selected' : ''} /></VersionPane>
      <DetailPane>
        {!selected ? <Empty description="暂无数据标准" style={{ marginTop: 120 }} /> : <>
          <DetailHeader>
            <div><Space wrap><Typography.Title level={4} style={{ margin: 0 }}>{selected.name}</Typography.Title><Tag color={statusMeta[selected.status].color}>{statusMeta[selected.status].label}</Tag></Space><Typography.Text type="secondary">{selected.standardCode} · 业务版本 V{selected.standardVersion} · 并发版本 {selected.version}</Typography.Text></div>
            <Space>
              {selected.status === 'DRAFT' && <Button aria-label="启用" type="primary" icon={<CheckCircleOutlined aria-hidden />} loading={enableMutation.isPending} onClick={confirmEnable}>启用</Button>}
              {selected.status === 'ENABLED' && <Button aria-label="停用" danger icon={<PauseCircleOutlined aria-hidden />} loading={disableMutation.isPending} onClick={confirmDisable}>停用</Button>}
            </Space>
          </DetailHeader>
          <DetailBody>
            <Descriptions size="small" column={{ xs: 1, sm: 2 }} bordered items={[
              { key: 'owner', label: '负责人', children: `${selected.ownerName} (${selected.ownerUserId})` },
              { key: 'effective', label: '生效时间', children: formatTime(selected.effectiveAt) },
              { key: 'types', label: '适用资产', children: selected.applicableAssetTypes.length ? selected.applicableAssetTypes.map(type => assetTypeLabels[type]).join('、') : '全部资产类型' },
              { key: 'impact', label: '影响资产', children: `${selected.affectedAssetCount} 项` },
              { key: 'change', label: '变更说明', span: { xs: 1, sm: 2 }, children: selected.changeSummary || '无' },
            ]} />

            <SectionTitle>字段规则与质量门槛</SectionTitle>
            <Table<GovernanceStandardRule> rowKey={rule => `${rule.targetField}-${rule.ruleType}`} size="small" pagination={false} scroll={{ x: 560 }} dataSource={selected.rules} columns={[
              { title: '目标字段', dataIndex: 'targetField', width: 120 },
              { title: '规则类型', dataIndex: 'ruleType', width: 110, render: value => ruleTypeLabels[value as GovernanceStandardRuleType] },
              { title: '规则说明', dataIndex: 'description' },
              { title: '质量阻断', dataIndex: 'blocking', width: 86, render: value => value ? <Tag color="error">阻断</Tag> : <Tag>提示</Tag> },
            ]} locale={{ emptyText: '尚未配置规则' }} />

            <SectionTitle>影响复核</SectionTitle>
            {impactQuery.isError && <Alert type="error" showIcon message="影响复核加载失败" />}
            <Table rowKey="id" size="small" pagination={false} scroll={{ x: 720 }} loading={impactQuery.isLoading} dataSource={impactQuery.data ?? []} columns={[
              { title: '复核单', dataIndex: 'id', width: 80, render: value => `#${value}` },
              { title: '状态', dataIndex: 'status', width: 90, render: value => <Tag color={value === 'OPEN' ? 'processing' : 'success'}>{value === 'OPEN' ? '待复核' : '已完成'}</Tag> },
              { title: '资产数量', dataIndex: 'affectedAssetCount', width: 92 },
              { title: '资产 ID', dataIndex: 'assetIds', render: value => value.length ? value.join('、') : '无受影响资产' },
              { title: '生成时间', dataIndex: 'createdAt', width: 170, render: formatTime },
            ]} locale={{ emptyText: '该版本尚未生成影响复核单' }} />
          </DetailBody>
        </>}
      </DetailPane>
    </MainGrid>

    <Drawer title={drawerMode === 'new' ? '新建数据标准' : `新建 ${selected?.standardCode ?? ''} 版本`} open={drawerMode !== null} width={560} onClose={() => setDrawerMode(null)} extra={<Button type="primary" loading={createMutation.isPending} onClick={() => form.submit()}>保存草稿</Button>}>
      {createMutation.error && <Alert type="error" showIcon message={createMutation.error.message} style={{ marginBottom: 14 }} />}
      <Form form={form} layout="vertical" onFinish={values => createMutation.mutate(values)}>
        <Space align="start" style={{ display: 'flex' }}>
          <Form.Item name="standardCode" label="标准编码" rules={[{ required: true }]} style={{ flex: 1 }}><Input disabled={drawerMode === 'version'} placeholder="例如 MODEL-ASSET" /></Form.Item>
          <Form.Item name="standardVersion" label="业务版本" rules={[{ required: true }]} style={{ width: 120 }}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
        </Space>
        <Form.Item name="name" label="标准名称" rules={[{ required: true }]}><Input /></Form.Item>
        <Space align="start" style={{ display: 'flex' }}>
          <Form.Item name="ownerUserId" label="负责人工号" rules={[{ required: true }]} style={{ flex: 1 }}><Input /></Form.Item>
          <Form.Item name="ownerName" label="负责人姓名" rules={[{ required: true }]} style={{ flex: 1 }}><Input /></Form.Item>
        </Space>
        <Form.Item name="applicableAssetTypes" label="适用资产类型"><Select mode="multiple" options={Object.entries(assetTypeLabels).map(([value, label]) => ({ value, label }))} placeholder="留空表示全部资产类型" /></Form.Item>
        <Form.Item name="changeSummary" label="变更说明" rules={[{ required: true }]}><Input.TextArea rows={3} /></Form.Item>
        <Form.List name="rules">
          {(fields, { add, remove }) => <Space direction="vertical" style={{ width: '100%' }}>
            <Space style={{ width: '100%', justifyContent: 'space-between' }}><Typography.Text strong>字段规则与质量门槛</Typography.Text><Button size="small" icon={<PlusOutlined />} onClick={() => add({ targetField: '', ruleType: 'REQUIRED', description: '', blocking: true, configurationJson: '{}' })}>添加规则</Button></Space>
            {fields.map(field => <RuleEditor key={field.key}>
              <Space align="start" style={{ display: 'flex' }}>
                <Form.Item name={[field.name, 'targetField']} label="目标字段" rules={[{ required: true }]} style={{ flex: 1 }}><Input /></Form.Item>
                <Form.Item name={[field.name, 'ruleType']} label="规则类型" rules={[{ required: true }]} style={{ width: 150 }}><Select options={Object.entries(ruleTypeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
              </Space>
              <Form.Item name={[field.name, 'description']} label="规则说明" rules={[{ required: true }]}><Input /></Form.Item>
              <Space align="start" style={{ display: 'flex' }}>
                <Form.Item name={[field.name, 'blocking']} label="质量处理" style={{ width: 150 }}><Select options={[{ value: true, label: '不合格时阻断' }, { value: false, label: '仅提示' }]} /></Form.Item>
                <Form.Item name={[field.name, 'configurationJson']} label="规则配置 JSON" style={{ flex: 1 }}><Input /></Form.Item>
                <Button danger type="text" style={{ marginTop: 30 }} onClick={() => remove(field.name)}>删除</Button>
              </Space>
            </RuleEditor>)}
          </Space>}
        </Form.List>
      </Form>
    </Drawer>
  </Workspace>
}
