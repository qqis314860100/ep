export interface RecentAssetView {
  assetId: number
  name: string
  assetNumber: string
  at: string
}

const RECENT_VIEWS_KEY = 'recent-asset-views-v1'
const MAX_RECENT_VIEWS = 8

export function recordRecentAssetView(asset: { id: number; name: string; assetNumber: string }) {
  try {
    const current = readRecentAssetViews().filter((view) => view.assetId !== asset.id)
    current.unshift({ assetId: asset.id, name: asset.name, assetNumber: asset.assetNumber, at: new Date().toISOString() })
    sessionStorage.setItem(RECENT_VIEWS_KEY, JSON.stringify(current.slice(0, MAX_RECENT_VIEWS)))
  } catch {
    // 隐私模式等场景下忽略记录失败
  }
}

export function readRecentAssetViews(): RecentAssetView[] {
  try {
    const raw = sessionStorage.getItem(RECENT_VIEWS_KEY)
    return raw ? JSON.parse(raw) as RecentAssetView[] : []
  } catch {
    return []
  }
}
