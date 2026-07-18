import { Button, Empty, Typography } from 'antd'
import styled from 'styled-components'
import type { Asset, AssetRelation } from '../../types/asset'
import { AssetStatusTag, AssetTypeTag } from './AssetTags'

const Canvas = styled.div`
  position: relative;
  width: 100%;
  min-height: 300px;
  aspect-ratio: 16 / 7;
  overflow: hidden;
  background: #f7f9f7;
  border: 1px solid #dfe5e1;
  border-radius: 6px;
`

const Lines = styled.svg`
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
`

const Node = styled.button<{ $center?: boolean }>`
  position: absolute;
  width: ${({ $center }) => ($center ? '196px' : '178px')};
  min-height: ${({ $center }) => ($center ? '92px' : '82px')};
  padding: 12px;
  text-align: left;
  color: #202824;
  background: ${({ $center }) => ($center ? '#e5f0ec' : '#ffffff')};
  border: 1px solid ${({ $center }) => ($center ? '#75a99a' : '#ccd6d0')};
  border-radius: 6px;
  transform: translate(-50%, -50%);
  cursor: pointer;
  box-shadow: 0 6px 18px rgb(29 48 42 / 7%);

  &:hover,
  &:focus-visible {
    border-color: #2f7567;
    outline: 2px solid rgb(47 117 103 / 18%);
  }
`

const NodeNumber = styled.div`
  margin-bottom: 4px;
  color: #66736e;
  font-size: 11px;
`

const NodeTitle = styled.div`
  margin-bottom: 7px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const relationPositions = [
  { x: 20, y: 26 },
  { x: 80, y: 26 },
  { x: 20, y: 76 },
  { x: 80, y: 76 },
]

interface AssetRelationMapProps {
  asset: Asset
  relations: AssetRelation[]
}

export function AssetRelationMap({ asset, relations }: AssetRelationMapProps) {
  if (relations.length === 0) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无关联资产" />
  }

  return (
    <Canvas aria-label="资产关系图">
      <Lines role="img" aria-label={`当前资产关联 ${relations.length} 个资产`}>
        {relations.slice(0, 4).map((relation, index) => {
          const position = relationPositions[index]
          const labelX = (50 + position.x) / 2
          const labelY = (50 + position.y) / 2
          return (
            <g key={relation.id}>
              <line
                x1="50%"
                y1="50%"
                x2={`${position.x}%`}
                y2={`${position.y}%`}
                stroke="#8fa39b"
                strokeWidth="1.4"
              />
              <rect
                x={`${labelX - 5}%`}
                y={`${labelY - 3}%`}
                width="10%"
                height="6%"
                rx="3"
                fill="#f7f9f7"
              />
              <text
                x={`${labelX}%`}
                y={`${labelY + 0.8}%`}
                textAnchor="middle"
                fontSize="11"
                fill="#50625b"
              >
                {relation.directionLabel}
              </text>
            </g>
          )
        })}
      </Lines>

      <Node $center style={{ left: '50%', top: '50%' }} type="button">
        <NodeNumber>{asset.assetNumber}</NodeNumber>
        <NodeTitle>{asset.name}</NodeTitle>
        <AssetTypeTag type={asset.assetType} />
      </Node>

      {relations.slice(0, 4).map((relation, index) => {
        const position = relationPositions[index]
        return (
          <Node
            key={relation.id}
            style={{ left: `${position.x}%`, top: `${position.y}%` }}
            type="button"
          >
            <NodeNumber>{relation.targetAssetNumber}</NodeNumber>
            <NodeTitle>{relation.targetAssetName}</NodeTitle>
            <AssetStatusTag status={relation.targetAssetStatus} />
          </Node>
        )
      })}

      {relations.length > 4 && (
        <Button size="small" style={{ position: 'absolute', right: 12, bottom: 12 }}>
          查看全部 {relations.length} 项
        </Button>
      )}
      <Typography.Text className="sr-only">
        当前资产为 {asset.name}
      </Typography.Text>
    </Canvas>
  )
}
