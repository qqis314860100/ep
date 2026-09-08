import { DownOutlined, UpOutlined } from '@ant-design/icons'
import { Button } from 'antd'
import type { ReactNode } from 'react'
import styled from 'styled-components'

/**
 * 全站统一的顶部"筛选区"网格容器：任意个子项（AntD 控件 / Form.Item /
 * 自定义 div）按固定列宽自动排布成整齐行列，控件逐一成为 grid 子项。
 * 纯布局组件，不做任何数据获取，也不持有筛选状态。
 * 用法示例：
 *   <FilterGrid>
 *     <Select allowClear placeholder="资产类型" ... />
 *     <Select allowClear placeholder="资料状态" ... />
 *   </FilterGrid>
 * 也可作为 antd Form 内部容器，让 Form.Item（label + 控件）成为 grid 子项：
 *   <Form form={form} onFinish={submit}>
 *     <FilterGrid> <Form.Item label="基地">...</Form.Item> ... </FilterGrid>
 *   </Form>
 */
const Grid = styled.div<{ $columns?: number; $minWidth: number; $gap: string }>`
  display: grid;
  grid-template-columns: ${({ $columns, $minWidth }) =>
    $columns != null
      ? `repeat(${$columns}, minmax(0, 1fr))`
      : `repeat(auto-fill, minmax(min(${$minWidth}px, 100%), 1fr))`};
  gap: ${({ $gap }) => $gap};
  align-items: center;

  /* 子项是 antd Form.Item（label + 控件组合）时去掉其自带纵向 margin，
     行距统一交给 gap，保证各行高度一致 */
  > .ant-form-item {
    margin-bottom: 0;
    margin-right: 0;
  }

  /* 允许控件内容在列内收缩，避免把列撑破 */
  > * {
    min-width: 0;
  }
`

export interface FilterGridProps {
  /** grid 子项：AntD 控件、antd Form.Item 或任意自定义 div */
  children?: ReactNode
  /** 固定列数；缺省时按容器宽度与 minWidth 自动排布 */
  columns?: number
  /** 自动排布时的单列最小宽度（px），默认 200 */
  minWidth?: number
  /** CSS gap，格式 "行距 列距"，默认 "10px 12px" */
  gap?: string
  className?: string
}

export function FilterGrid({ children, columns, minWidth = 200, gap = '10px 12px', className }: FilterGridProps) {
  return (
    <Grid $columns={columns} $minWidth={minWidth} $gap={gap} className={className}>
      {children}
    </Grid>
  )
}

/**
 * 可选的"更多筛选"折叠容器：折叠时只显示一个带已选计数的切换按钮；
 * 展开后内部用 FilterGrid 承载额外筛选子项。
 * 展开/收起状态由页面 props（open/onToggle）持有，组件内不持久化。
 * 通常放在一组常用筛选的 FilterGrid 下方，与该 FilterGrid 使用相同
 * 的默认列宽，视觉上同属一个筛选区。
 */
const MoreGroup = styled.div`
  min-width: 0;
`

const MoreButton = styled(Button)`
  font-size: 12px;
`

export interface MoreFiltersProps {
  /** 折叠时显示的按钮文案，默认"更多筛选" */
  label?: string
  /** 已选中的条件数；大于 0 时在按钮上显示" · 已选 N" */
  count?: number
  /** 是否展开（状态由页面持有） */
  open: boolean
  /** 切换展开/收起（写回页面状态） */
  onToggle: () => void
  /** 展开后展示的额外筛选子项，逐项作为内部 FilterGrid 的子项 */
  children?: ReactNode
  className?: string
}

export function MoreFilters({ label = '更多筛选', count, open, onToggle, children, className }: MoreFiltersProps) {
  return (
    <MoreGroup className={className}>
      <MoreButton
        type="text"
        size="small"
        icon={open ? <UpOutlined /> : <DownOutlined />}
        aria-expanded={open}
        onClick={onToggle}
      >
        {open ? '收起筛选' : `${label}${count ? ` · 已选 ${count}` : ''}`}
      </MoreButton>
      {open && <FilterGrid>{children}</FilterGrid>}
    </MoreGroup>
  )
}
