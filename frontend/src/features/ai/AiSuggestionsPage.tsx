import { LinkOutlined, ReloadOutlined } from '@ant-design/icons'
import { Button, Popover, Select, Space, Table, Tag, Typography, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useEffect, useState } from 'react'
import styled from 'styled-components'
import type { AiSuggestion, AiSuggestionStatus, AiSuggestionTargetType } from '../../types/ai'
import { currentActor } from '../auth/session'
import {
  AiApiError,
  confirmSuggestion,
  listSuggestions,
  regenerateSuggestion,
  rejectSuggestion,
} from './api'

const { Text } = Typography

const Page = styled.div`
  background: #fff;
  border: 1px solid #e6e8ea;
  border-radius: 8px;
  padding: 14px 16px;
`

const Filters = styled.div`
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
`

const statusColor: Record<AiSuggestionStatus, string> = {
  PENDING: 'gold',
  CONFIRMED: 'green',
  REJECTED: 'red',
  SUPERSEDED: 'default',
}

const statusLabel: Record<AiSuggestionStatus, string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  SUPERSEDED: '已作废',
}

const targetLabel: Record<AiSuggestionTargetType, string> = {
  ASSET: '资产',
  KNOWLEDGE_DOC: '文档',
}

interface SuggestionRow extends AiSuggestion {
  key: number
}

function targetPath(row: SuggestionRow): string {
  return row.targetType === 'ASSET' ? `/assets/${row.targetId}` : `/documents/${row.targetId}`
}

export default function AiSuggestionsPage() {
  const [rows, setRows] = useState<SuggestionRow[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [perPage, setPerPage] = useState(10)
  const [status, setStatus] = useState<AiSuggestionStatus | undefined>(undefined)
  const [targetType, setTargetType] = useState<AiSuggestionTargetType | undefined>(undefined)
  const [loading, setLoading] = useState(false)
  const [actingId, setActingId] = useState<number | null>(null)

  const actor = currentActor()
  const canCurate = actor.roles.split(',').some((role) => role === 'CONTENT_ADMIN' || role === 'SYSTEM_ADMIN')

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listSuggestions({ targetType, status, page, perPage })
      setRows(result.data.map((item) => ({ ...item, key: item.id })))
      setTotal(result.meta.total)
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '建议清单加载失败')
    } finally {
      setLoading(false)
    }
  }, [targetType, status, page, perPage])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const act = async (row: SuggestionRow, action: 'confirm' | 'reject' | 'regenerate') => {
    setActingId(row.id)
    try {
      if (action === 'confirm') await confirmSuggestion(row.id)
      else if (action === 'reject') await rejectSuggestion(row.id)
      else await regenerateSuggestion(row.targetType, row.targetId)
      message.success(action === 'confirm' ? '建议已确认并生效' : action === 'reject' ? '建议已驳回' : '已重新整理')
      await refresh()
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '操作失败')
    } finally {
      setActingId(null)
    }
  }

  const columns: ColumnsType<SuggestionRow> = [
    {
      title: '目标',
      dataIndex: 'targetTitle',
      width: 200,
      render: (title: string, row) => (
        <Space direction="vertical" size={0}>
          <Tag>{targetLabel[row.targetType]}</Tag>
          <Text ellipsis style={{ maxWidth: 220 }}>
            <a href={targetPath(row)}>{title}</a>
          </Text>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: AiSuggestionStatus) => <Tag color={statusColor[value]}>{statusLabel[value]}</Tag>,
    },
    {
      title: 'AI 建议',
      width: 320,
      render: (_, row) => {
        const p = row.proposed
        const parts = [p.name, p.summary, p.description, ...p.tags].filter(Boolean).join(' · ')
        return (
          <Space direction="vertical" size={0}>
            <Text>{parts || '—'}</Text>
            {p.assetTypeCode && <Text type="secondary" style={{ fontSize: 12 }}>类型：{p.assetTypeCode}</Text>}
          </Space>
        )
      },
    },
    {
      title: '依据',
      width: 120,
      render: (_, row) => {
        const count = row.evidence.length
        if (count === 0) return <Text type="secondary">无</Text>
        const evidenceText = row.evidence.join('；')
        return (
          <Popover title="依据片段" content={<Text style={{ maxWidth: 320, whiteSpace: 'pre-wrap' }}>{evidenceText}</Text>}>
            <Button type="link" size="small" icon={<LinkOutlined />}>{count} 条依据</Button>
          </Popover>
        )
      },
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 90,
      render: (value: number | null) => (value == null ? '—' : `${Math.round(value * 100)}%`),
    },
    {
      title: '创建',
      dataIndex: 'createdAt',
      width: 150,
      render: (value: string) => new Date(value).toLocaleString(),
    },
    {
      title: '操作',
      width: 170,
      render: (_, row) => (
        <Space>
          {canCurate && row.status === 'PENDING' && row.targetType === 'ASSET' && (
            <>
              <Button size="small" type="primary" data-testid={`confirm-${row.id}`} loading={actingId === row.id}
                onClick={() => void act(row, 'confirm')}>
                确认
              </Button>
              <Button size="small" danger data-testid={`reject-${row.id}`} loading={actingId === row.id}
                onClick={() => void act(row, 'reject')}>
                驳回
              </Button>
            </>
          )}
          {canCurate && row.targetType === 'ASSET' && (
            <TooltipLink title="重新整理（替换待确认建议）">
              <Button size="small" icon={<ReloadOutlined />} loading={actingId === row.id}
                onClick={() => void act(row, 'regenerate')} />
            </TooltipLink>
          )}
        </Space>
      ),
    },
  ]

  return (
    <Page>
      <Typography.Title level={5} style={{ marginTop: 0 }}>AI 编目建议 · 待确认处理</Typography.Title>
      <Filters>
        <Select<AiSuggestionStatus | undefined>
          aria-label="按状态筛选"
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          value={status}
          onChange={(value) => { setStatus(value); setPage(1) }}
          options={(['PENDING', 'CONFIRMED', 'REJECTED', 'SUPERSEDED'] as AiSuggestionStatus[]).map((key) => ({
            value: key,
            label: statusLabel[key],
          }))}
        />
        <Select<AiSuggestionTargetType | undefined>
          aria-label="按目标类型筛选"
          allowClear
          placeholder="目标类型"
          style={{ width: 130 }}
          value={targetType}
          onChange={(value) => { setTargetType(value); setPage(1) }}
          options={(['ASSET', 'KNOWLEDGE_DOC'] as AiSuggestionTargetType[]).map((key) => ({
            value: key,
            label: targetLabel[key],
          }))}
        />
        <Button data-testid="refresh-list" onClick={() => void refresh()}>刷新</Button>
      </Filters>
      <Table<SuggestionRow>
        rowKey="id"
        size="small"
        columns={columns}
        dataSource={rows}
        loading={loading}
        pagination={{
          current: page,
          pageSize: perPage,
          total,
          showSizeChanger: true,
          onChange: (nextPage, nextSize) => { setPage(nextPage); setPerPage(nextSize) },
        }}
        locale={{ emptyText: '暂无 AI 建议' }}
      />
    </Page>
  )
}

function TooltipLink({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <span title={title} aria-label={title}>
      {children}
    </span>
  )
}
