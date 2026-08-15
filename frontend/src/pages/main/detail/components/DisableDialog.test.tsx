import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DisableDialog } from './DisableDialog'

describe('DisableDialog', () => {
  it('requires a reason before submitting', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<DisableDialog open assetName="输送模块布置数模" onClose={() => {}} onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: '确认停用' }))
    expect(onSubmit).not.toHaveBeenCalled()
    expect(await screen.findByText('请填写停用原因')).toBeInTheDocument()
  })

  it('submits the trimmed reason and shows server errors', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockRejectedValue(new Error('仅内容管理员或系统管理员可以停用资料'))
    render(<DisableDialog open assetName="输送模块布置数模" onClose={() => {}} onSubmit={onSubmit} />)

    await user.type(screen.getByPlaceholderText('例如：该产线已停产，资料不再使用'), '  该产线已停产  ')
    await user.click(screen.getByRole('button', { name: '确认停用' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('该产线已停产'))
    expect(await screen.findByText('仅内容管理员或系统管理员可以停用资料')).toBeInTheDocument()
  })
})
