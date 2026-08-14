import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getGovernanceIssues } from '../../../../features/governance/api'
import { useEquipmentInterconnections } from '../../../../hooks/useAssets'
import { DrawingInfoPanel } from './DrawingInfoPanel'

vi.mock('../../../../features/governance/api', () => ({ getGovernanceIssues: vi.fn() }))
vi.mock('../../../../hooks/useAssets', () => ({ useEquipmentInterconnections: vi.fn() }))

const asset = {
  id: 11,
  assetNumber: 'M-2026-0001',
  name: '底部水冷模组',
  description: '电池包底部水冷板',
  assetType: 'MODULE' as const,
  status: 'PENDING_CURATION' as const,
  specialties: ['结构'],
  tags: [],
  moduleTags: [],
  standardEquipmentModule: false,
  linkedModuleAssetIds: [],
  equipmentInterconnectCode: '',
  scopes: [{ platform: '乘用车', platformVariant: '底部水冷', productLine: 'H03', base: '宁德基地', productionLine: 'A 拉线', processSection: '焊接段' }],
  files: [],
  ownerName: '陈工',
  ownerDepartment: '设备工程部',
  updatedAt: '2026-08-01T00:00:00Z',
  legacy: false,
}

function renderPanel() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter><DrawingInfoPanel asset={asset} /></MemoryRouter></QueryClientProvider>)
}

describe('DrawingInfoPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useEquipmentInterconnections).mockReturnValue({ data: [], isLoading: false } as ReturnType<typeof useEquipmentInterconnections>)
  })

  it('lists open issues as supplement requirements', async () => {
    vi.mocked(getGovernanceIssues).mockResolvedValue([
      { id: 9001, assetId: 11, targetField: 'DESCRIPTION', issueType: 'MISSING_REQUIRED_FIELD', targetPath: '/description', originalFactJson: '{}', severity: 'HIGH', blocking: true, status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' },
      { id: 9002, assetId: 11, targetField: 'SCOPE', issueType: 'INVALID_SCOPE', targetPath: '/scopes/0', originalFactJson: '{}', severity: 'MEDIUM', blocking: false, status: 'OPEN', taskId: null, version: 0, createdAt: '2026-08-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z' },
    ])

    renderPanel()

    expect(await screen.findByText('补充要求')).toBeInTheDocument()
    expect(screen.getByText('缺少必填字段')).toBeInTheDocument()
    expect(screen.getByText('适用范围不完整')).toBeInTheDocument()
    expect(screen.getByText('/scopes/0')).toBeInTheDocument()
  })

  it('hides the supplement section when there are no open issues', async () => {
    vi.mocked(getGovernanceIssues).mockResolvedValue([])

    renderPanel()

    expect(await screen.findByText('资料信息')).toBeInTheDocument()
    expect(screen.queryByText('补充要求')).not.toBeInTheDocument()
  })
})
