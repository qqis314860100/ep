import { FilterOutlined } from '@ant-design/icons'
import { Alert, Button, Checkbox, Form, Input, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getInventory } from '../api'
import type { InventoryFilters, InventoryView } from '../api'
import { AssetStatusTag, AssetTypeTag } from '../../assets/AssetTags'
import { assetTypeLabels } from '../../assets/assetPresentation'

const Header = styled.header`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 14px;
`

const Metrics = styled.div`
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
`

const MetricCard = styled.div`
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #e2e7e4;
  border-radius: 6px;

  .ant-statistic-title {
    font-size: 11px;
  }

  .ant-statistic-content {
    font-size: 20px;
  }
`

const MissingTag = styled(Tag)`
  font-size: 10px;
`

const FILE_FORMATS = ['PDF', 'PNG', 'JPG', 'JPEG', 'TIFF', 'X_T', 'STEP', 'STP', 'DWG', 'DXF', 'DOCX', 'DOC', 'TXT']

export function GovernanceInventoryPage() {
  const navigate = useNavigate()
  const [filters, setFilters] = useState<InventoryFilters>({})
  const [page, setPage] = useState(1)
  const [form] = Form.useForm()
  const query = useQuery({
    queryKey: ['governance-inventory', filters, page],
    queryFn: () => getInventory({ ...filters, page, perPage: 10 }),
  })
  const view: InventoryView | undefined = query.data

  const columns: ColumnsType<InventoryView['items'][number]> = [
    { title: '资产', dataIndex: 'assetName', width: 180, render: (_, item) => <Button type="link" style={{ padding: 0 }} onClick={() => navigate(`/assets/${item.assetId}`)}>{item.assetName}</Button> },
    { title: '编号', dataIndex: 'assetNumber', width: 140 },
    { title: '类型', dataIndex: 'assetType', width: 110, render: (value) => <AssetTypeTag type={value as never} /> },
    { title: '状态', dataIndex: 'status', width: 100, render: (value) => <AssetStatusTag status={value as never} /> },
    { title: '负责人', dataIndex: 'ownerName', width: 90, render: (value) => value || <Tag color="orange">缺失</Tag> },
    { title: '旧平台', dataIndex: 'legacyPlatform', width: 110, ellipsis: true },
    { title: '旧拉线', dataIndex: 'legacyLine', width: 100, ellipsis: true },
    {
      title: '缺失项',
      key: 'missing',
      render: (_, item) => (
        <Space size={4} wrap>
          {item.missingBase && <MissingTag color="orange">基地</MissingTag>}
          {item.missingLine && <MissingTag color="orange">拉线</MissingTag>}
          {item.missingDescription && <MissingTag color="orange">说明</MissingTag>}
          {item.missingOwner && <MissingTag color="orange">负责人</MissingTag>}
          {item.missingFile && <MissingTag color="orange">文件</MissingTag>}
          {item.claimed && <Tag color="blue">已认领</Tag>}
          {!item.missingBase && !item.missingLine && !item.missingDescription && !item.missingOwner && !item.missingFile && !item.claimed && <Tag>完整</Tag>}
        </Space>
      ),
    },
  ]

  const submit = (values: InventoryFilters) => {
    setPage(1)
    setFilters(values)
  }

  return (
    <section>
      <Header>
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>资产盘点</Typography.Title>
          <Typography.Text type="secondary">统计资产质量与治理缺口，按旧维度或缺字段筛选问题资产</Typography.Text>
        </div>
        <Button icon={<FilterOutlined />} onClick={() => form.submit()}>应用筛选</Button>
      </Header>

      <Metrics>
        <MetricCard><Statistic title="资产总量" value={view?.totals.total ?? 0} /></MetricCard>
        <MetricCard><Statistic title="待整理" value={view?.totals.pendingCuration ?? 0} /></MetricCard>
        <MetricCard><Statistic title="已认领" value={view?.totals.claimed ?? 0} /></MetricCard>
        <MetricCard><Statistic title="已标准化" value={view?.totals.standardized ?? 0} /></MetricCard>
        <MetricCard><Statistic title="疑似重复" value={view?.totals.duplicateSuspects ?? 0} /></MetricCard>
        <MetricCard><Statistic title="异常文件" value={view?.totals.anomalousFiles ?? 0} /></MetricCard>
        <MetricCard><Statistic title="必填完整率" value={view?.rates.completeness ?? 0} suffix="%" /></MetricCard>
        <MetricCard><Statistic title="范围覆盖率" value={view?.rates.scopeCoverage ?? 0} suffix="%" /></MetricCard>
        <MetricCard><Statistic title="负责人覆盖率" value={view?.rates.ownerCoverage ?? 0} suffix="%" /></MetricCard>
        <MetricCard><Statistic title="文件可用率" value={view?.rates.fileAvailability ?? 0} suffix="%" /></MetricCard>
      </Metrics>

      <Form form={form} layout="inline" onFinish={submit} style={{ marginBottom: 12, rowGap: 8 }}>
        <Form.Item name="legacyPlatform" label="旧平台"><Input allowClear placeholder="旧平台文本" style={{ width: 130 }} /></Form.Item>
        <Form.Item name="legacyLine" label="旧拉线"><Input allowClear placeholder="旧拉线文本" style={{ width: 130 }} /></Form.Item>
        <Form.Item name="legacyCategory" label="旧分类">
          <Select allowClear placeholder="资产类型" style={{ width: 130 }} options={Object.entries(assetTypeLabels).map(([value, label]) => ({ value, label }))} />
        </Form.Item>
        <Form.Item name="owner" label="创建人"><Input allowClear placeholder="负责人" style={{ width: 120 }} /></Form.Item>
        <Form.Item name="format" label="文件格式">
          <Select allowClear placeholder="格式" style={{ width: 110 }} options={FILE_FORMATS.map((value) => ({ value, label: value }))} />
        </Form.Item>
        <Form.Item name="missingBase" label="缺基地" valuePropName="checked"><Checkbox /></Form.Item>
        <Form.Item name="missingLine" label="缺标准拉线" valuePropName="checked"><Checkbox /></Form.Item>
        <Form.Item name="missingDescription" label="缺功能说明" valuePropName="checked"><Checkbox /></Form.Item>
        <Form.Item name="missingOwner" label="缺负责人" valuePropName="checked"><Checkbox /></Form.Item>
        <Form.Item name="missingFile" label="缺可用文件" valuePropName="checked"><Checkbox /></Form.Item>
      </Form>

      {query.isError && <Alert type="error" showIcon message="盘点数据加载失败" style={{ marginBottom: 12 }} />}
      <Table
        rowKey="assetId"
        size="small"
        loading={query.isLoading}
        columns={columns}
        dataSource={view?.items ?? []}
        scroll={{ x: 1100 }}
        pagination={{ current: page, pageSize: 10, total: view?.meta.total ?? 0, showSizeChanger: false, onChange: setPage }}
      />
    </section>
  )
}
