import { EyeOutlined, FileTextOutlined } from '@ant-design/icons'
import { Empty, Tag } from 'antd'
import styled from 'styled-components'
import type { DictionaryItem } from '../../../types/dictionary'
import type { KnowledgeDocument } from '../../../types/document'
import { documentCategoryName, formatDocumentTime } from '../documentPresentation'

const Header = styled.div`
  display: grid;
  grid-template-columns: minmax(280px, 1.6fr) minmax(180px, 0.8fr) 118px 136px;
  gap: 16px;
  min-height: 36px;
  padding: 0 14px;
  color: #7d8983;
  background: #f5f7f6;
  border-bottom: 1px solid #e2e7e4;
  font-size: 11px;
  align-items: center;

  @media (max-width: 1180px) {
    grid-template-columns: minmax(260px, 1.5fr) 150px 110px;
    span:nth-child(2) { display: none; }
  }
`

const Row = styled.button`
  display: grid;
  grid-template-columns: minmax(280px, 1.6fr) minmax(180px, 0.8fr) 118px 136px;
  align-items: center;
  gap: 16px;
  width: 100%;
  min-height: 78px;
  padding: 9px 14px;
  color: inherit;
  text-align: left;
  background: #fff;
  border: 0;
  border-bottom: 1px solid #edf0ee;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #f4f8f6;
    outline: none;
  }

  @media (max-width: 1180px) {
    grid-template-columns: minmax(260px, 1.5fr) 150px 110px;
    > div:nth-child(2) { display: none; }
  }
`

const Primary = styled.div`
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 10px;
  min-width: 0;
`

const FileMark = styled.span`
  display: grid;
  place-items: center;
  width: 30px;
  height: 36px;
  color: #2f7567;
  background: #e8f1ee;
  border: 1px solid #cfdfd9;
  border-radius: 3px;
  font-size: 16px;
`

const Title = styled.div`
  overflow: hidden;
  color: #26342e;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Summary = styled.div`
  display: -webkit-box;
  margin-top: 4px;
  overflow: hidden;
  color: #77827d;
  font-size: 11px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
`

const Number = styled.div`
  margin-top: 3px;
  color: #919a96;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
`

const Cell = styled.div`
  min-width: 0;
  color: #4f5d57;
  font-size: 11px;
`

const Secondary = styled.div`
  margin-top: 4px;
  overflow: hidden;
  color: #8b9590;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Preview = styled.span`
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 6px;
  color: #3578a8;
  font-size: 10px;
`

const EmptyArea = styled.div`
  display: grid;
  place-items: center;
  min-height: 360px;
`

interface DocumentResultListProps {
  documents: KnowledgeDocument[]
  categories: DictionaryItem[]
  onOpen: (id: number) => void
}

export function DocumentResultList({ documents, categories, onOpen }: DocumentResultListProps) {
  if (documents.length === 0) {
    return <EmptyArea><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="未找到符合条件的已发布文档" /></EmptyArea>
  }
  return (
    <div>
      <Header aria-hidden="true">
        <span>文档</span><span>分类 / 当前版本</span><span>维护人</span><span>更新时间</span>
      </Header>
      {documents.map((document) => {
        const previewable = document.currentVersion.files.some((file) => file.previewable)
        return (
          <Row key={document.id} type="button" onClick={() => onOpen(document.id)}>
            <Primary>
              <FileMark><FileTextOutlined /></FileMark>
              <div>
                <Title>{document.title}</Title>
                <Summary>{document.summary}</Summary>
                <Number>{document.documentNumber}</Number>
              </div>
            </Primary>
            <Cell>
              <Tag color="green">{documentCategoryName(document.categoryCode, categories)}</Tag>
              <Secondary>
                {document.currentVersion.versionNumber}
                {previewable && <Preview><EyeOutlined />可预览</Preview>}
              </Secondary>
            </Cell>
            <Cell>
              {document.maintainerName}
              <Secondary>{document.maintainerDepartment || '-'}</Secondary>
            </Cell>
            <Cell>{formatDocumentTime(document.updatedAt)}</Cell>
          </Row>
        )
      })}
    </div>
  )
}
