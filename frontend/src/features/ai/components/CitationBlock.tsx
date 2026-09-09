import { DownOutlined, FileTextOutlined } from '@ant-design/icons'
import { Button, Tag, Typography } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import type { ChatCitation } from '../../../types/ai'

const { Text } = Typography

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
  gap: 6px;
  margin-top: 8px;
`

const Row = styled.div`
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 8px 10px;
  background: #fafcfb;
  border: 1px solid #e6e8ea;
  border-radius: 8px;

  &:hover {
    border-color: #cfe0d9;
  }
`

const Index = styled.span`
  flex: none;
  min-width: 20px;
  height: 20px;
  margin-top: 1px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e7f0ec;
  color: #2f7567;
  font-size: 12px;
`

const Excerpt = styled.div`
  font-size: 12px;
  color: #7a8a92;
  line-height: 1.6;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
`

interface CitationBlockProps {
  citations: ChatCitation[]
}

/** 引用卡：折叠的「来源引用 N 处」摘要条 + 可展开的逐条引用（位置/摘录/站内跳转）。 */
export default function CitationBlock({ citations }: CitationBlockProps) {
  const [open, setOpen] = useState(false)
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
            const jumpable = Number.isFinite(Number.parseInt(citation.docId, 10))
            return (
              <Row key={`${citation.docId}-${index}`}>
                <Index>{index + 1}</Index>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Text style={{ fontSize: 13, color: '#26322d' }}>
                    {citation.location || citation.docId}
                  </Text>
                  {citation.excerpt && <Excerpt>{citation.excerpt}</Excerpt>}
                  {citation.inScope === false && (
                    <div style={{ marginTop: 4 }}>
                      <Tag style={{ fontSize: 11, lineHeight: '18px' }}>范围外</Tag>
                    </div>
                  )}
                </div>
                {jumpable && (
                  <Button size="small" type="link" icon={<FileTextOutlined />}
                    aria-label={`查看引用 ${index + 1}`}
                    onClick={() => openDoc(citation.docId)}>
                    查看
                  </Button>
                )}
              </Row>
            )
          })}
        </RowList>
      )}
    </div>
  )
}
