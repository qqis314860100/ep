import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createAssetRelation, searchAssets, updateAssetRelation } from '../../../../services/assetService'
import { AssetRelationDialog } from './AssetRelationDialog'

vi.mock('../../../../services/assetService', async (importOriginal) => ({
  ...await importOriginal<typeof import('../../../../services/assetService')>(),
  searchAssets: vi.fn(),
  createAssetRelation: vi.fn(),
  updateAssetRelation: vi.fn(),
}))

const target = {
  id: 104,
  assetNumber: 'LEGACY-00000104',
  name: 'XM-PL01 设备图',
  description: '历史设备图',
  assetType: 'TWO_DIMENSIONAL_DRAWING' as const,
  status: 'PENDING_CURATION' as const,
  specialties: ['机械'],
  tags: [],
  moduleTags: [],
  standardEquipmentModule: false,
  linkedModuleAssetIds: [],
  equipmentInterconnectCode: '',
  scopes: [{ platform: '乘用车', platformVariant: '底部水冷', productLine: '', base: '', productionLine: 'XM-PL01', processSection: '' }],
  files: [],
  ownerName: '赵工',
  ownerDepartment: '设备工程部',
  updatedAt: '2026-08-01T00:00:00Z',
  legacy: true,
}

const relation = {
  id: 9,
  sourceAssetId: 102,
  targetAssetId: 104,
  targetAssetNumber: 'LEGACY-00000104',
  targetAssetName: 'XM-PL01 设备图',
  targetAssetType: 'TWO_DIMENSIONAL_DRAWING' as const,
  targetAssetStatus: 'PENDING_CURATION' as const,
  relationType: 'REFERENCES' as const,
  directionLabel: '引用',
  primaryScope: '乘用车 / XM-PL01',
  description: '定位工装引用历史设备图',
  createdBy: 'emp-chen',
  createdAt: '2026-08-01T00:00:00Z',
  updatedBy: 'emp-chen',
  updatedAt: '2026-08-01T00:00:00Z',
  version: 1,
}

function renderDialog(props: Partial<Parameters<typeof AssetRelationDialog>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const saved = vi.fn()
  const closed = vi.fn()
  render(
    <QueryClientProvider client={client}>
      <AssetRelationDialog open assetId={102} relation={undefined} onClose={closed} onSaved={saved} {...props} />
    </QueryClientProvider>,
  )
  return { saved, closed }
}

describe('AssetRelationDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(searchAssets).mockResolvedValue({ data: [target], meta: { total: 1, page: 1, perPage: 8, totalPages: 1 } })
    vi.mocked(createAssetRelation).mockResolvedValue(relation)
    vi.mocked(updateAssetRelation).mockResolvedValue({ ...relation, version: 2 })
  })

  it('creates a relation with target, type, and description', async () => {
    const user = userEvent.setup()
    renderDialog()

    const targetItem = (await screen.findByText('目标资料')).closest('.ant-form-item')!
    await user.click(targetItem.querySelector('.ant-select-selector')!)
    const targetCombo = within(targetItem).getByRole('combobox')
    await user.click(targetCombo)
    await user.type(targetCombo, '设备图')
    await user.click(await screen.findByText((text) => text.includes('XM-PL01')))

    const typeItem = screen.getByText('关系类型').closest('.ant-form-item')!
    await user.click(typeItem.querySelector('.ant-select-selector')!)
    await user.click(await screen.findByText('包含'))

    await user.type(screen.getByPlaceholderText('关系说明（可选）'), '引用说明')
    await user.click(screen.getByRole('button', { name: '确认建立' }))

    await waitFor(() => expect(createAssetRelation).toHaveBeenCalledWith(102, {
      targetAssetId: 104,
      relationType: 'CONTAINS',
      description: '引用说明',
    }))
  })

  it('submits an update with version and current direction in edit mode', async () => {
    const user = userEvent.setup()
    renderDialog({ relation })

    expect(await screen.findByText('修改资产关系')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '保存修改' }))

    await waitFor(() => expect(updateAssetRelation).toHaveBeenCalledWith(102, 9, {
      sourceAssetId: 102,
      targetAssetId: 104,
      relationType: 'REFERENCES',
      description: '定位工装引用历史设备图',
      version: 1,
    }))
  })
})
