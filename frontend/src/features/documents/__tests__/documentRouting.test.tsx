import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import App from '../../../App'

vi.mock('../DocumentSearchPage', () => ({
  default: () => <h1>文档中心</h1>,
}))

describe('document routing', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/documents')
  })

  it('opens the document workspace from primary navigation', async () => {
    const scrollTo = vi.spyOn(window, 'scrollTo').mockImplementation(() => undefined)
    render(<App />)

    expect(await screen.findByRole('heading', { name: '文档中心' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '文档中心' })).toHaveAttribute('aria-current', 'page')
    expect(scrollTo).toHaveBeenCalledWith(0, 0)
  })
})
