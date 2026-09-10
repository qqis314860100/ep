import { BarChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { SVGRenderer } from 'echarts/renderers'
import { useEffect, useRef, type CSSProperties } from 'react'
import styled from 'styled-components'

/**
 * 治理页图表统一封装：按需注册 ECharts 图表/组件（bar、pie + 提示/图例/网格），
 * 用 SVG 渲染器保证清晰度与可测试性；容器尺寸变化自动 resize，卸载时销毁实例。
 */

echarts.use([BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, SVGRenderer])

const ChartBox = styled.div`
  width: 100%;
  min-width: 0;
`

export interface EChartProps {
  option: echarts.EChartsCoreOption
  height?: number
  ariaLabel?: string
  style?: CSSProperties
}

export function EChart({ option, height = 260, ariaLabel, style }: EChartProps) {
  const nodeRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)
  const optionRef = useRef(option)
  optionRef.current = option

  useEffect(() => {
    const node = nodeRef.current
    if (!node) return
    let disposed = false

    const render = () => {
      if (disposed) return
      if (!chartRef.current) {
        // 容器尚未测量出尺寸（如 jsdom 或隐藏容器）时跳过实例化，避免空尺寸报错
        if (!node.clientWidth || !node.clientHeight) return
        try {
          chartRef.current = echarts.init(node, undefined, { renderer: 'svg' })
        } catch {
          return
        }
      }
      chartRef.current.setOption(optionRef.current, true)
      chartRef.current.resize()
    }

    render()
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(render)
    observer?.observe(node)
    return () => {
      disposed = true
      observer?.disconnect()
      chartRef.current?.dispose()
      chartRef.current = null
    }
  }, [])

  useEffect(() => {
    chartRef.current?.setOption(option, true)
  }, [option])

  return <ChartBox ref={nodeRef} role="img" aria-label={ariaLabel} style={{ height, ...style }} />
}
