import { ArrowLeftOutlined, LinkOutlined } from '@ant-design/icons'
import { Button, Empty, Select, Space, Spin, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import styled from 'styled-components'
import { AssetStatusTag, AssetTypeTag } from '../../../features/assets/AssetTags'
import { assetStatusLabels } from '../../../features/assets/assetPresentation'
import { getAsset, getRelationGraph } from '../../../services/assetService'
import type { RelationGraphNode } from '../../../services/assetService'
import type { RelationType } from '../../../types/asset'

const Page = styled.div`
  min-width: 0;
`

const Header = styled.header`
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-bottom: 12px;
  padding: 0 2px;
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #21322c;
  font-size: 18px;
  font-weight: 650;
`

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  align-items: center;
  margin-bottom: 10px;

  .ant-select {
    font-size: 12px;
  }
`

const LevelRow = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: stretch;
  margin-bottom: 8px;
`

const LevelLabel = styled.div`
  display: flex;
  align-items: center;
  min-width: 64px;
  color: #85908a;
  font-size: 11px;
  font-weight: 650;
`

const NodeCard = styled.button`
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 168px;
  padding: 8px 10px;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #dfe6e2;
  border-radius: 5px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #f4f8f6;
    border-color: #a9c6bc;
    outline: none;
  }
`

const NodeName = styled.div`
  overflow: hidden;
  color: #26372f;
  font-size: 12px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const NodeNumber = styled.div`
  color: #7a8781;
  font-size: 10px;
  font-family: 'SFMono-Regular', Consolas, monospace;
`

const EdgeLine = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 2px 0 10px 64px;
  color: #6c7973;
  font-size: 11px;
`

const relationTypeOptions = [
  { value: 'CONTAINS', label: '包含' },
  { value: 'REFERENCES', label: '引用' },
  { value: 'MATCHES', label: '配套' },
  { value: 'REPLACES', label: '替代' },
]

export default function RelationBrowserPage() {
  const navigate = useNavigate()
  const params = useParams()
  const assetId = Number(params.id)
  const [depth, setDepth] = useState(2)
  const [typeFilter, setTypeFilter] = useState<RelationType[]>([])
  const [statusFilter, setStatusFilter] = useState<string[]>([])
  const assetQuery = useQuery({ queryKey: ['asset', assetId], queryFn: () => getAsset(assetId), enabled: Number.isFinite(assetId) })
  const graphQuery = useQuery({ queryKey: ['relation-graph', assetId, depth], queryFn: () => getRelationGraph(assetId, depth), enabled: Number.isFinite(assetId) })

  const { levels, edges } = useMemo(() => {
    const graph = graphQuery.data
    if (!graph) return { levels: [], edges: [] }
    const filteredEdges = graph.edges.filter((edge) =>
      (typeFilter.length === 0 || typeFilter.includes(edge.relationType)) &&
      (statusFilter.length === 0 || graph.nodes.some((node) =>
        node.assetId === edge.sourceAssetId && statusFilter.includes(node.status)) || graph.nodes.some((node) =>
        node.assetId === edge.targetAssetId && statusFilter.includes(node.status))),
    )
    const involved = new Set<number>()
    filteredEdges.forEach((edge) => { involved.add(edge.sourceAssetId); involved.add(edge.targetAssetId) })
    involved.add(assetId)
    const visibleNodes = graph.nodes.filter((node) =>
      involved.has(node.assetId) &&
      (statusFilter.length === 0 || statusFilter.includes(node.status)))
    const maxDepth = Math.max(0, ...visibleNodes.map((node) => node.depth))
    const grouped: RelationGraphNode[][] = []
    for (let level = 0; level <= maxDepth; level++) {
      grouped.push(visibleNodes.filter((node) => node.depth === level))
    }
    return { levels: grouped, edges: filteredEdges }
  }, [graphQuery.data, typeFilter, statusFilter, assetId])

  if (assetQuery.isLoading || graphQuery.isLoading) return <Page><Spin style={{ display: 'block', padding: 56 }} /></Page>
  if (!assetQuery.data) return <Page><Empty description="未找到该资产"><Button onClick={() => navigate('/')}>返回检索</Button></Empty></Page>

  return (
    <Page>
      <Header>
        <div>
          <Space size={10}><Button type="text" size="small" icon={<ArrowLeftOutlined />} onClick={() => navigate(`/assets/${assetId}`)}>返回详情</Button></Space>
          <Title>关系浏览 · {assetQuery.data.name}</Title>
          <Typography.Text type="secondary">以当前资料为中心按层展开包含、引用、配套和替代关系</Typography.Text>
        </div>
        <Select<number>
          size="small"
          aria-label="展开层数"
          value={depth}
          onChange={setDepth}
          style={{ width: 96 }}
          options={[{ value: 1, label: '1 层' }, { value: 2, label: '2 层' }, { value: 3, label: '3 层' }]}
        />
      </Header>
      <Toolbar aria-label="关系筛选">
        <Select<RelationType[]>
          mode="multiple"
          allowClear
          placeholder="关系类型"
          value={typeFilter}
          onChange={setTypeFilter}
          style={{ minWidth: 200 }}
          options={relationTypeOptions}
          maxTagCount={2}
        />
        <Select<string[]>
          mode="multiple"
          allowClear
          placeholder="资产状态"
          value={statusFilter}
          onChange={setStatusFilter}
          style={{ minWidth: 180 }}
          options={Object.entries(assetStatusLabels).map(([value, label]) => ({ value, label }))}
          maxTagCount={2}
        />
      </Toolbar>

      {graphQuery.isError ? <Empty description="关系图加载失败"><Button type="primary" onClick={() => void graphQuery.refetch()}>重试</Button></Empty>
        : levels.length === 0 ? <Empty description="暂无关联资料" />
          : levels.map((levelNodes, levelIndex) => (
            <div key={levelIndex}>
              <LevelRow>
                <LevelLabel>{levelIndex === 0 ? '当前资料' : `第 ${levelIndex} 层`}</LevelLabel>
                {levelNodes.map((node) => (
                  <NodeCard key={node.assetId} type="button" onClick={() => navigate(`/assets/${node.assetId}`)} aria-label={`打开 ${node.assetName}`}>
                    <NodeName>{node.assetName}</NodeName>
                    <NodeNumber>{node.assetNumber}</NodeNumber>
                    <Space size={4} wrap><AssetTypeTag type={node.assetType} /><AssetStatusTag status={node.status} /></Space>
                  </NodeCard>
                ))}
              </LevelRow>
              {levelIndex < levels.length - 1 && edges
                .filter((edge) =>
                  levelNodes.some((node) => node.assetId === edge.sourceAssetId)
                  && levels[levelIndex + 1].some((node) => node.assetId === edge.targetAssetId))
                .map((edge) => (
                  <EdgeLine key={edge.id}>
                    <Tag icon={<LinkOutlined />} style={{ margin: 0 }}>{edge.directionLabel}</Tag>
                    <span>{edge.description || '—'}</span>
                  </EdgeLine>
                ))}
            </div>
          ))}
    </Page>
  )
}
