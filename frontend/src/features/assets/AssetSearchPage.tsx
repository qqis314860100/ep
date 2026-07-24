import {
  AppstoreOutlined,
  CloudUploadOutlined,
  EyeOutlined,
  FileImageOutlined,
  FileOutlined,
  FilePdfOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import {
  Button,
  Empty,
  Pagination,
  Segmented,
  Skeleton,
  Space,
  Switch,
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
import { getAssetFilePreviewUrl } from '../../services/assetService'
import type { Asset, AssetFile, AssetSearchParams, AssetStatus, AssetType } from '../../types/asset'
import { scopeLabel } from './assetPresentation'
import { AssetStatusTag, AssetTypeTag } from './AssetTags'

type ViewMode = 'gallery' | 'list'

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

const ResultsToolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-top: 18px;
`

const ResultCount = styled.div`
  color: #68746f;
  font-size: 13px;
`

const VisualGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-top: 14px;

  @media (max-width: 1260px) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
`

const AssetCard = styled.button`
  min-width: 0;
  padding: 0;
  overflow: hidden;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;

  &:hover,
  &:focus-visible {
    border-color: #8fb7aa;
    box-shadow: 0 8px 22px rgba(36, 62, 52, 0.1);
    outline: none;
    transform: translateY(-2px);
  }
`

const PreviewFrame = styled.div`
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background-color: #eef2f0;
  background-image:
    linear-gradient(#dfe7e3 1px, transparent 1px),
    linear-gradient(90deg, #dfe7e3 1px, transparent 1px);
  background-size: 24px 24px;
  border-bottom: 1px solid #e0e6e2;
`

const PreviewImage = styled.img`
  width: 100%;
  height: 100%;
  object-fit: contain;
`

const PreviewBadge = styled.span`
  position: absolute;
  top: 10px;
  left: 10px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 7px;
  color: #214f43;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid rgba(47, 117, 103, 0.24);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
`

const FormatPreview = styled.div`
  display: grid;
  justify-items: center;
  max-width: calc(100% - 36px);
  color: #36554b;
`

const FormatIcon = styled.div`
  display: grid;
  place-items: center;
  width: 58px;
  height: 68px;
  color: #2f7567;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid #cbd8d2;
  border-radius: 5px;
  box-shadow: 6px 7px 0 rgba(35, 74, 62, 0.08);
  font-size: 27px;
`

const PreviewFileName = styled.div`
  width: 100%;
  margin-top: 12px;
  overflow: hidden;
  color: #52645d;
  font-size: 11px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const CardBody = styled.div`
  padding: 13px 14px 14px;
`

const CardTitle = styled.div`
  overflow: hidden;
  color: #20332c;
  font-size: 15px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const CardNumber = styled.div`
  margin-top: 3px;
  color: #7a8680;
  font-size: 11px;
`

const CardDescription = styled.div`
  display: -webkit-box;
  min-height: 38px;
  margin-top: 10px;
  overflow: hidden;
  color: #5e6b65;
  font-size: 12px;
  line-height: 19px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
`

const CardFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  color: #6c7973;
  border-top: 1px solid #edf0ee;
  font-size: 11px;
`

const GalleryEmpty = styled.div`
  margin-top: 14px;
  padding: 70px 0;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const PaginationRow = styled.div`
  display: flex;
  justify-content: flex-end;
  margin-top: 18px;
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

const imageFormats = new Set(['PNG', 'JPG', 'JPEG', 'TIFF', 'WEBP'])

function previewFile(asset: Asset) {
  return asset.files.find((file) => file.previewable && imageFormats.has(file.format))
    ?? asset.files.find((file) => file.previewable)
}

function previewIcon(file?: AssetFile) {
  if (!file) return <FileOutlined />
  if (file.format === 'PDF') return <FilePdfOutlined />
  if (imageFormats.has(file.format)) return <FileImageOutlined />
  return <FileOutlined />
}

function useDebouncedValue<T>(value: T, delay: number) {
  const [debouncedValue, setDebouncedValue] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebouncedValue(value), delay)
    return () => window.clearTimeout(timer)
  }, [value, delay])
  return debouncedValue
}

export function AssetSearchPage() {
  const navigate = useNavigate()
  const [query, setQuery] = useState('')
  const [assetType, setAssetType] = useState<AssetType>()
  const [status, setStatus] = useState<AssetStatus>()
  const [platformFamily, setPlatformFamily] = useState<string>()
  const [platformVariant, setPlatformVariant] = useState<string>()
  const [base, setBase] = useState<string>()
  const [productionLine, setProductionLine] = useState<string>()
  const [previewableOnly, setPreviewableOnly] = useState(true)
  const [viewMode, setViewMode] = useState<ViewMode>('gallery')
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
    previewable: previewableOnly,
    page,
    perPage: viewMode === 'gallery' ? 12 : 20,
  }
  const assetsQuery = useAssetSearch(params)

  useEffect(() => setPage(1), [debouncedQuery, assetType, status, platformFamily, platformVariant, base, productionLine, previewableOnly, viewMode])

  const columns = useMemo<ColumnsType<Asset>>(
    () => [
      {
        title: '数模资产',
        dataIndex: 'name',
        width: 260,
        render: (_, asset) => (
          <AssetNameCell>
            <AssetTitle>{asset.name}</AssetTitle>
            <AssetNumber>{asset.assetNumber}</AssetNumber>
          </AssetNameCell>
        ),
      },
      {
        title: '类型',
        dataIndex: 'assetType',
        width: 112,
        render: (value: AssetType) => <AssetTypeTag type={value} />,
      },
      {
        title: '主要适用范围',
        dataIndex: 'scopes',
        width: 250,
        render: (_, asset) => scopeLabel(asset.scopes[0] ?? {
          platform: '', productLine: '', base: '', productionLine: '', processSection: '',
        }),
      },
      {
        title: '文件',
        dataIndex: 'files',
        width: 110,
        render: (_, asset) => (
          <Space size={5}>
            <FileOutlined />
            <span>{asset.files.length}</span>
            <Typography.Text type="secondary">{asset.files[0]?.format ?? '-'}</Typography.Text>
          </Space>
        ),
      },
      {
        title: '专业',
        dataIndex: 'specialties',
        width: 145,
        render: (values: string[]) => values.slice(0, 2).map((value) => <Tag key={value}>{value}</Tag>),
      },
      { title: '负责人', dataIndex: 'ownerName', width: 90 },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (value: AssetStatus) => <AssetStatusTag status={value} />,
      },
      {
        title: '更新时间',
        dataIndex: 'updatedAt',
        width: 112,
        render: (value: string) => new Intl.DateTimeFormat('zh-CN').format(new Date(value)),
      },
    ],
    [],
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

  const assets = assetsQuery.data?.data ?? []
  const total = assetsQuery.data?.meta.total ?? 0

  return (
    <>
      <SearchHero
        query={query}
        onQueryChange={setQuery}
        onSearch={() => setPage(1)}
        onQuickSearch={(value) => {
          if (value === '可预览资料') setPreviewableOnly(true)
          else if (value.includes('基地')) setBase(value)
          else if (value === '三维模型') setAssetType('THREE_DIMENSIONAL_MODEL')
          else if (value === 'PDF 图纸') setAssetType('TWO_DIMENSIONAL_DRAWING')
          else setQuery(value)
        }}
      />

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

      <ResultsToolbar>
        <ResultCount>{previewableOnly ? `共 ${total} 项可在线预览资产` : `共 ${total} 项资产`}</ResultCount>
        <Space size={16}>
          <Space size={7}><Typography.Text type="secondary" style={{ fontSize: 12 }}>仅看可预览</Typography.Text><Switch size="small" checked={previewableOnly} onChange={setPreviewableOnly} /></Space>
          <Segmented<ViewMode>
            value={viewMode}
            onChange={setViewMode}
            options={[
              { value: 'gallery', label: '图像浏览', icon: <AppstoreOutlined /> },
              { value: 'list', label: '紧凑列表', icon: <UnorderedListOutlined /> },
            ]}
          />
          <Button type="link" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传新资料</Button>
        </Space>
      </ResultsToolbar>

      {viewMode === 'gallery' ? (
        assetsQuery.isError ? (
          <GalleryEmpty><Empty description="资产列表加载失败"><Button type="primary" onClick={() => void assetsQuery.refetch()}>重试</Button></Empty></GalleryEmpty>
        ) : assetsQuery.isLoading ? (
          <VisualGrid>{Array.from({ length: 8 }, (_, index) => <AssetCard key={index} type="button"><Skeleton.Node active style={{ width: '100%', height: 210 }} /><CardBody><Skeleton active paragraph={{ rows: 2 }} title={{ width: '70%' }} /></CardBody></AssetCard>)}</VisualGrid>
        ) : assets.length ? (
          <>
            <VisualGrid>
              {assets.map((asset) => {
                const file = previewFile(asset)
                const previewUrl = file ? getAssetFilePreviewUrl(asset.id, file) : undefined
                const scope = asset.scopes[0]
                return (
                  <AssetCard key={asset.id} type="button" onClick={() => navigate(`/assets/${asset.id}`)} aria-label={`打开 ${asset.name}`}>
                    <PreviewFrame>
                      {previewUrl && file && imageFormats.has(file.format) ? <PreviewImage src={previewUrl} alt={`${asset.name}预览`} loading="lazy" /> : <FormatPreview><FormatIcon>{previewIcon(file)}</FormatIcon><PreviewFileName>{file?.name ?? '暂无预览文件'}</PreviewFileName></FormatPreview>}
                      {file && <PreviewBadge><EyeOutlined />{file.format} 可预览</PreviewBadge>}
                    </PreviewFrame>
                    <CardBody>
                      <Space size={5} wrap><AssetTypeTag type={asset.assetType} />{scope?.platformFamily && <Tag>{scope.platformFamily}{scope.platformVariant ? ` · ${scope.platformVariant}` : ''}</Tag>}</Space>
                      <CardTitle title={asset.name}>{asset.name}</CardTitle>
                      <CardNumber>{asset.assetNumber}</CardNumber>
                      <CardDescription>{asset.description || '暂无功能说明'}</CardDescription>
                      <CardFooter><span>{asset.files.filter((item) => item.previewable).length} 个可预览文件</span><span>{scope?.base || asset.ownerName}</span></CardFooter>
                    </CardBody>
                  </AssetCard>
                )
              })}
            </VisualGrid>
            {total > params.perPage && <PaginationRow><Pagination current={page} pageSize={params.perPage} total={total} showSizeChanger={false} onChange={setPage} /></PaginationRow>}
          </>
        ) : (
          <GalleryEmpty><Empty description={previewableOnly ? '没有符合条件的可预览资产' : '没有符合条件的数模资产'} /></GalleryEmpty>
        )
      ) : (
        <Results>
          {assetsQuery.isError ? (
            <Empty description="资产列表加载失败" style={{ padding: '72px 0' }}><Button type="primary" onClick={() => void assetsQuery.refetch()}>重试</Button></Empty>
          ) : (
            <Table
              rowKey="id"
              columns={columns}
              dataSource={assets}
              loading={assetsQuery.isFetching}
              size="middle"
              scroll={{ x: 1200 }}
              locale={{ emptyText: <Empty description="没有符合条件的数模资产" /> }}
              pagination={{ current: page, pageSize: params.perPage, total, showSizeChanger: false, onChange: setPage }}
              onRow={(record) => ({ onClick: () => navigate(`/assets/${record.id}`) })}
            />
          )}
        </Results>
      )}
    </>
  )
}
