import type { EChartsCoreOption } from 'echarts/core'
import { railTheme } from './railTheme'

/**
 * 治理图表统一主题与选项构造器。
 *
 * 设计取舍：治理页的图表只回答"量级与构成"两类问题，因此不外露 ECharts
 * 细节，页面只提供分类与数值，坐标轴/网格/提示/图例的样式在这里统一，
 * 保证各页图表观感一致。
 */

export const CHART_PALETTE = [
  railTheme.brand,
  railTheme.blue,
  railTheme.amber,
  '#8a67b8',
  railTheme.red,
  railTheme.green,
  '#5b6573',
] as const

const axisText = { color: railTheme.text3, fontSize: 11 }
const labelText = { color: railTheme.text2, fontSize: 12 }

const splitLine = { lineStyle: { color: '#eef1ef' } }

export interface ChartSeries {
  name: string
  data: number[]
  color?: string
}

/** 按某个字段汇总计数，降序返回（供环形/条形图直接使用）。 */
export function countByValue<T>(items: T[], key: (item: T) => string): Array<{ name: string; value: number }> {
  const counts = new Map<string, number>()
  for (const item of items) {
    const name = key(item)
    counts.set(name, (counts.get(name) ?? 0) + 1)
  }
  return [...counts.entries()]
    .map(([name, value]) => ({ name, value }))
    .sort((a, b) => b.value - a.value)
}

/** 分类条形/柱状：单个系列为普通条形，多系列自动堆叠。横向时分类在 Y 轴。 */
export function categoryBarOption(opts: {
  categories: string[]
  series: ChartSeries[]
  horizontal?: boolean
  max?: number
  unit?: string
  /** 数值标签（默认不显示；横向单系列默认显示在条末） */
  showLabel?: boolean
}): EChartsCoreOption {
  const { categories, series, horizontal = false, max, unit = '', showLabel } = opts
  const stacked = series.length > 1
  const labelVisible = showLabel ?? (horizontal && !stacked)
  const categoryAxis = {
    type: 'category' as const,
    data: categories,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { ...labelText },
  }
  const numberAxis = {
    type: 'value' as const,
    ...(max === undefined ? {} : { max }),
    // 非百分比口径按"条数"理解，避免出现 0.5 个这类无意义刻度
    ...(unit === '%' ? {} : { minInterval: 1 }),
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { ...axisText, formatter: unit ? `{value}${unit}` : '{value}' },
    splitLine,
  }
  return {
    color: series.map((item, index) => item.color ?? CHART_PALETTE[index % CHART_PALETTE.length]),
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      valueFormatter: (value: number) => `${value}${unit}`,
    },
    ...(stacked ? { legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { ...labelText } } } : {}),
    grid: {
      left: 8,
      right: horizontal && labelVisible ? 46 : 16,
      top: 10,
      bottom: stacked ? 28 : 8,
      containLabel: true,
    },
    ...(horizontal
      ? { xAxis: numberAxis, yAxis: { ...categoryAxis, inverse: true } }
      : { xAxis: categoryAxis, yAxis: numberAxis }),
    series: series.map(item => ({
      name: item.name,
      type: 'bar',
      ...(stacked ? { stack: 'total' } : {}),
      barWidth: horizontal ? 12 : stacked ? 18 : 22,
      itemStyle: {
        borderRadius: horizontal && !stacked ? [0, 4, 4, 0] : stacked ? 0 : [4, 4, 0, 0],
      },
      label: labelVisible
        ? { show: true, position: 'right', formatter: `{c}${unit}`, ...axisText }
        : { show: false },
      data: item.data,
    })),
  }
}

/** 环形构成图：用于"分类占比"，图例在下方。 */
export function donutOption(opts: {
  data: Array<{ name: string; value: number }>
  colors?: readonly string[]
  /** 环形中心文案（如总计） */
  centerText?: { value: string; label?: string }
}): EChartsCoreOption {
  const { data, colors = CHART_PALETTE, centerText } = opts
  return {
    color: [...colors],
    tooltip: { trigger: 'item', formatter: '{b}：{c}（{d}%）' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { ...labelText } },
    series: [
      {
        type: 'pie',
        radius: ['54%', '74%'],
        center: ['50%', centerText ? '44%' : '42%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: centerText
          ? {
            show: true,
            position: 'center',
            formatter: `{v|${centerText.value}}\n{l|${centerText.label ?? ''}}`,
            rich: {
              v: { color: railTheme.text, fontSize: 20, fontWeight: 700, lineHeight: 26 },
              l: { color: railTheme.text3, fontSize: 11 },
            },
          }
          : { show: false },
        labelLine: { show: false },
        data,
      },
    ],
  }
}
