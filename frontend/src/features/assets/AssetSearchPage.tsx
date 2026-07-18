import {
  CloudUploadOutlined,
  DatabaseOutlined,
  FileOutlined,
  FileImageOutlined,
} from '@ant-design/icons'
import {
  Button,
  Empty,
  Flex,
  Input,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { useAssetSearch } from '../../hooks/useAssets'
import { SearchHero } from '../../pages/main/search/components/SearchHero'
import { SearchSidebar } from '../../pages/main/search/components/SearchSidebar'
import type { Asset, AssetSearchParams, AssetStatus, AssetType } from '../../types/asset'
import {
  scopeLabel,
} from './assetPresentation'
import { AssetStatusTag, AssetTypeTag } from './AssetTags'

const Results = styled.div`
  margin-top: 14px;
  background: #ffffff;
  border: 1px solid #dce3df;
  border-radius: 6px;

  .ant-table-wrapper .ant-table {
    border-radius: 6px;
  }

  .ant-table-row {
    cursor: pointer;
  }
`

const AssetNameCell = styled.div`
  min-width: 0;
`

const AssetTitle = styled.div`
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const AssetNumber = styled.div`
  margin-top: 2px;
  color: #78847f;
  font-size: 12px;
`

const ManagementHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 20px;
`

const ManagementTitle = styled.h1`
  margin: 0 0 4px;
  color: #202824;
  font-size: 22px;
  font-weight: 600;
`

const ManagementSubtitle = styled.div`
  color: #68746f;
  font-size: 13px;
`

const PreviewCell = styled.div`
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
`

const PreviewThumb = styled.div`
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 72px;
  height: 48px;
  color: #2f7567;
  background: linear-gradient(135deg, #edf5f1, #dcebe3);
  border: 1px solid #cfe1d8;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 700;
`

function useDebouncedValue<T>(value: T, delay: number) {
  const [debouncedValue, setDebouncedValue] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedValue(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debouncedValue
}

interface AssetSearchPageProps {
  management?: boolean
}

export function AssetSearchPage({ management = false }: AssetSearchPageProps) {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [assetType, setAssetType] = useState<AssetType>()
  const [status, setStatus] = useState<AssetStatus>()
  const [platformFamily, setPlatformFamily] = useState<string>()
  const [platformVariant, setPlatformVariant] = useState<string>()
  const [base, setBase] = useState<string>()
  const [productionLine, setProductionLine] = useState<string>()
  const [page, setPage] = useState(1)
  const debouncedQuery = useDebouncedValue(query, 280)

  const params: AssetSearchParams = {
    query: debouncedQuery,
    assetType,
    status,
    platformFamily,
    platformVariant,
    base,
    productionLine,
    page,
    perPage: 20,
  }
  const assetsQuery = useAssetSearch(params)

  useEffect(() => setPage(1), [debouncedQuery, assetType, status, platformFamily, platformVariant, base, productionLine])

  const columns = useMemo<ColumnsType<Asset>>(
    () => [
      {
        title: '预览',
        dataIndex: 'preview',
        width: management ? 210 : 230,
        render: (_, asset) => {
          const preview = asset.files.find((file) => file.previewable)
          const primary = asset.files.find((file) => file.primary) ?? asset.files[0]
          const format = preview?.format ?? primary?.format ?? 'FILE'
          return <PreviewCell><PreviewThumb><FileImageOutlined style={{ marginRight: 4 }} />{format}</PreviewThumb><AssetNameCell><AssetTitle>{asset.name}</AssetTitle><AssetNumber>{asset.assetNumber}</AssetNumber></AssetNameCell></PreviewCell>
        },
      },
      {
        title: management ? '资料编号' : '数模资产',
        dataIndex: 'name',
        width: management ? 130 : 260,
        render: (_, asset) => management ? <Typography.Text type="secondary">{asset.assetNumber}</Typography.Text> : (
          <AssetNameCell>
            <AssetTitle>{asset.name}</AssetTitle>
            <AssetNumber>{asset.assetNumber}</AssetNumber>
          </AssetNameCell>
        ),
      },
      {
        title: '类型',
        dataIndex: 'assetType',
        width: management ? 100 : 112,
        render: (value: AssetType) => <AssetTypeTag type={value} />,
      },
      {
        title: '主要适用范围',
        dataIndex: 'scopes',
        width: management ? 220 : 250,
        render: (_, asset) => scopeLabel(asset.scopes[0] ?? {
          platform: '', productLine: '', base: '', productionLine: '', processSection: '',
        }),
      },
      {
        title: '文件',
        dataIndex: 'files',
        width: management ? 100 : 110,
        render: (_, asset) => (
          <Space size={5}>
            <FileOutlined />
            <span>{asset.files.length}</span>
            <Typography.Text type="secondary">
              {asset.files[0]?.format ?? '-'}
            </Typography.Text>
          </Space>
        ),
      },
      {
        title: '专业',
        dataIndex: 'specialties',
        width: management ? 110 : 145,
        render: (values: string[]) => values.slice(0, 2).map((value) => <Tag key={value}>{value}</Tag>),
      },
      { title: '负责人', dataIndex: 'ownerName', width: management ? 80 : 90 },
      {
        title: '状态',
        dataIndex: 'status',
        width: management ? 90 : 110,
        render: (value: AssetStatus) => <AssetStatusTag status={value} />,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: management ? 100 : 112,
        render: (value: string) => new Intl.DateTimeFormat('zh-CN').format(new Date(value)),
      },
    ],
    [management],
  )

  const clearFilters = () => {
    setQuery('')
    setAssetType(undefined)
    setStatus(undefined)
    setPlatformFamily(undefined)
    setPlatformVariant(undefined)
    setBase(undefined)
    setProductionLine(undefined)
  }

  return (
    <>
      {management ? (
        <ManagementHeader>
          <div>
            <ManagementTitle>图纸与数模资产</ManagementTitle>
            <ManagementSubtitle>按平台、基地、拉线和文件类型查找生产资料</ManagementSubtitle>
          </div>
          <Space>
            <Button icon={<DatabaseOutlined />} onClick={() => navigate('/governance')}>数据治理</Button>
            <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传资料</Button>
          </Space>
        </ManagementHeader>
      ) : (
        <SearchHero
          query={query}
          onQueryChange={setQuery}
          onSearch={() => setPage(1)}
          onQuickSearch={(value) => {
            if (value.includes('基地')) setBase(value)
            else if (value === '三维模型') setAssetType('THREE_DIMENSIONAL_MODEL')
            else setQuery(value)
          }}
        />
      )}

      {management && <Input.Search value={query} onChange={(event) => setQuery(event.target.value)} onSearch={() => setPage(1)} placeholder="搜索资料编号、名称、文件名" allowClear style={{ width: 420, marginBottom: 14 }} />}

      <SearchSidebar
        assetType={assetType}
        platformFamily={platformFamily}
        platformVariant={platformVariant}
        base={base}
        productionLine={productionLine}
        status={status}
        onAssetTypeChange={setAssetType}
        onPlatformFamilyChange={(value) => {
          setPlatformFamily(value)
          setPlatformVariant(undefined)
        }}
        onPlatformVariantChange={setPlatformVariant}
        onBaseChange={setBase}
        onProductionLineChange={setProductionLine}
        onStatusChange={setStatus}
        onClear={clearFilters}
      />

      <Flex justify="space-between" align="center" style={{ marginTop: 14 }}>
        <Typography.Text type="secondary">
          共 {assetsQuery.data?.meta.total ?? 0} 项资产
        </Typography.Text>
        <Button type="link" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传新资料</Button>
      </Flex>

      <Results>
        {assetsQuery.isError ? (
          <Empty
            description="资产列表加载失败"
            style={{ padding: '72px 0' }}
          >
            <Button type="primary" onClick={() => void assetsQuery.refetch()}>
              重试
            </Button>
          </Empty>
        ) : (
          <Table
            rowKey="id"
            columns={columns}
            dataSource={assetsQuery.data?.data ?? []}
            loading={assetsQuery.isFetching}
            size="middle"
            scroll={{ x: management ? 1100 : 1200 }}
            locale={{ emptyText: <Empty description="没有符合条件的数模资产" /> }}
            pagination={{
              current: page,
              pageSize: 20,
              total: assetsQuery.data?.meta.total ?? 0,
              showSizeChanger: false,
              onChange: setPage,
            }}
            onRow={(record) => ({ onClick: () => navigate(`/assets/${record.id}`) })}
          />
        )}
      </Results>

    </>
  )
}
