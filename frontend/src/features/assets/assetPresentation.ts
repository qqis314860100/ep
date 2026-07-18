import type { AssetScope, AssetStatus, AssetType } from '../../types/asset'

export const assetTypeLabels: Record<AssetType, string> = {
  THREE_DIMENSIONAL_MODEL: '三维模型',
  TWO_DIMENSIONAL_DRAWING: '二维图纸',
  MIXED_ASSET: '混合资产',
  OTHER: '其他资料',
}

export const assetStatusLabels: Record<AssetStatus, string> = {
  DRAFT: '草稿',
  PENDING_CURATION: '待整理',
  STANDARDIZED: '已标准化',
  DISABLED: '已停用',
}

export function formatBytes(bytes: number) {
  if (bytes <= 0) return '未知'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  return `${(bytes / 1024 ** index).toFixed(index > 1 ? 1 : 0)} ${units[index]}`
}

export function scopeLabel(scope: AssetScope) {
  const platform = scope.platformVariant
    ? `${scope.platformFamily ?? scope.platform} · ${scope.platformVariant}`
    : scope.platformFamily ?? scope.platform
  return [platform, scope.base, scope.productionLine, scope.processSection].filter(Boolean).join(' / ') || '范围待整理'
}
