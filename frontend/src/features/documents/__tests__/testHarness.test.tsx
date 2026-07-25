import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

describe('document test harness', () => {
  it('renders the test environment', () => {
    render(<button type="button">新建文档</button>)

    expect(screen.getByRole('button', { name: '新建文档' })).toBeInTheDocument()
  })
})
