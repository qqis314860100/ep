import { FileTextOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Empty, Skeleton, Tag } from 'antd'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import type { DocumentPage } from '../../../types/document'

const Section = styled.section`
  margin: 12px;
  border: 1px solid #dfe5e2;
  border-radius: 5px;
  background: #fff;
`
const Header = styled.div`
  display:flex; align-items:center; justify-content:space-between; min-height:44px; padding:0 12px; border-bottom:1px solid #e5e9e7;
  strong { color:#2f3e37; font-size:13px; } small { margin-left:6px; color:#84908a; font-size:11px; font-weight:400; }
`
const Row = styled.button`
  display:grid; grid-template-columns:28px minmax(0,1fr) auto; gap:10px; align-items:center; width:100%; min-height:58px; padding:9px 12px; text-align:left; background:#fff; border:0; border-bottom:1px solid #edf0ee; cursor:pointer;
  &:last-child { border-bottom:0; } &:hover { background:#f6faf8; }
`
const Title = styled.div`overflow:hidden; color:#314139; font-size:12px; font-weight:650; text-overflow:ellipsis; white-space:nowrap;`
const Meta = styled.div`margin-top:4px; overflow:hidden; color:#7c8882; font-size:10px; text-overflow:ellipsis; white-space:nowrap;`

interface Props { page?: DocumentPage; loading: boolean; error: boolean; onRetry: () => void; query: string }

export function DocumentSearchResultSection({ page, loading, error, onRetry, query }: Props) {
  const navigate = useNavigate()
  const documents = page?.data ?? []
  return <Section aria-label="知识文档检索结果">
    <Header><strong>知识文档 <small>{page?.meta.total ?? 0} 项</small></strong><Button type="link" size="small" onClick={() => navigate(`/documents?q=${encodeURIComponent(query)}`)}>进入文档中心</Button></Header>
    {loading ? <Skeleton active paragraph={{ rows: 2 }} style={{ padding: 12 }} /> : error ? <Empty description="文档结果加载失败" style={{ margin: 18 }}><Button onClick={onRetry}>重试</Button></Empty>
      : documents.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有匹配的知识文档" style={{ margin: 16 }} />
        : documents.map((document) => <Row key={document.id} type="button" onClick={() => navigate(`/documents/${document.id}`)}>
          <FileTextOutlined style={{ color:'#427a6d' }} /><div><Title>{document.title}</Title><Meta>{document.documentNumber} · 当前版本 {document.currentVersion.versionNumber} · {document.maintainerName}</Meta></div>
          <div><Tag color={document.scopeMode === 'GLOBAL' ? 'blue' : document.scopeMode === 'SPECIFIED' ? 'green' : 'gold'}>{document.scopeMode === 'GLOBAL' ? '全局通用' : document.scopeMode === 'SPECIFIED' ? '指定范围' : '范围待补充'}</Tag><RightOutlined style={{ color:'#88948f', fontSize:10 }} /></div>
        </Row>)}
  </Section>
}
