import { DownOutlined, FileTextOutlined, UpOutlined } from '@ant-design/icons'
import { Button, Tag, Typography } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import type { ChatCitation } from '../../../types/ai'
import MarkdownAnswer from './MarkdownAnswer'

const { Text } = Typography

/** 摘录超过该长度时折叠展示，可展开全文。 */
const LONG_EXCERPT = 200

const Summary = styled.button`
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 3px 10px;
  border: 1px solid #d7e4df;
  border-radius: 999px;
  background: #f2f8f5;
  color: #2f7567;
  font-size: 12px;
  cursor: pointer;

  &:hover {
    background: #e6f2ec;
  }
`

const Chevron = styled(DownOutlined)<{ $open: boolean }>`
  font-size: 10px;
  transition: transform 0.2s;
  transform: ${({ $open }) => ($open ? 'rotate(180deg)' : 'rotate(0deg)')};
`

const RowList = styled.div`
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
`

const Row = styled.div`
  padding: 8px 10px;
  background: #fafcfb;
  border: 1px solid #e6e8ea;
  border-radius: 8px;

  &:hover {
    border-color: #cfe0d9;
  }
`

const RowHead = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
`

const Index = styled.span`
  flex: none;
  min-width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e7f0ec;
  color: #2f7567;
  font-size: 12px;
`

const Location = styled(Text)`
  flex: 1;
  min-width: 0;
  color: #26322d;
  font-size: 13px;
`

const Clip = styled.div<{ $clipped: boolean }>`
  position: relative;
  margin-top: 6px;
  overflow: hidden;
  max-height: ${({ $clipped }) => ($clipped ? '150px' : 'none')};
`

const Fade = styled.div`
  position: absolute;
  inset-inline: 0;
  bottom: 0;
  height: 42px;
  background: linear-gradient(to top, #fafcfb, rgba(250, 252, 251, 0));
  pointer-events: none;
`

const ExpandRow = styled.div`
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
`

const Meta = styled.span`
  font-size: 11px;
  color: #a5b2b8;
`

interface CitationBlockProps {
  citations: ChatCitation[]
}

/** 引用卡：折叠的「来源引用 N 处」摘要条 + 可展开的逐条引用；摘录以 markdown 渲染，长片段可展开全文。 */
export default function CitationBlock({ citations }: CitationBlockProps) {
  const [open, setOpen] = useState(false)
  const [expandedRows, setExpandedRows] = useState<Record<number, boolean>>({})
  if (citations.length === 0) return null

  const openDoc = (docId: string) => {
    const assetId = Number.parseInt(docId, 10)
    if (!Number.isFinite(assetId)) return
    window.open(`/assets/${assetId}`, '_blank', 'noopener,noreferrer')
  }

  return (
    <div>
      <Summary type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)}>
        <FileTextOutlined />
        来源引用 {citations.length} 处
        <Chevron $open={open} />
      </Summary>
      {open && (
        <RowList aria-label="引用列表">
          {citations.map((citation, index) => {
            const excerpt = citation.excerpt || ''
            const isLong = excerpt.length > LONG_EXCERPT
            const expanded = Boolean(expandedRows[index])
            const jumpable = Number.isFinite(Number.parseInt(citation.docId, 10))
            return (
              <Row key={`${citation.docId}-${index}`}>
                <RowHead>
                  <Index>{index + 1}</Index>
                  <Location ellipsis={{ tooltip: true }}>
                    {citation.location || citation.docId}
                  </Location>
                  {citation.inScope === false && <Tag style={{ fontSize: 11, lineHeight: '18px' }}>范围外</Tag>}
                  {jumpable && (
                    <Button size="small" type="link" icon={<FileTextOutlined />}
                      aria-label={`查看引用 ${index + 1}`}
                      onClick={() => openDoc(citation.docId)}>
                      查看
                    </Button>
                  )}
                </RowHead>
                {excerpt && (
                  <Clip $clipped={isLong && !expanded}>
                    <MarkdownAnswer content={excerpt} compact />
                    {isLong && !expanded && <Fade />}
                  </Clip>
                )}
                {isLong && (
                  <ExpandRow>
                    <Meta>{excerpt.length} 字符</Meta>
                    <Button type="link" size="small" style={{ padding: 0 }}
                      icon={expanded ? <UpOutlined /> : <DownOutlined />}
                      onClick={() => setExpandedRows((previous) => ({ ...previous, [index]: !expanded }))}>
                      {expanded ? '收起' : '展开全文'}
                    </Button>
                  </ExpandRow>
                )}
              </Row>
            )
          })}
        </RowList>
      )}
    </div>
  )
}
