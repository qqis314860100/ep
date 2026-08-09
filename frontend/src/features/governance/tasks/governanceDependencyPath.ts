export function buildDependencyPath(x1: number, y1: number, x2: number, y2: number): string {
  const handle = Math.max(18, Math.min(48, Math.abs(x2 - x1) / 2))
  return `M${x1},${y1} C${x1 + handle},${y1} ${x2 - handle},${y2} ${x2},${y2}`
}
