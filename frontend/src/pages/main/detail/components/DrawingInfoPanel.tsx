import { DatabaseOutlined, LinkOutlined, RightOutlined, UserOutlined } from '@ant-design/icons'
import { Avatar, Tag } from 'antd'
import { Link } from 'react-router-dom'
import styled from 'styled-components'
import { scopeLabel } from '../../../../features/assets/assetPresentation'
import { AssetStatusTag, AssetTypeTag } from '../../../../features/assets/AssetTags'
import type { Asset } from '../../../../types/asset'
import { useEquipmentInterconnections } from '../../../../hooks/useAssets'

const Panel = styled.aside`
  min-width: 0;
  max-height: 477px;
  overflow-y: auto;
  scrollbar-gutter: stable;
  padding: 18px;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const PanelTitle = styled.h2`
  margin: 0 0 12px;
  color: #2a3b34;
  font-size: 14px;
  font-weight: 650;
`

const Tags = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-bottom: 14px;

  .ant-tag {
    margin: 0;
  }
`

const Description = styled.p`
  margin: 0;
  color: #46564f;
  font-size: 13px;
  line-height: 1.7;
`

const Divider = styled.div`
  height: 1px;
  margin: 16px 0;
  background: #e8ece9;
`

const Label = styled.div`
  margin-bottom: 7px;
  color: #85908a;
  font-size: 10px;
  font-weight: 650;
  text-transform: uppercase;
`

const Owner = styled.div`
  display: flex;
  align-items: center;
  gap: 9px;
`

const OwnerName = styled.div`
  color: #2c3d35;
  font-size: 13px;
  font-weight: 650;
`

const OwnerDepartment = styled.div`
  margin-top: 2px;
  color: #7f8a84;
  font-size: 11px;
`

const ScopeList = styled.div`
  display: grid;
  gap: 6px;
`

const Scope = styled.div`
  padding-left: 9px;
  color: #405149;
  border-left: 2px solid #99b9ae;
  font-size: 12px;
  line-height: 1.55;
`

const MetaGrid = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 12px;
`

const MetaValue = styled.div`
  color: #34443d;
  font-size: 12px;
  line-height: 1.55;
`

const ModuleLinks = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 5px;

  a {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: #2f7567;
    font-size: 12px;
  }
`

const LineDataLink = styled.a`
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) auto;
  gap: 9px;
  align-items: center;
  padding: 9px;
  color: inherit;
  background: #f5f8f6;
  border: 1px solid #dfe6e2;
  border-radius: 5px;

  &:hover,
  &:focus-visible {
    background: #edf4f1;
    border-color: #a9c6bc;
    outline: none;
  }
`

const LineIcon = styled.span`
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: #2f7567;
  background: #fff;
  border: 1px solid #d8e2dd;
  border-radius: 4px;
`

const LineName = styled.div`
  overflow: hidden;
  color: #304139;
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const LineScope = styled.div`
  margin-top: 2px;
  overflow: hidden;
  color: #7c8983;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

interface DrawingInfoPanelProps {
  asset: Asset
}

export function DrawingInfoPanel({ asset }: DrawingInfoPanelProps) {
  const moduleTags = asset.moduleTags ?? []
  const linkedModuleAssetIds = asset.linkedModuleAssetIds ?? []
  const interconnectionsQuery = useEquipmentInterconnections(asset.equipmentInterconnectCode)
  const interconnections = interconnectionsQuery.data ?? []

  return (
    <Panel aria-label="资产信息">
      <PanelTitle>资料信息</PanelTitle>
      <Tags>
        <AssetStatusTag status={asset.status} />
        <AssetTypeTag type={asset.assetType} />
        {asset.legacy && <Tag>历史资料</Tag>}
        {asset.specialties.map((item) => <Tag key={item}>{item}</Tag>)}
      </Tags>
      <Description>{asset.description || '功能说明待补充'}</Description>

      <Divider />
      <Label>资料责任</Label>
      <Owner>
        <Avatar size={32} icon={<UserOutlined />} style={{ background: '#e6efeb', color: '#2f7567' }} />
        <div>
          <OwnerName>{asset.ownerName}</OwnerName>
          <OwnerDepartment>{asset.ownerDepartment || '归属部门待补充'}</OwnerDepartment>
        </div>
      </Owner>

      <Divider />
      <Label>适用范围</Label>
      <ScopeList>
        {asset.scopes.length > 0
          ? asset.scopes.map((scope, index) => <Scope key={`${scopeLabel(scope)}-${index}`}>{scopeLabel(scope)}</Scope>)
          : <MetaValue>待补充</MetaValue>}
      </ScopeList>

      {(asset.equipmentInterconnectCode || asset.standardEquipmentModule || moduleTags.length > 0 || linkedModuleAssetIds.length > 0) && (
        <>
          <Divider />
          <MetaGrid>
            {(asset.standardEquipmentModule || moduleTags.length > 0) && (
              <div>
                <Label>模块标注</Label>
                <Tags style={{ marginBottom: 0 }}>
                  {asset.standardEquipmentModule && <Tag color="green">标准设备模块</Tag>}
                  {moduleTags.filter((tag) => tag !== '标准设备模块').map((tag) => <Tag key={tag}>{tag}</Tag>)}
                </Tags>
              </div>
            )}
          </MetaGrid>
          {linkedModuleAssetIds.length > 0 && (
            <div style={{ marginTop: 14 }}>
              <Label>关联模块数模</Label>
              <ModuleLinks>
                {linkedModuleAssetIds.map((id) => <Link key={id} to={`/assets/${id}`}><LinkOutlined />模块资产 #{id}</Link>)}
              </ModuleLinks>
            </div>
          )}
          {asset.equipmentInterconnectCode && (
            <div style={{ marginTop: 14 }}>
              <Label>关联产线设备</Label>
              {interconnectionsQuery.isLoading ? <MetaValue>正在读取产线关联...</MetaValue> : interconnections.length > 0 ? interconnections.map((link) => (
                <LineDataLink key={link.id} href={link.dataReference} target="_blank" rel="noreferrer">
                  <LineIcon><DatabaseOutlined /></LineIcon>
                  <div>
                    <LineName>{link.equipmentName}</LineName>
                    <LineScope>{link.equipmentCode} · {link.base} / {link.productionLine} / {link.processSection}</LineScope>
                  </div>
                  <RightOutlined style={{ color: '#7d8c85', fontSize: 11 }} />
                </LineDataLink>
              )) : <MetaValue>{asset.equipmentInterconnectCode} · 暂无可访问的产线数据</MetaValue>}
            </div>
          )}
        </>
      )}

      {asset.tags.length > 0 && (
        <>
          <Divider />
          <Label>业务标签</Label>
          <Tags style={{ marginBottom: 0 }}>{asset.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}</Tags>
        </>
      )}
    </Panel>
  )
}
