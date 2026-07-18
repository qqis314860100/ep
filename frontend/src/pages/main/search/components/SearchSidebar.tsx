import { ClearOutlined } from '@ant-design/icons'
import { Button, Select, Space } from 'antd'
import styled from 'styled-components'
import type { AssetStatus, AssetType } from '../../../../types/asset'
import { assetStatusLabels, assetTypeLabels } from '../../../../features/assets/assetPresentation'

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
  const platformOptions = [
    { value: '乘用车', label: '乘用车', variants: ['大面水冷', '底部水冷'] },
    { value: '商用车', label: '商用车', variants: [] },
    { value: '储能', label: '储能', variants: ['集装箱', '电箱', '电柜'] },
    { value: '模组', label: '模组', variants: [] },
    { value: '圆柱', label: '圆柱', variants: [] },
  ]
  const selectedPlatform = platformOptions.find((item) => item.value === props.platformFamily)
  return (
    <Panel aria-label="筛选条件">
      <Select allowClear placeholder="资产类型" value={props.assetType} onChange={props.onAssetTypeChange} style={{ width: 150 }} options={Object.entries(assetTypeLabels).map(([value, label]) => ({ value, label }))} />
      <Select allowClear placeholder="平台族" value={props.platformFamily} onChange={props.onPlatformFamilyChange} style={{ width: 150 }} options={platformOptions.map(({ value, label }) => ({ value, label }))} />
      <Select allowClear placeholder="平台子类" value={props.platformVariant} onChange={props.onPlatformVariantChange} disabled={!selectedPlatform?.variants.length} style={{ width: 150 }} options={(selectedPlatform?.variants ?? []).map((value) => ({ value, label: value }))} />
      <Select allowClear placeholder="基地" value={props.base} onChange={props.onBaseChange} style={{ width: 150 }} options={['宁德基地', '溧阳基地'].map((value) => ({ value, label: value }))} />
      <Select allowClear placeholder="拉线" value={props.productionLine} onChange={props.onProductionLineChange} style={{ width: 140 }} options={['A 拉线', 'B 拉线'].map((value) => ({ value, label: value }))} />
      <Select allowClear placeholder="资料状态" value={props.status} onChange={props.onStatusChange} style={{ width: 140 }} options={Object.entries(assetStatusLabels).map(([value, label]) => ({ value, label }))} />
      <Space />
      <Button icon={<ClearOutlined />} onClick={props.onClear}>清空</Button>
    </Panel>
  )
}
