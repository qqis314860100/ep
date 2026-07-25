import { DownloadOutlined, EyeOutlined, FileOutlined } from '@ant-design/icons'
import { Button, Empty, Tag, Tooltip } from 'antd'
import { useEffect, useState } from 'react'
import styled from 'styled-components'
import { formatBytes } from '../../../../features/assets/assetPresentation'
import { FileTypeIcon } from '../../../../features/assets/AssetTags'
import { getAssetFilePreviewUrl } from '../../../../services/assetService'
import type { AssetFile } from '../../../../types/asset'

const Workbench = styled.section`
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  min-height: 472px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;

`

const Viewer = styled.div`
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  min-width: 0;
  background: #eef2f0;
`

const ViewerToolbar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 48px;
  padding: 6px 9px 6px 13px;
  background: #fff;
  border-bottom: 1px solid #dce3df;
`

const ViewerTitle = styled.div`
  min-width: 0;
`

const FileName = styled.div`
  overflow: hidden;
  color: #25362f;
  font-size: 13px;
  font-weight: 650;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const FileMeta = styled.div`
  margin-top: 2px;
  color: #7b8781;
  font-size: 11px;
`

const Actions = styled.div`
  display: flex;
  flex: 0 0 auto;
  gap: 4px;
`

const Stage = styled.div`
  position: relative;
  display: grid;
  place-items: center;
  min-height: 424px;
  overflow: hidden;
  background-color: #edf1ef;
  background-image:
    linear-gradient(#dfe6e2 1px, transparent 1px),
    linear-gradient(90deg, #dfe6e2 1px, transparent 1px);
  background-size: 28px 28px;
`

const PreviewImage = styled.img`
  width: 100%;
  height: 100%;
  max-height: 472px;
  padding: 18px;
  object-fit: contain;
`

const FileStage = styled.div`
  display: grid;
  justify-items: center;
  max-width: 360px;
  padding: 30px;
  color: #35544a;
  text-align: center;

  .anticon {
    font-size: 54px;
  }
`

const StageFormat = styled.div`
  margin-top: 14px;
  color: #243a32;
  font-size: 18px;
  font-weight: 700;
`

const StageHint = styled.div`
  margin-top: 5px;
  color: #73817a;
  font-size: 12px;
  line-height: 1.6;
`

const Rail = styled.aside`
  min-width: 0;
  background: #fbfcfb;
  border-right: 1px solid #dce3df;
`

const RailHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 14px;
  color: #405149;
  border-bottom: 1px solid #dce3df;
  font-size: 12px;
  font-weight: 650;
`

const FileList = styled.div`
  max-height: 424px;
  overflow-y: auto;
  padding: 6px;
`

const FileRow = styled.button<{ $active: boolean }>`
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 9px;
  width: 100%;
  padding: 8px;
  color: inherit;
  text-align: left;
  background: ${({ $active }) => ($active ? '#e7f0ed' : 'transparent')};
  border: 1px solid ${({ $active }) => ($active ? '#aec9c0' : 'transparent')};
  border-radius: 5px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #edf3f0;
    outline: none;
  }
`

const Format = styled.div`
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  color: #2f7567;
  background: #fff;
  border: 1px solid #d5ded9;
  border-radius: 4px;
  font-size: 16px;
`

const RowBody = styled.div`
  min-width: 0;
`

const RowName = styled.div`
  overflow: hidden;
  color: #34443d;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const RowMeta = styled.div`
  margin-top: 3px;
  color: #87918c;
  font-size: 10px;
`

interface DrawingGalleryProps {
  assetId: number
  files: AssetFile[]
  onPreview: (file: AssetFile) => void
  onDownload: (file: AssetFile) => void
}

const imageFormats = new Set(['PNG', 'JPG', 'JPEG', 'TIFF', 'WEBP'])

export function DrawingGallery({ assetId, files, onPreview, onDownload }: DrawingGalleryProps) {
  const preferredIndex = Math.max(0, files.findIndex((file) => file.previewable || file.primary))
  const [selectedIndex, setSelectedIndex] = useState(preferredIndex)

  useEffect(() => {
    if (!files[selectedIndex]) setSelectedIndex(preferredIndex)
  }, [files, preferredIndex, selectedIndex])

  if (files.length === 0) return <Empty description="暂无文件" />

  const selected = files[selectedIndex] ?? files[0]
  const previewUrl = getAssetFilePreviewUrl(assetId, selected)
  const isImage = imageFormats.has(selected.format)

  return (
    <Workbench aria-label="图纸与数模文件工作台">
      <Rail>
        <RailHeader><span>资产文件</span><span>{files.length}</span></RailHeader>
        <FileList>
          {files.map((file, index) => (
            <FileRow
              key={`${file.id}-${file.name}-${index}`}
              type="button"
              $active={index === selectedIndex}
              aria-pressed={index === selectedIndex}
              onClick={() => setSelectedIndex(index)}
            >
              <Format>{file.format ? <FileTypeIcon format={file.format} /> : <FileOutlined />}</Format>
              <RowBody>
                <RowName title={file.name}>{file.name}</RowName>
                <RowMeta>{file.format} · {formatBytes(file.sizeBytes)}{file.primary ? ' · 主文件' : ''}</RowMeta>
              </RowBody>
            </FileRow>
          ))}
        </FileList>
      </Rail>

      <Viewer>
        <ViewerToolbar>
          <ViewerTitle>
            <FileName title={selected.name}>{selected.name}</FileName>
            <FileMeta>{selected.role} · {selected.format} · {formatBytes(selected.sizeBytes)}</FileMeta>
          </ViewerTitle>
          <Actions>
            <Tooltip title={selected.previewable ? '打开在线预览' : '该格式暂不支持在线预览'}>
              <Button icon={<EyeOutlined />} disabled={!selected.previewable} onClick={() => onPreview(selected)}>预览</Button>
            </Tooltip>
            <Tooltip title="下载源文件">
              <Button icon={<DownloadOutlined />} onClick={() => onDownload(selected)} aria-label={`下载 ${selected.name}`} />
            </Tooltip>
          </Actions>
        </ViewerToolbar>
        <Stage>
          {previewUrl && isImage ? (
            <PreviewImage src={previewUrl} alt={`${selected.name}预览`} />
          ) : (
            <FileStage>
              <FileTypeIcon format={selected.format} />
              <StageFormat>{selected.format}</StageFormat>
              <StageHint>
                {selected.previewable ? '此文件支持在线预览，点击上方“预览”在独立窗口查看完整内容。' : '该源文件暂不支持网页解析，可下载后使用专业软件打开。'}
              </StageHint>
              {selected.primary && <Tag color="gold" style={{ marginTop: 12 }}>主文件</Tag>}
            </FileStage>
          )}
        </Stage>
      </Viewer>
    </Workbench>
  )
}
