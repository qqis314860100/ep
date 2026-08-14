import { SearchOutlined } from '@ant-design/icons'
import { Alert, Form, Input, Modal, Select, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { searchAssets, createAssetRelation, updateAssetRelation } from '../../../../services/assetService'
import type { AssetRelation, RelationType } from '../../../../types/asset'

interface RelationTypeMeta {
  label: string
  forward: string
  reverse: string
  directional: boolean
}

const relationTypeMeta: Record<RelationType, RelationTypeMeta> = {
  CONTAINS: { label: '包含', forward: '包含', reverse: '属于', directional: true },
  REFERENCES: { label: '引用', forward: '引用', reverse: '被引用', directional: true },
  MATCHES: { label: '配套', forward: '配套', reverse: '配套', directional: false },
  ASSOCIATED_WITH: { label: '关联', forward: '关联', reverse: '关联', directional: true },
  REPLACES: { label: '替代', forward: '替代', reverse: '被替代', directional: true },
}

interface FormValues {
  targetAssetId: number
  relationType: RelationType
  direction: 'FORWARD' | 'REVERSE'
  description?: string
}

interface AssetRelationDialogProps {
  open: boolean
  assetId: number
  relation?: AssetRelation
  onClose: () => void
  onSaved: () => void
}

export function AssetRelationDialog({ open, assetId, relation, onClose, onSaved }: AssetRelationDialogProps) {
  const queryClient = useQueryClient()
  const [form] = Form.useForm<FormValues>()
  const [targetQuery, setTargetQuery] = useState('')
  const editing = Boolean(relation)
  const relationType = Form.useWatch('relationType', form)
  const direction = Form.useWatch('direction', form) ?? 'FORWARD'
  const typeMeta = relationType ? relationTypeMeta[relationType] : undefined

  const targetQueryResult = useQuery({
    queryKey: ['relation-target-search', targetQuery],
    queryFn: () => searchAssets({ query: targetQuery, page: 1, perPage: 8 }),
    enabled: open,
  })
  const targetOptions = useMemo(
    () => (targetQueryResult.data?.data ?? [])
      .filter((asset) => asset.id !== assetId)
      .map((asset) => ({ value: asset.id, label: `${asset.name} · ${asset.assetNumber}` })),
    [targetQueryResult.data, assetId],
  )

  const saveMutation = useMutation({
    mutationFn: (values: FormValues) => {
      if (relation) {
        const forward = values.direction === 'FORWARD'
        return updateAssetRelation(assetId, relation.id, {
          sourceAssetId: forward ? assetId : values.targetAssetId,
          targetAssetId: forward ? values.targetAssetId : assetId,
          relationType: values.relationType,
          description: values.description,
          version: relation.version,
        })
      }
      return createAssetRelation(assetId, {
        targetAssetId: values.targetAssetId,
        relationType: values.relationType,
        description: values.description,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['asset-relations'] })
      onSaved()
      onClose()
    },
  })

  const openDialog = () => {
    form.resetFields()
    if (relation) {
      form.setFieldsValue({
        targetAssetId: relation.targetAssetId,
        relationType: relation.relationType,
        direction: relation.sourceAssetId === assetId ? 'FORWARD' : 'REVERSE',
        description: relation.description,
      })
    }
    setTargetQuery('')
  }

  useEffect(() => {
    if (open) openDialog()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  const preview = typeMeta
    ? typeMeta.directional
      ? direction === 'FORWARD'
        ? `${typeMeta.forward}（当前资料 → 目标资料）`
        : `${typeMeta.reverse}（目标资料 → 当前资料）`
      : `${typeMeta.label}（无方向）`
    : ''

  return (
    <Modal
      title={editing ? '修改资产关系' : '新增资产关系'}
      open={open}
      onOk={() => form.submit()}
      onCancel={onClose}
      okText={editing ? '保存修改' : '确认建立'}
      confirmLoading={saveMutation.isPending}
      width={460}
    >
      <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
        {saveMutation.error && <Alert type="error" showIcon message={saveMutation.error.message} style={{ marginBottom: 12 }} />}
        <Form.Item name="targetAssetId" label="目标资料" rules={[{ required: true, message: '请选择目标资料' }]}>
          <Select
            showSearch
            disabled={editing}
            placeholder="搜索资料名称或编号"
            filterOption={false}
            onSearch={setTargetQuery}
            loading={targetQueryResult.isFetching}
            notFoundContent={targetQuery ? '未找到匹配资料' : '输入关键词搜索资料'}
            options={editing && relation ? [
              { value: relation.targetAssetId, label: `${relation.targetAssetName} · ${relation.targetAssetNumber}` },
              ...targetOptions.filter((option) => option.value !== relation.targetAssetId),
            ] : targetOptions}
            suffixIcon={<SearchOutlined />}
          />
        </Form.Item>
        <Form.Item name="relationType" label="关系类型" rules={[{ required: true, message: '请选择关系类型' }]}>
          <Select
            options={Object.entries(relationTypeMeta).map(([value, meta]) => ({ value, label: meta.label }))}
            placeholder="包含 / 引用 / 配套 / 替代"
          />
        </Form.Item>
        {typeMeta?.directional && (
          <Form.Item name="direction" label="方向" initialValue="FORWARD">
            <Select
              options={[
                { value: 'FORWARD', label: `正向：${typeMeta.forward}` },
                { value: 'REVERSE', label: `反向：${typeMeta.reverse}` },
              ]}
            />
          </Form.Item>
        )}
        <Form.Item name="description" label="说明">
          <Input.TextArea rows={2} maxLength={200} placeholder="关系说明（可选）" />
        </Form.Item>
        {preview && <Typography.Text type="secondary">保存前确认：{preview}{editing ? '（修改后双方详情同步更新）' : ''}</Typography.Text>}
      </Form>
    </Modal>
  )
}
