import type { Asset } from '../../../types/asset'

export interface FavoriteFilters {
  keyword?: string
  base?: string
  line?: string
}

/** 收藏筛选：关键词匹配名称/编号/说明；基地与拉线须在同一适用范围中匹配。 */
export function matchesFavoriteFilters(asset: Asset, filters: FavoriteFilters): boolean {
  const normalized = (filters.keyword ?? '').trim().toLowerCase()
  const matchesQuery = normalized.length === 0
    || asset.name.toLowerCase().includes(normalized)
    || asset.assetNumber.toLowerCase().includes(normalized)
    || asset.description.toLowerCase().includes(normalized)
  const matchesScope = (!filters.base && !filters.line)
    || asset.scopes.some((scope) =>
      (!filters.base || scope.base === filters.base) && (!filters.line || scope.productionLine === filters.line))
  return matchesQuery && matchesScope
}
