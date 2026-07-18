import { Descriptions, Tag } from 'antd'
import styled from 'styled-components'
import type { Asset } from '../../../../types/asset'
import { scopeLabel } from '../../../../features/assets/assetPresentation'
import { AssetStatusTag, AssetTypeTag } from '../../../../features/assets/AssetTags'

const Panel = styled.section`
  padding: 22px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 8px;
`

interface DrawingInfoPanelProps {
  asset: Asset
}

export function DrawingInfoPanel({ asset }: DrawingInfoPanelProps) {
  const moduleTags = asset.moduleTags ?? []
  const linkedModuleAssetIds = asset.linkedModuleAssetIds ?? []
  const equipmentInterconnectCode = asset.equipmentInterconnectCode ?? ''
  return (
    <Panel>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
        <AssetStatusTag status={asset.status} />
        <AssetTypeTag type={asset.assetType} />
        {asset.legacy && <Tag>历史资料</Tag>}
        {asset.specialties.map((item) => <Tag key={item}>{item}</Tag>)}
      </div>
      <Descriptions column={2} size="small">
        <Descriptions.Item label="功能说明" span={2}>{asset.description}</Descriptions.Item>
        <Descriptions.Item label="负责人">{asset.ownerName}</Descriptions.Item>
        <Descriptions.Item label="归属部门">{asset.ownerDepartment || '待补充'}</Descriptions.Item>
        <Descriptions.Item label="适用范围" span={2}>{asset.scopes.map((scope, index) => <div key={`${scopeLabel(scope)}-${index}`}>{scopeLabel(scope)}</div>)}</Descriptions.Item>
        <Descriptions.Item label="模块标注" span={2}>
          {asset.standardEquipmentModule ? <Tag color="green">标准设备模块</Tag> : <Tag>普通资产</Tag>}
          {moduleTags.filter((tag) => tag !== '标准设备模块').map((tag) => <Tag key={tag}>{tag}</Tag>)}
        </Descriptions.Item>
        <Descriptions.Item label="设备互联">{equipmentInterconnectCode || '待补充'}</Descriptions.Item>
        <Descriptions.Item label="关联模块数模">{linkedModuleAssetIds.length ? linkedModuleAssetIds.map((id) => <Tag key={id} color="blue"><a href={`/assets/${id}`}>模块资产 #{id}</a></Tag>) : '暂无关联'}</Descriptions.Item>
        <Descriptions.Item label="标签" span={2}>{asset.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</Descriptions.Item>
      </Descriptions>
    </Panel>
  )
}
