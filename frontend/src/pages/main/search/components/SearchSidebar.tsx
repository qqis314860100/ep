import { ClearOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Button, Select, Space } from 'antd'
import styled from 'styled-components'
import type { AssetStatus, AssetType } from '../../../../types/asset'
import { assetStatusLabels, assetTypeLabels } from '../../../../features/assets/assetPresentation'
import { getDictionaryItems } from '../../../../services/dictionaryService'

const Panel = styled.aside`
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px 16px;
  background: #fff;
  border: 1px solid #e5e8e3;
  border-radius: 8px;
`

interface SearchSidebarProps {
  assetType?: AssetType
  platformFamily?: string
  platformVariant?: string
  base?: string
  productionLine?: string
  status?: AssetStatus
  onAssetTypeChange: (value: AssetType | undefined) => void
  onPlatformFamilyChange: (value: string | undefined) => void
  onPlatformVariantChange: (value: string | undefined) => void
  onBaseChange: (value: string | undefined) => void
  onProductionLineChange: (value: string | undefined) => void
  onStatusChange: (value: AssetStatus | undefined) => void
  onClear: () => void
}

export function SearchSidebar(props: SearchSidebarProps) {
  const dictionaryQuery = useQuery({ queryKey: ['dictionary-items'], queryFn: getDictionaryItems })
  const enabledItems = (dictionaryQuery.data ?? []).filter((item) => item.status === 'ENABLED')
  const platformFamilies = enabledItems.filter((item) => item.category === 'PLATFORM_FAMILY')
  const selectedPlatform = platformFamilies.find((item) => item.name === props.platformFamily)
  const platformVariants = enabledItems.filter((item) => item.category === 'PLATFORM_VARIANT' && item.parentId === selectedPlatform?.id)
  const bases = enabledItems.filter((item) => item.category === 'BASE')
  const selectedBase = bases.find((item) => item.name === props.base)
  const productionLines = enabledItems.filter((item) => item.category === 'PRODUCTION_LINE' && item.parentId === selectedBase?.id)
  const assetTypes = enabledItems.filter((item) => item.category === 'ASSET_TYPE' && item.code in assetTypeLabels)
  return (
    <Panel aria-label="筛选条件">
      <Select allowClear placeholder="资产类型" value={props.assetType} onChange={props.onAssetTypeChange} style={{ width: 150 }} options={assetTypes.map((item) => ({ value: item.code as AssetType, label: item.name }))} />
      <Select allowClear placeholder="平台族" value={props.platformFamily} onChange={(value) => { props.onPlatformFamilyChange(value); props.onPlatformVariantChange(undefined) }} style={{ width: 150 }} options={platformFamilies.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="平台子类" value={props.platformVariant} onChange={props.onPlatformVariantChange} disabled={!selectedPlatform} style={{ width: 150 }} options={platformVariants.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="基地" value={props.base} onChange={(value) => { props.onBaseChange(value); props.onProductionLineChange(undefined) }} style={{ width: 150 }} options={bases.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="拉线" value={props.productionLine} onChange={props.onProductionLineChange} disabled={!selectedBase} style={{ width: 140 }} options={productionLines.map((item) => ({ value: item.name, label: item.name }))} />
      <Select allowClear placeholder="资料状态" value={props.status} onChange={props.onStatusChange} style={{ width: 140 }} options={Object.entries(assetStatusLabels).map(([value, label]) => ({ value, label }))} />
      <Space />
      <Button icon={<ClearOutlined />} onClick={props.onClear}>清空</Button>
    </Panel>
  )
}
