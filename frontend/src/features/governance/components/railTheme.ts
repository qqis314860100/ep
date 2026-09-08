/**
 * 治理轨道 v1 视觉 token（企业浅色）。
 * 取值严格对齐 docs/plans/2026-09-08-governance-visual-design.md §1 与
 * /tmp/ep-governance-rail-prototype.html 小样（卡片白 / 描边 #e6e9ef / 圆角 14 / 克制阴影）。
 */
export const railTheme = {
  bg: '#f5f6f8',
  card: '#ffffff',
  line: '#e6e9ef',
  text: '#1b2230',
  text2: '#5b6573',
  text3: '#98a1ad',
  brand: '#2f7567',
  brandDeep: '#23584d',
  brandWeak: '#e7f2ef',
  blue: '#3d6fe0',
  blueWeak: '#ecf1fc',
  red: '#d64545',
  redWeak: '#fbebea',
  amber: '#c98a12',
  amberWeak: '#faf2e1',
  green: '#2e9e6b',
  greenWeak: '#e5f5ec',
  radius: 14,
  radiusSmall: 9,
  shadow: '0 1px 2px rgba(20, 30, 50, 0.05), 0 6px 18px -8px rgba(20, 30, 50, 0.12)',
} as const

/** 责任人头像示意调色板（按出现顺序取色） */
export const railAvatarPalette = ['#3d6fe0', '#2f7567', '#c98a12', '#8a67b8'] as const

/** 取中文姓名的首字符作为头像示意（如"王工"→"王"） */
export function railAvatarChar(name: string): string {
  return (name ?? '').trim().charAt(0) || '?'
}
