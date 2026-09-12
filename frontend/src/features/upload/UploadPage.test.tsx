import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { App as AntdApp } from 'antd'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  saveAssetDraft,
  submitAsset,
  updateAssetDraft,
  uploadAssetFile,
} from '../../services/assetService'
import { getDictionaryItems } from '../../services/dictionaryService'
import type { Asset } from '../../types/asset'
import { AuthContext } from '../auth/AuthContextValue'
import { UploadPage } from './UploadPage'

vi.mock('../../services/assetService', () => ({
  saveAssetDraft: vi.fn(),
  saveAssetDraftsBatch: vi.fn(),
  submitAsset: vi.fn(),
  updateAssetDraft: vi.fn(),
  uploadAssetFile: vi.fn(),
}))
vi.mock('../../services/dictionaryService', () => ({ getDictionaryItems: vi.fn() }))

const dictionaryItem = (id: number, category: string, code: string, name: string, parentId?: number) => ({
  id, category, code, name, parentId, status: 'ENABLED' as const, sortOrder: id, usageCount: 0,
  version: 1, directional: false, allowDuplicate: false, updatedAt: '2026-09-11T00:00:00Z',
})

const draft = {
  id: 106,
  assetNumber: 'E2E-UPLOAD-001',
  name: 'E2E 草稿',
  description: '首次保存',
  assetType: 'MIXED_ASSET',
  status: 'DRAFT',
  specialties: ['机械'],
  tags: [],
  moduleTags: [],
  standardEquipmentModule: false,
  linkedModuleAssetIds: [],
  equipmentInterconnectCode: '',
  scopes: [{ platform: '乘用车', platformFamily: '乘用车', platformVariant: '底部水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '' }],
  files: [{ id: 9, name: 'model.step', format: 'STEP', sizeBytes: 5, role: '三维源模型', previewable: false, primary: true, storageKey: 'e2e/model.step', contentSha256: 'abc' }],
  ownerName: '系统管理员',
  ownerDepartment: '信息化部',
  updatedAt: '2026-09-11T00:00:00Z',
  legacy: false,
} satisfies Asset

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <AntdApp>
        <AuthContext.Provider value={{
          user: { userId: 'admin', name: '系统管理员', department: '信息化部', roles: ['SYSTEM_ADMIN', 'CONTENT_ADMIN'] },
          ready: true,
          login: vi.fn(),
          logout: vi.fn(),
        }}>
          <MemoryRouter><UploadPage /></MemoryRouter>
        </AuthContext.Provider>
      </AntdApp>
    </QueryClientProvider>,
  )
}

async function choose(user: ReturnType<typeof userEvent.setup>, label: string, option: string) {
  await user.click(screen.getByRole('combobox', { name: label }))
  await user.click(await screen.findByTitle(option))
}

describe('UploadPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getDictionaryItems).mockResolvedValue([
      dictionaryItem(1, 'ASSET_TYPE', 'MIXED_ASSET', '混合资产'),
      dictionaryItem(2, 'FILE_ROLE', 'SOURCE_3D', '三维源模型'),
      dictionaryItem(3, 'SPECIALTY', 'MECHANICAL', '机械'),
      dictionaryItem(10, 'PLATFORM_FAMILY', 'PASSENGER', '乘用车'),
      dictionaryItem(11, 'PLATFORM_VARIANT', 'BOTTOM_COOLING', '底部水冷', 10),
      dictionaryItem(12, 'PRODUCT_LINE', 'H03', 'H03', 11),
      dictionaryItem(20, 'BASE', 'NINGDE', '宁德基地'),
      dictionaryItem(21, 'PRODUCTION_LINE', 'LINE_A', 'A 拉线', 20),
    ])
    vi.mocked(uploadAssetFile).mockResolvedValue(draft.files[0])
    vi.mocked(saveAssetDraft).mockResolvedValue(draft)
    vi.mocked(updateAssetDraft).mockResolvedValue({ ...draft, description: '提交前最新说明' })
    vi.mocked(submitAsset).mockResolvedValue({ ...draft, description: '提交前最新说明', status: 'PENDING_CURATION' })
  })

  it('persists the latest form values before submitting an existing draft', async () => {
    const user = userEvent.setup()
    const view = renderPage()

    await user.type(screen.getByLabelText('资料编号'), 'E2E-UPLOAD-001')
    await user.type(screen.getByLabelText('资料名称'), 'E2E 草稿')
    await user.type(screen.getByLabelText('功能说明'), '首次保存')
    await choose(user, '专业类别', '机械')
    await choose(user, '覆盖平台', '乘用车')
    await choose(user, '平台子类', '底部水冷')
    await choose(user, '蓝本', 'H03')
    await choose(user, '基地', '宁德基地')
    await choose(user, '拉线', 'A 拉线')

    const fileInput = view.container.querySelector<HTMLInputElement>('input[type="file"]')
    expect(fileInput).not.toBeNull()
    await user.upload(fileInput!, new File(['model'], 'model.step', { type: 'application/octet-stream' }))
    await user.click(screen.getByRole('button', { name: '保存草稿' }))
    await waitFor(() => expect(saveAssetDraft).toHaveBeenCalledTimes(1))

    await user.clear(screen.getByLabelText('功能说明'))
    await user.type(screen.getByLabelText('功能说明'), '提交前最新说明')
    await user.click(screen.getByRole('button', { name: /提交待整理/ }))

    await waitFor(() => expect(updateAssetDraft).toHaveBeenCalledWith(106, expect.objectContaining({
      description: '提交前最新说明',
      specialties: ['机械'],
      scopes: [expect.objectContaining({ productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线' })],
    })))
    expect(submitAsset).toHaveBeenCalledWith(106)
    expect(vi.mocked(updateAssetDraft).mock.invocationCallOrder[0])
      .toBeLessThan(vi.mocked(submitAsset).mock.invocationCallOrder[0])
  }, 15_000)

  it('allows selecting a single JPEG while keeping a separate folder picker', async () => {
    const user = userEvent.setup()
    const view = renderPage()
    const fileInputs = view.container.querySelectorAll<HTMLInputElement>('input[type="file"]')

    expect(fileInputs).toHaveLength(2)
    expect(fileInputs[0]).not.toHaveAttribute('webkitdirectory')
    expect(fileInputs[0].accept).toContain('.jpeg')
    expect(fileInputs[1]).toHaveAttribute('webkitdirectory')

    await user.upload(fileInputs[0], new File(['jpeg'], 'images1.jpeg', { type: 'image/jpeg' }))
    expect(screen.getByText('images1.jpeg')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '保存草稿' }))
    await waitFor(() => expect(uploadAssetFile).toHaveBeenCalledWith(expect.objectContaining({ name: 'images1.jpeg' })))
  })
})
