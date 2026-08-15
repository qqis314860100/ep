import {
  AppstoreOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  EnvironmentOutlined,
  EyeOutlined,
  FileImageOutlined,
  FileOutlined,
  FilePdfOutlined,
  FolderOpenOutlined,
  InboxOutlined,
  SearchOutlined,
  StopOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import {
  Button,
  Empty,
  Input,
  Pagination,
  Segmented,
  Select,
  Skeleton,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { useAssetSearch } from '../../hooks/useAssets'
import { SearchSidebar } from '../../pages/main/search/components/SearchSidebar'
import { DocumentSearchResultSection } from '../documents/components/DocumentSearchResultSection'
import { getAssetFilePreviewUrl } from '../../services/assetService'
import { searchDocuments } from '../../services/documentService'
import type { Asset, AssetFile, AssetSearchParams, AssetSort, AssetStatus, AssetType } from '../../types/asset'
import { scopeLabel } from './assetPresentation'
import { AssetStatusTag, AssetTypeTag } from './AssetTags'

type ViewMode = 'gallery' | 'list'
type DirectoryView = 'all' | 'previewable' | 'pending' | 'standardized' | 'disabled' | 'ningde' | 'liyang' | 'custom'

const SEARCH_STATE_KEY = 'asset-search-state-v1'

interface SavedSearchState {
  query: string
  assetType?: AssetType
  status?: AssetStatus
  platformFamily?: string
  platformVariant?: string
  base?: string
  productionLine?: string
  productLine?: string
  processSection?: string
  specialty?: string
  owner?: string
  format?: string
  updatedFrom?: string
  updatedTo?: string
  missingScope: boolean
  sort: AssetSort
  previewableOnly: boolean
  viewMode: ViewMode
  page: number
  scrollY?: number
}

function readSavedSearchState(): SavedSearchState | null {
  try {
    const raw = sessionStorage.getItem(SEARCH_STATE_KEY)
    return raw ? JSON.parse(raw) as SavedSearchState : null
  } catch {
    return null
  }
}

function persistSearchState(state: SavedSearchState) {
  try {
    sessionStorage.setItem(SEARCH_STATE_KEY, JSON.stringify(state))
  } catch {
    // 隐私模式等场景下忽略保存失败
  }
}

const Page = styled.div`
  min-width: 0;
`

const PageBar = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 44px;
  padding: 0 2px 10px;
`

const PageTitle = styled.h1`
  margin: 0;
  color: #22312b;
  font-size: 18px;
  font-weight: 680;
`

const PageMeta = styled.div`
  margin-top: 2px;
  color: #7c8882;
  font-size: 11px;
`

const SearchBar = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 6px 10px;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-bottom: 0;
  border-radius: 5px 5px 0 0;

  .ant-input-affix-wrapper {
    width: min(620px, 58vw);
  }
`

const SearchHint = styled.span`
  overflow: hidden;
  color: #9aa39f;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Workspace = styled.div`
  display: grid;
  grid-template-columns: 224px minmax(0, 1fr);
  min-height: calc(100vh - 153px);
  overflow: hidden;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-radius: 0 0 5px 5px;

  @media (max-width: 1120px) {
    grid-template-columns: 202px minmax(0, 1fr);
  }
`

const Directory = styled.aside`
  min-width: 0;
  background: #fbfcfb;
  border-right: 1px solid #dfe5e2;
`

const DirectoryHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 13px;
  color: #34443d;
  border-bottom: 1px solid #e5e9e7;
  font-size: 13px;
  font-weight: 650;
`

const DirectoryBody = styled.div`
  max-height: calc(100vh - 202px);
  overflow-y: auto;
  padding: 8px;
`

const DirectoryGroup = styled.div`
  & + & {
    margin-top: 10px;
    padding-top: 8px;
    border-top: 1px solid #edf0ee;
  }
`

const DirectoryLabel = styled.div`
  padding: 4px 8px 6px;
  color: #939d98;
  font-size: 10px;
  font-weight: 650;
`

const DirectoryItem = styled.button<{ $active: boolean }>`
  position: relative;
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding: 0 9px 0 6px;
  color: ${({ $active }) => ($active ? '#245f54' : '#47564f')};
  text-align: left;
  background: ${({ $active }) => ($active ? '#e9f2ef' : 'transparent')};
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  font-weight: ${({ $active }) => ($active ? 650 : 400)};

  &::before {
    position: absolute;
    top: 7px;
    bottom: 7px;
    left: 0;
    width: 3px;
    content: '';
    background: ${({ $active }) => ($active ? '#2f7567' : 'transparent')};
    border-radius: 0 2px 2px 0;
  }

  .anticon {
    justify-self: center;
    color: ${({ $active }) => ($active ? '#2f7567' : '#7b8882')};
  }

  &:hover,
  &:focus-visible {
    background: ${({ $active }) => ($active ? '#e9f2ef' : '#f0f4f2')};
    outline: none;
  }
`

const DirectoryCount = styled.span`
  color: #8a9690;
  font-size: 10px;
`

const MainPanel = styled.section`
  min-width: 0;
  overflow: hidden;
`

const Results = styled.div`
  background: #ffffff;

  .ant-table-row {
    cursor: pointer;
  }
`

const ResultsToolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 7px 12px;
  border-bottom: 1px solid #e5e9e7;
`

const ResultTitle = styled.div`
  color: #2f3e37;
  font-size: 14px;
  font-weight: 650;
`

const ResultCount = styled.span`
  margin-left: 7px;
  color: #84908a;
  font-size: 11px;
  font-weight: 400;
`

const VisualGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  padding: 10px 12px;

  @media (min-width: 1700px) {
    grid-template-columns: repeat(4, minmax(0, 1fr));
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
    box-shadow: 0 5px 16px rgba(36, 62, 52, 0.09);
    outline: none;
    transform: translateY(-2px);
  }
`

const PreviewFrame = styled.div`
  position: relative;
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 16 / 9;
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
  top: 7px;
  left: 7px;
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
  padding: 10px 11px 11px;
`

const CardTitle = styled.div`
  overflow: hidden;
  color: #20332c;
  margin-top: 7px;
  font-size: 13px;
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
  min-height: 18px;
  margin-top: 6px;
  overflow: hidden;
  color: #5e6b65;
  font-size: 12px;
  line-height: 18px;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
`

const CardTime = styled.div`
  margin-top: 4px;
  color: #8b9590;
  font-size: 11px;
`

const CardFooter = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  padding-top: 7px;
  color: #6c7973;
  border-top: 1px solid #edf0ee;
  font-size: 11px;
`

const GalleryEmpty = styled.div`
  padding: 70px 0;
  background: #fff;
`

const PaginationRow = styled.div`
  display: flex;
  justify-content: flex-end;
  padding: 4px 12px 12px;
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

function formatAssetTime(value?: string) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

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
  const savedSearch = useRef(readSavedSearchState()).current
  const urlParams = useMemo(() => new URLSearchParams(window.location.search), [])
  const urlStatus = urlParams.get('status') as AssetStatus | null
  const [query, setQuery] = useState(urlParams.get('q') ?? savedSearch?.query ?? '')
  const [assetType, setAssetType] = useState<AssetType | undefined>(savedSearch?.assetType)
  const [status, setStatus] = useState<AssetStatus | undefined>(urlStatus ?? savedSearch?.status)
  const [platformFamily, setPlatformFamily] = useState<string | undefined>(savedSearch?.platformFamily)
  const [platformVariant, setPlatformVariant] = useState<string | undefined>(savedSearch?.platformVariant)
  const [base, setBase] = useState<string | undefined>(savedSearch?.base)
  const [productionLine, setProductionLine] = useState<string | undefined>(savedSearch?.productionLine)
  const [productLine, setProductLine] = useState<string | undefined>(savedSearch?.productLine)
  const [processSection, setProcessSection] = useState<string | undefined>(savedSearch?.processSection)
  const [specialty, setSpecialty] = useState<string | undefined>(savedSearch?.specialty)
  const [owner, setOwner] = useState<string | undefined>(savedSearch?.owner)
  const [format, setFormat] = useState<string | undefined>(savedSearch?.format)
  const [updatedFrom, setUpdatedFrom] = useState<string | undefined>(savedSearch?.updatedFrom)
  const [updatedTo, setUpdatedTo] = useState<string | undefined>(savedSearch?.updatedTo)
  const [missingScope, setMissingScope] = useState(savedSearch?.missingScope ?? false)
  const [sort, setSort] = useState<AssetSort>(savedSearch?.sort ?? 'RELEVANCE')
  const [previewableOnly, setPreviewableOnly] = useState(savedSearch?.previewableOnly ?? true)
  const [viewMode, setViewMode] = useState<ViewMode>(savedSearch?.viewMode ?? 'gallery')
  const [directoryView, setDirectoryView] = useState<DirectoryView>(savedSearch ? 'custom' : 'previewable')
  const [page, setPage] = useState(savedSearch?.page ?? 1)
  const debouncedQuery = useDebouncedValue(query, 280)

  const params: AssetSearchParams = {
    query: debouncedQuery,
    assetType,
    status,
    platformFamily,
    platformVariant,
    base,
    productionLine,
    productLine,
    processSection,
    specialty,
    owner,
    format,
    updatedFrom,
    updatedTo,
    missingScope,
    sort,
    previewable: previewableOnly,
    page,
    perPage: viewMode === 'gallery' ? 12 : 20,
  }
  const assetsQuery = useAssetSearch(params)
  const documentsQuery = useQuery({
    queryKey: ['document-search-section', debouncedQuery, platformFamily, platformVariant, base, productionLine],
    queryFn: () => searchDocuments({
      query: debouncedQuery,
      category: '',
      platformFamily,
      platformVariant,
      base,
      productionLine,
      page: 1,
      perPage: 8,
    }),
  })

  useEffect(() => setPage(1), [debouncedQuery, assetType, status, platformFamily, platformVariant, base, productionLine, productLine, processSection, specialty, owner, format, updatedFrom, updatedTo, missingScope, sort, previewableOnly, viewMode])

  const openAsset = (id: number) => {
    persistSearchState({
      query, assetType, status, platformFamily, platformVariant, base, productionLine,
      productLine, processSection, specialty, owner, format, updatedFrom, updatedTo,
      missingScope, sort, previewableOnly, viewMode, page,
      scrollY: window.scrollY,
    })
    navigate(`/assets/${id}`)
  }

  useEffect(() => {
    if (!savedSearch?.scrollY) return
    const restored = savedSearch.scrollY
    const timer = window.setTimeout(() => window.scrollTo(0, restored), 0)
    return () => window.clearTimeout(timer)
  }, [savedSearch])

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
        width: 150,
        render: formatAssetTime,
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
    setProductLine(undefined)
    setBase(undefined)
    setProductionLine(undefined)
    setProcessSection(undefined)
    setSpecialty(undefined)
    setOwner(undefined)
    setFormat(undefined)
    setUpdatedFrom(undefined)
    setUpdatedTo(undefined)
    setMissingScope(false)
    setSort('RELEVANCE')
    setPreviewableOnly(false)
    setDirectoryView('all')
  }

  const applyDirectory = (view: DirectoryView) => {
    setDirectoryView(view)
    setPage(1)
    setStatus(view === 'pending' ? 'PENDING_CURATION' : view === 'standardized' ? 'STANDARDIZED' : view === 'disabled' ? 'DISABLED' : undefined)
    setBase(view === 'ningde' ? '宁德基地' : view === 'liyang' ? '溧阳基地' : undefined)
    setPreviewableOnly(view === 'previewable')
  }

  const assets = assetsQuery.data?.data ?? []
  const total = assetsQuery.data?.meta.total ?? 0
  const directoryLabels: Record<DirectoryView, string> = {
    all: '全部资料',
    previewable: '可在线预览',
    pending: '待整理资料',
    standardized: '已标准化资料',
    disabled: '已停用资料',
    ningde: '宁德基地',
    liyang: '溧阳基地',
    custom: '筛选结果',
  }

  return (
    <Page>
      <PageBar>
        <div><PageTitle>资料检索</PageTitle><PageMeta>查找、预览和复用已经应用于生产线的图纸与数模资产</PageMeta></div>
        <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => navigate('/upload')}>上传资料</Button>
      </PageBar>
      <SearchBar>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索资料编号、名称、功能说明或文件名"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          onPressEnter={() => setPage(1)}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={() => setPage(1)}>搜索</Button>
        <SearchHint>支持资料编号精确匹配及名称、说明、文件名模糊搜索</SearchHint>
      </SearchBar>
      <Workspace>
        <Directory aria-label="资料目录">
          <DirectoryHeader><span>资料目录</span><DirectoryCount>{total} 项</DirectoryCount></DirectoryHeader>
          <DirectoryBody>
            <DirectoryGroup>
              <DirectoryLabel>常用视图</DirectoryLabel>
              <DirectoryItem type="button" $active={directoryView === 'all'} onClick={() => applyDirectory('all')}><FolderOpenOutlined /><span>全部资料</span></DirectoryItem>
              <DirectoryItem type="button" $active={directoryView === 'previewable'} onClick={() => applyDirectory('previewable')}><EyeOutlined /><span>可在线预览</span></DirectoryItem>
            </DirectoryGroup>
            <DirectoryGroup>
              <DirectoryLabel>资产状态</DirectoryLabel>
              <DirectoryItem type="button" $active={directoryView === 'pending'} onClick={() => applyDirectory('pending')}><InboxOutlined /><span>待整理</span></DirectoryItem>
              <DirectoryItem type="button" $active={directoryView === 'standardized'} onClick={() => applyDirectory('standardized')}><CheckCircleOutlined /><span>已标准化</span></DirectoryItem>
              <DirectoryItem type="button" $active={directoryView === 'disabled'} onClick={() => applyDirectory('disabled')}><StopOutlined /><span>已停用</span></DirectoryItem>
            </DirectoryGroup>
            <DirectoryGroup>
              <DirectoryLabel>生产范围</DirectoryLabel>
              <DirectoryItem type="button" $active={directoryView === 'ningde'} onClick={() => applyDirectory('ningde')}><EnvironmentOutlined /><span>宁德基地</span></DirectoryItem>
              <DirectoryItem type="button" $active={directoryView === 'liyang'} onClick={() => applyDirectory('liyang')}><EnvironmentOutlined /><span>溧阳基地</span></DirectoryItem>
            </DirectoryGroup>
          </DirectoryBody>
        </Directory>
        <MainPanel>
          <ResultsToolbar>
            <ResultTitle>{directoryLabels[directoryView]}<ResultCount>{total} 项资产</ResultCount></ResultTitle>
            <Space size={12}>
              <Select<AssetSort>
                size="small"
                value={sort}
                onChange={setSort}
                aria-label="排序方式"
                style={{ width: 110 }}
                options={[
                  { value: 'RELEVANCE', label: '相关度' },
                  { value: 'UPDATED_AT', label: '更新时间' },
                  { value: 'ASSET_NUMBER', label: '资料编号' },
                  { value: 'NAME', label: '名称' },
                ]}
              />
              <Space size={7}><Typography.Text type="secondary" style={{ fontSize: 11 }}>仅看可预览</Typography.Text><Switch size="small" checked={previewableOnly} onChange={(value) => { setPreviewableOnly(value); setDirectoryView(value ? 'previewable' : 'custom') }} /></Space>
              <Segmented<ViewMode>
                size="small"
                value={viewMode}
                onChange={setViewMode}
                options={[
                  { value: 'gallery', label: '图像浏览', icon: <AppstoreOutlined /> },
                  { value: 'list', label: '紧凑列表', icon: <UnorderedListOutlined /> },
                ]}
              />
            </Space>
          </ResultsToolbar>
          <SearchSidebar
            assetType={assetType}
            platformFamily={platformFamily}
            platformVariant={platformVariant}
            base={base}
            productionLine={productionLine}
            productLine={productLine}
            processSection={processSection}
            specialty={specialty}
            owner={owner}
            format={format}
            updatedFrom={updatedFrom}
            updatedTo={updatedTo}
            missingScope={missingScope}
            status={status}
            onAssetTypeChange={(value) => { setAssetType(value); setDirectoryView('custom') }}
            onPlatformFamilyChange={(value) => { setPlatformFamily(value); setPlatformVariant(undefined); setProductLine(undefined); setDirectoryView('custom') }}
            onPlatformVariantChange={(value) => { setPlatformVariant(value); setProductLine(undefined); setDirectoryView('custom') }}
            onProductLineChange={(value) => { setProductLine(value); setDirectoryView('custom') }}
            onBaseChange={(value) => { setBase(value); setProductionLine(undefined); setProcessSection(undefined); setDirectoryView('custom') }}
            onProductionLineChange={(value) => { setProductionLine(value); setProcessSection(undefined); setDirectoryView('custom') }}
            onProcessSectionChange={(value) => { setProcessSection(value); setDirectoryView('custom') }}
            onSpecialtyChange={(value) => { setSpecialty(value); setDirectoryView('custom') }}
            onOwnerChange={(value) => { setOwner(value); setDirectoryView('custom') }}
            onFormatChange={(value) => { setFormat(value); setDirectoryView('custom') }}
            onUpdatedFromChange={(value) => { setUpdatedFrom(value); setDirectoryView('custom') }}
            onUpdatedToChange={(value) => { setUpdatedTo(value); setDirectoryView('custom') }}
            onMissingScopeChange={(value) => { setMissingScope(value); setDirectoryView('custom') }}
            onStatusChange={(value) => { setStatus(value); setDirectoryView('custom') }}
            onClear={clearFilters}
          />

          {viewMode === 'gallery' ? (
            assetsQuery.isError ? (
              <GalleryEmpty><Empty description="资产列表加载失败"><Button type="primary" onClick={() => void assetsQuery.refetch()}>重试</Button></Empty></GalleryEmpty>
            ) : assetsQuery.isLoading ? (
              <VisualGrid>{Array.from({ length: 8 }, (_, index) => <AssetCard key={index} type="button"><Skeleton.Node active style={{ width: '100%', height: 168 }} /><CardBody><Skeleton active paragraph={{ rows: 2 }} title={{ width: '70%' }} /></CardBody></AssetCard>)}</VisualGrid>
            ) : assets.length ? (
              <>
                <VisualGrid>
                  {assets.map((asset) => {
                    const file = previewFile(asset)
                    const previewUrl = file ? getAssetFilePreviewUrl(asset.id, file) : undefined
                    const scope = asset.scopes[0]
                    return (
                      <AssetCard key={asset.id} type="button" onClick={() => openAsset(asset.id)} aria-label={`打开 ${asset.name}`}>
                        <PreviewFrame>
                          {previewUrl && file && imageFormats.has(file.format) ? <PreviewImage src={previewUrl} alt={`${asset.name}预览`} loading="lazy" /> : <FormatPreview><FormatIcon>{previewIcon(file)}</FormatIcon><PreviewFileName>{file?.name ?? '暂无预览文件'}</PreviewFileName></FormatPreview>}
                          {file && <PreviewBadge><EyeOutlined />{file.format} 可预览</PreviewBadge>}
                        </PreviewFrame>
                        <CardBody>
                          <Space size={5} wrap><AssetTypeTag type={asset.assetType} />{scope?.platformFamily && <Tag>{scope.platformFamily}{scope.platformVariant ? ` · ${scope.platformVariant}` : ''}</Tag>}</Space>
                          <CardTitle title={asset.name}>{asset.name}</CardTitle>
                          <CardNumber>{asset.assetNumber}</CardNumber>
                          <CardDescription>{asset.description || '暂无功能说明'}</CardDescription>
                          <CardTime>{formatAssetTime(asset.updatedAt)}</CardTime>
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
                  size="small"
                  scroll={{ x: 1120 }}
                  locale={{ emptyText: <Empty description="没有符合条件的数模资产" /> }}
                  pagination={{ current: page, pageSize: params.perPage, total, showSizeChanger: false, onChange: setPage }}
                  onRow={(record) => ({ onClick: () => openAsset(record.id) })}
                />
              )}
            </Results>
          )}
          <DocumentSearchResultSection
            page={documentsQuery.data}
            loading={documentsQuery.isLoading}
            error={documentsQuery.isError}
            onRetry={() => void documentsQuery.refetch()}
            query={query}
          />
        </MainPanel>
      </Workspace>
    </Page>
  )
}
