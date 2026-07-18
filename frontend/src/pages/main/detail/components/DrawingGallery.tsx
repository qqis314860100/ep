import { DownloadOutlined, EyeOutlined, FileOutlined } from '@ant-design/icons'
import { Button, Empty, Space, Tag, Typography } from 'antd'
import styled from 'styled-components'
import type { AssetFile } from '../../../../types/asset'
import { formatBytes } from '../../../../features/assets/assetPresentation'

const Gallery = styled.div`
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 1180px) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
`

const FileCard = styled.article`
  min-width: 0;
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 8px;
`

const Preview = styled.div`
  display: grid;
  place-items: center;
  aspect-ratio: 16 / 9;
  margin-bottom: 12px;
  color: #8d9a93;
  background: #f2f5f2;
  border-radius: 6px;
  font-size: 32px;
`

interface DrawingGalleryProps {
  files: AssetFile[]
  onPreview: (file: AssetFile) => void
  onDownload: (file: AssetFile) => void
}

export function DrawingGallery({ files, onPreview, onDownload }: DrawingGalleryProps) {
  if (files.length === 0) return <Empty description="暂无文件" />
  return (
    <Gallery>
      {files.map((file) => (
        <FileCard key={file.id || file.name}>
          <Preview><FileOutlined /></Preview>
          <Typography.Text strong ellipsis style={{ display: 'block' }}>{file.name}</Typography.Text>
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 12 }}>
            {file.format} · {formatBytes(file.sizeBytes)}
          </Typography.Text>
          <Space wrap size={4} style={{ marginTop: 12 }}>
            <Tag>{file.role}</Tag>
            {file.primary && <Tag color="gold">主文件</Tag>}
          </Space>
          <Space size={4} style={{ marginTop: 12 }}>
            <Button type="link" size="small" icon={<EyeOutlined />} disabled={!file.previewable} onClick={() => onPreview(file)}>预览</Button>
            <Button type="link" size="small" icon={<DownloadOutlined />} onClick={() => onDownload(file)}>下载</Button>
          </Space>
        </FileCard>
      ))}
    </Gallery>
  )
}
