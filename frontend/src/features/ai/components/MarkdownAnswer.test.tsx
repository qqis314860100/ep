import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import MarkdownAnswer from './MarkdownAnswer'

describe('MarkdownAnswer（GFM 渲染）', () => {
  it('渲染 GFM 表格为 <table> 而非纯文本', () => {
    const { container } = render(
      <MarkdownAnswer content={'| 项目 | 标准 |\n|---|---|\n| 漏率 | ≤1mL/min |'} />,
    )
    expect(container.querySelector('table')).not.toBeNull()
    expect(container.querySelectorAll('th').length).toBeGreaterThan(0)
    expect(screen.getByText('≤1mL/min')).toBeInTheDocument()
  })

  it('渲染标题与加粗', () => {
    const { container } = render(<MarkdownAnswer content={'## 标题\n\n**加粗词**'} />)
    expect(container.querySelector('h2')).not.toBeNull()
    expect(container.querySelector('strong')?.textContent).toBe('加粗词')
  })
})
