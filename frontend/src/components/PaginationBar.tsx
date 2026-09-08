import { Pagination } from 'antd'
import styled from 'styled-components'

/**
 * 全站统一分页条：右下对齐、带总数、快速跳页与每页条数切换。
 * 用法：page/pageSize 由页面状态或 URL 驱动，onChange(page, pageSize) 写回。
 * 总条数不超过一页时不渲染（避免出现无意义的空分页条）。
 */
const defaultPageSizeOptions = [10, 20, 50]

const Row = styled.div`
  display: flex;
  justify-content: flex-end;
  padding: 10px 14px;
  border-top: 1px solid #edf0ee;
`

interface PaginationBarProps {
  page: number
  pageSize: number
  total: number
  /** 可选的每页条数档位，默认 [10, 20, 50] */
  pageSizeOptions?: number[]
  onChange: (page: number, pageSize: number) => void
}

export function PaginationBar({ page, pageSize, total, pageSizeOptions = defaultPageSizeOptions, onChange }: PaginationBarProps) {
  if (total <= pageSize) return null
  return (
    <Row>
      <Pagination
        size="small"
        current={page}
        pageSize={pageSize}
        total={total}
        showSizeChanger
        pageSizeOptions={pageSizeOptions.map(String)}
        showQuickJumper
        showTotal={(value) => `共 ${value} 项`}
        onChange={onChange}
      />
    </Row>
  )
}
