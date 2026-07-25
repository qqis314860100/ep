import { DeleteOutlined, FileImageOutlined, FileOutlined, FilePdfOutlined } from '@ant-design/icons'
import { Button, Tooltip } from 'antd'
import styled from 'styled-components'
import type { DocumentFile } from '../../../types/document'
import { formatFileSize } from '../documentPresentation'

const List = styled.div`
  margin-top: 8px;
  border: 1px solid #e2e7e4;
  border-radius: 4px;
`

const Row = styled.div`
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr) 76px 34px;
  align-items: center;
  min-height: 44px;
  padding: 4px 6px;

  & + & { border-top: 1px solid #edf0ee; }
`

const Mark = styled.span`
  color: #2f7567;
  text-align: center;
  font-size: 16px;
`

const Name = styled.div`
  overflow: hidden;
  color: #34423c;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Meta = styled.div`
  margin-top: 2px;
  color: #8b9691;
  font-size: 10px;
`

function fileIcon(file: DocumentFile) {
  if (file.format === 'PDF') return <FilePdfOutlined />
  if (['PNG', 'JPG', 'JPEG', 'TIFF'].includes(file.format)) return <FileImageOutlined />
  return <FileOutlined />
}

interface DocumentFileListProps {
  files: DocumentFile[]
  disabled?: boolean
  onRemove: (index: number) => void
}

export function DocumentFileList({ files, disabled, onRemove }: DocumentFileListProps) {
  if (files.length === 0) return null
  return (
    <List>
      {files.map((file, index) => (
        <Row key={`${file.storageKey ?? file.name}-${index}`}>
          <Mark>{fileIcon(file)}</Mark>
          <div>
            <Name>{file.name}</Name>
            <Meta>{file.format} · {formatFileSize(file.sizeBytes)}{file.previewable ? ' · 可预览' : ''}</Meta>
          </div>
          <Meta>{file.contentSha256 ? file.contentSha256.slice(0, 8) : '校验中'}</Meta>
          <Tooltip title="移除文件">
            <Button
              type="text"
              danger
              disabled={disabled}
              icon={<DeleteOutlined />}
              aria-label={`移除 ${file.name}`}
              onClick={() => onRemove(index)}
            />
          </Tooltip>
        </Row>
      ))}
    </List>
  )
}
