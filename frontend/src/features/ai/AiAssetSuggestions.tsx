import { Button, Empty, List, Space, Tag, Typography, message } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import type { AiSuggestion } from '../../types/ai'
import { currentActor } from '../auth/session'
import { AiApiError, confirmSuggestion, listSuggestions, rejectSuggestion } from './api'

const statusColor: Record<AiSuggestion['status'], string> = {
  PENDING: 'gold',
  CONFIRMED: 'green',
  REJECTED: 'red',
  SUPERSEDED: 'default',
}

const statusLabel: Record<AiSuggestion['status'], string> = {
  PENDING: '待确认',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  SUPERSEDED: '已作废',
}

/** 资产详情内嵌的 AI 编目建议区（T5 内嵌入口）：列出该资产建议并可确认/驳回。 */
export function AiAssetSuggestions({ assetId }: { assetId: number }) {
  const [items, setItems] = useState<AiSuggestion[]>([])
  const [loading, setLoading] = useState(false)
  const [actingId, setActingId] = useState<number | null>(null)
  const actor = currentActor()
  const canCurate = actor.roles.split(',').some((role) => role === 'CONTENT_ADMIN' || role === 'SYSTEM_ADMIN')

  const refresh = useCallback(async () => {
    setLoading(true)
    try {
      const result = await listSuggestions({ targetType: 'ASSET', targetId: assetId, page: 1, perPage: 20 })
      setItems(result.data)
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : 'AI 建议加载失败')
    } finally {
      setLoading(false)
    }
  }, [assetId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const act = async (id: number, action: 'confirm' | 'reject') => {
    setActingId(id)
    try {
      if (action === 'confirm') await confirmSuggestion(id)
      else await rejectSuggestion(id)
      message.success(action === 'confirm' ? '建议已确认并生效' : '建议已驳回')
      await refresh()
    } catch (error) {
      message.error(error instanceof AiApiError ? error.message : '操作失败')
    } finally {
      setActingId(null)
    }
  }

  return (
    <List
      size="small"
      loading={loading}
      dataSource={items}
      locale={{ emptyText: <Empty description="该资产暂无 AI 编目建议" image={Empty.PRESENTED_IMAGE_SIMPLE} /> }}
      renderItem={(item) => {
        const parts = [item.proposed.name, item.proposed.summary, item.proposed.description, ...item.proposed.tags]
          .filter(Boolean)
          .join(' · ')
        return (
          <List.Item
            actions={canCurate && item.status === 'PENDING' ? [
              <Button key="confirm" type="primary" size="small" loading={actingId === item.id}
                onClick={() => void act(item.id, 'confirm')}>
                确认
              </Button>,
              <Button key="reject" size="small" danger loading={actingId === item.id}
                onClick={() => void act(item.id, 'reject')}>
                驳回
              </Button>,
            ] : []}
          >
            <List.Item.Meta
              title={
                <Space>
                  <Tag color={statusColor[item.status]}>{statusLabel[item.status]}</Tag>
                  <Typography.Text strong>{parts || 'AI 建议'}</Typography.Text>
                </Space>
              }
              description={
                item.evidence.length > 0
                  ? `依据：${item.evidence.join('；')}`
                  : undefined
              }
            />
          </List.Item>
        )
      }}
    />
  )
}
