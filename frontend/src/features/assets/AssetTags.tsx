import { FileImageOutlined, FilePdfOutlined, FileUnknownOutlined } from '@ant-design/icons'
import { Tag } from 'antd'
import type { AssetStatus, AssetType } from '../../types/asset'
import { assetStatusLabels, assetTypeLabels } from './assetPresentation'

export function AssetStatusTag({ status }: { status: AssetStatus }) {
  const colors: Record<AssetStatus, string> = {
    DRAFT: 'default',
    PENDING_CURATION: 'gold',
    STANDARDIZED: 'green',
    DISABLED: 'red',
  }
  return <Tag color={colors[status]}>{assetStatusLabels[status]}</Tag>
}

export function AssetTypeTag({ type }: { type: AssetType }) {
  const colors: Record<AssetType, string> = {
    THREE_DIMENSIONAL_MODEL: 'cyan',
    TWO_DIMENSIONAL_DRAWING: 'geekblue',
    MIXED_ASSET: 'volcano',
    OTHER: 'default',
  }
  return <Tag color={colors[type]}>{assetTypeLabels[type]}</Tag>
}

export function FileTypeIcon({ format }: { format: string }) {
  if (format === 'PDF') return <FilePdfOutlined />
  if (['PNG', 'JPG', 'JPEG', 'TIFF'].includes(format)) return <FileImageOutlined />
  return <FileUnknownOutlined />
}
