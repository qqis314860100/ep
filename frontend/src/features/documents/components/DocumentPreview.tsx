import { DownloadOutlined, EyeInvisibleOutlined, FileOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import { useEffect, useState } from 'react'
import styled from 'styled-components'
import { getDocumentFileUrl } from '../../../services/documentService'
import type { DocumentFile } from '../../../types/document'
import { formatFileSize } from '../documentPresentation'

const Surface = styled.section`
  position: relative;
  display: grid;
  grid-template-rows: 42px minmax(0, 1fr);
  min-width: 0;
  min-height: 0;
  background: #e9edeb;
`

const Toolbar = styled.div`
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  padding: 0 10px;
  background: #f8faf9;
  border-bottom: 1px solid #dbe2de;
`

const FileName = styled.div`
  flex: 1;
  overflow: hidden;
  color: #46544e;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Frame = styled.iframe`
  width: 100%;
  height: 100%;
  min-height: 0;
  background: #fff;
  border: 0;
`

const Image = styled.img`
  width: 100%;
  height: 100%;
  min-height: 0;
  object-fit: contain;
`

const Fallback = styled.div`
  display: grid;
  place-items: center;
  min-height: 0;
  padding: 32px;
  color: #68756f;
  text-align: center;
`

const FallbackIcon = styled.div`
  margin-bottom: 10px;
  color: #7a8b83;
  font-size: 32px;
`

const FallbackTitle = styled.div`
  color: #3f4d46;
  font-size: 13px;
  font-weight: 650;
`

const FallbackMeta = styled.div`
  margin-top: 5px;
  color: #87918c;
  font-size: 11px;
`

interface DocumentPreviewProps {
  documentId: number
  versionId: number
  file: DocumentFile
}

export function DocumentPreview({ documentId, versionId, file }: DocumentPreviewProps) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [file.id])
  const previewUrl = getDocumentFileUrl(documentId, versionId, file.id, true)
  const downloadUrl = getDocumentFileUrl(documentId, versionId, file.id, false)
  const isImage = ['PNG', 'JPG', 'JPEG', 'TIFF'].includes(file.format)
  const isFramePreview = file.format === 'PDF' || file.format === 'DOCX' || file.format === 'DOC'
  const canPreview = file.previewable && (isFramePreview || isImage)

  return (
    <Surface aria-label="文件预览区" onErrorCapture={() => setFailed(true)}>
      <Toolbar>
        <FileName>{file.name}</FileName>
        <Button
          type="link"
          size="small"
          icon={<DownloadOutlined />}
          href={downloadUrl}
          aria-label="下载文件"
        >下载文件</Button>
      </Toolbar>
      {!failed && canPreview && isFramePreview && (
        <Frame title="文档预览" src={previewUrl} />
      )}
      {!failed && canPreview && isImage && (
        <Image alt="文档预览" src={previewUrl} />
      )}
      {(failed || !canPreview) && (
        <Fallback>
          <div>
            <FallbackIcon>{failed ? <EyeInvisibleOutlined /> : <FileOutlined />}</FallbackIcon>
            <FallbackTitle>{failed ? '预览加载失败' : '暂不支持在线预览'}</FallbackTitle>
            <FallbackMeta>{file.format} · {formatFileSize(file.sizeBytes)} · 可继续下载原文件</FallbackMeta>
          </div>
        </Fallback>
      )}
    </Surface>
  )
}
