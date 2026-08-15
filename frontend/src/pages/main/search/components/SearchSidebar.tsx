import { ClearOutlined, DownOutlined, UpOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Input, Select, Space, Switch, Typography } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import type { AssetStatus, AssetType } from '../../../../types/asset'
import { assetStatusLabels, assetTypeLabels } from '../../../../features/assets/assetPresentation'
import { getDictionaryItems } from '../../../../services/dictionaryService'

const Panel = styled.aside`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
  min-height: 46px;
  padding: 6px 10px;
  background: #fbfcfb;
  border-bottom: 1px solid #e5e9e7;

  .ant-select {
    font-size: 12px;
  }
`

const MoreRow = styled.div`
  display: contents;
`

const MoreToggle = styled(Button)`
  font-size: 12px;
`

const DateInput = styled.input`
  width: 128px;
  padding: 3px 8px;
  color: #3f4d46;
  font-size: 12px;
  background: #fff;
  border: 1px solid #d9dedb;
  border-radius: 4px;
`

const COLLAPSE_KEY = 'asset-search-more-filters-collapsed'

interface SearchSidebarProps {
  assetType?: AssetType
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
  missingScope?: boolean
  status?: AssetStatus
  onAssetTypeChange: (value: AssetType | undefined) => void
  onPlatformFamilyChange: (value: string | undefined) => void
  onPlatformVariantChange: (value: string | undefined) => void
  onBaseChange: (value: string | undefined) => void
  onProductionLineChange: (value: string | undefined) => void
  onProductLineChange: (value: string | undefined) => void
  onProcessSectionChange: (value: string | undefined) => void
  onSpecialtyChange: (value: string | undefined) => void
  onOwnerChange: (value: string | undefined) => void
  onFormatChange: (value: string | undefined) => void
  onUpdatedFromChange: (value: string | undefined) => void
  onUpdatedToChange: (value: string | undefined) => void
  onMissingScopeChange: (value: boolean) => void
  onStatusChange: (value: AssetStatus | undefined) => void
  onClear: () => void
}

const FILE_FORMATS = ['PDF', 'PNG', 'JPG', 'JPEG', 'TIFF', 'X_T', 'STEP', 'STP', 'DWG', 'DXF', 'DOCX', 'DOC', 'TXT']

function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return true
  }
}

export function SearchSidebar(props: SearchSidebarProps) {
  const [collapsed, setCollapsed] = useState(readCollapsed)
  const dictionaryQuery = useQuery({ queryKey: ['dictionary-items'], queryFn: getDictionaryItems })
  const enabledItems = (dictionaryQuery.data ?? []).filter((item) => item.status === 'ENABLED')
  const platformFamilies = enabledItems.filter((item) => item.category === 'PLATFORM_FAMILY')
  const selectedPlatform = platformFamilies.find((item) => item.name === props.platformFamily)
  const platformVariants = enabledItems.filter((item) => item.category === 'PLATFORM_VARIANT' && item.parentId === selectedPlatform?.id)
  const selectedVariant = platformVariants.find((item) => item.name === props.platformVariant)
  const productLines = enabledItems.filter((item) => item.category === 'PRODUCT_LINE' && item.parentId === selectedVariant?.id)
  const bases = enabledItems.filter((item) => item.category === 'BASE')
  const selectedBase = bases.find((item) => item.name === props.base)
  const productionLines = enabledItems.filter((item) => item.category === 'PRODUCTION_LINE' && item.parentId === selectedBase?.id)
  const selectedLine = productionLines.find((item) => item.name === props.productionLine)
  const processSections = enabledItems.filter((item) => item.category === 'PROCESS_SECTION' && item.parentId === selectedLine?.id)
  const specialties = enabledItems.filter((item) => item.category === 'SPECIALTY')
  const assetTypes = enabledItems.filter((item) => item.category === 'ASSET_TYPE' && item.code in assetTypeLabels)

  const toggleCollapsed = () => {
    setCollapsed((current) => {
      const next = !current
      try {
        localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0')
      } catch {
        // 忽略存储失败
      }
      return next
    })
  }

  const moreSelectedCount = [
    props.productLine, props.processSection, props.specialty, props.owner, props.format,
    props.updatedFrom, props.updatedTo, props.missingScope ? 'missingScope' : undefined,
  ].filter(Boolean).length

  return (
    <Panel aria-label="筛选条件">
      <Select allowClear placeholder="资产类型" value={props.assetType} onChange={props.onAssetTypeChange} style={{ width: 128 }} options={assetTypes.map((item) => ({ value: item.code as AssetType, label: item.name }))} />
      <Select allowClear placeholder="平台族" value={props.platformFamily} onChange={(value) => { props.onPlatformFamilyChange(value); props.onPlatformVariantChange(undefined); props.onProductLineChange(undefined) }} style={{ width: 124 }} options={platformFamilies.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="平台子类" value={props.platformVariant} onChange={(value) => { props.onPlatformVariantChange(value); props.onProductLineChange(undefined) }} disabled={!selectedPlatform} style={{ width: 132 }} options={platformVariants.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="基地" value={props.base} onChange={(value) => { props.onBaseChange(value); props.onProductionLineChange(undefined); props.onProcessSectionChange(undefined) }} style={{ width: 120 }} options={bases.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="拉线" value={props.productionLine} onChange={(value) => { props.onProductionLineChange(value); props.onProcessSectionChange(undefined) }} disabled={!selectedBase} style={{ width: 112 }} options={productionLines.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="专业类别" value={props.specialty} onChange={props.onSpecialtyChange} style={{ width: 116 }} options={specialties.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="资料状态" value={props.status} onChange={props.onStatusChange} style={{ width: 120 }} options={Object.entries(assetStatusLabels).map(([value, label]) => ({ value, label }))} />

      {!collapsed && (
        <MoreRow>
          <Select allowClear placeholder="蓝本" value={props.productLine} onChange={props.onProductLineChange} disabled={!selectedVariant} style={{ width: 110 }} options={productLines.map((item) => ({ value: item.name, label: item.name }))} />
          <Select allowClear placeholder="工序段" value={props.processSection} onChange={props.onProcessSectionChange} disabled={!selectedLine} style={{ width: 110 }} options={processSections.map((item) => ({ value: item.name, label: item.name }))} />
          <Input allowClear placeholder="负责人" value={props.owner} onChange={(event) => props.onOwnerChange(event.target.value || undefined)} style={{ width: 110, fontSize: 12 }} />
          <Select allowClear placeholder="文件格式" value={props.format} onChange={props.onFormatChange} style={{ width: 108 }} options={FILE_FORMATS.map((value) => ({ value, label: value }))} />
          <DateInput aria-label="更新时间起" type="date" value={props.updatedFrom ?? ''} onChange={(event) => props.onUpdatedFromChange(event.target.value || undefined)} />
          <DateInput aria-label="更新时间止" type="date" value={props.updatedTo ?? ''} onChange={(event) => props.onUpdatedToChange(event.target.value || undefined)} />
          <Space size={6}><Typography.Text type="secondary" style={{ fontSize: 11 }}>仅看缺少范围</Typography.Text><Switch size="small" checked={props.missingScope ?? false} onChange={props.onMissingScopeChange} aria-label="仅看缺少标准适用范围的历史资料" /></Space>
        </MoreRow>
      )}

      <MoreToggle type="text" size="small" onClick={toggleCollapsed}>
        {collapsed ? `更多筛选${moreSelectedCount > 0 ? ` · 已选 ${moreSelectedCount}` : ''}` : '收起筛选'}
        {collapsed ? <DownOutlined /> : <UpOutlined />}
      </MoreToggle>
      <Button icon={<ClearOutlined />} onClick={props.onClear}>清空</Button>
    </Panel>
  )
}
