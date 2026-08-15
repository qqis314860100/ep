import { Alert, Form, Input, Modal, Typography } from 'antd'
import { useState } from 'react'

interface DisableDialogProps {
  open: boolean
  assetName: string
  onClose: () => void
  onSubmit: (reason: string) => Promise<void>
}

export function DisableDialog({ open, assetName, onClose, onSubmit }: DisableDialogProps) {
  const [form] = Form.useForm<{ reason: string }>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string>()

  const submit = async () => {
    let values: { reason: string }
    try {
      values = await form.validateFields()
    } catch {
      return // 表单校验错误已由 Form 展示
    }
    setSubmitting(true)
    setError(undefined)
    try {
      await onSubmit(values.reason.trim())
      form.resetFields()
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '停用失败')
      setSubmitting(false)
    }
  }

  return (
    <Modal
      title="停用资料"
      open={open}
      onOk={() => void submit()}
      onCancel={() => { setError(undefined); onClose() }}
      okText="确认停用"
      okButtonProps={{ danger: true }}
      confirmLoading={submitting}
      width={440}
    >
      <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
        停用后「{assetName}」默认不再出现在普通检索结果中，原文件、收藏、评论和点赞继续保留；管理员可按状态查看。
      </Typography.Paragraph>
      <Form form={form} layout="vertical">
        {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 12 }} />}
        <Form.Item name="reason" label="停用原因" rules={[{ required: true, whitespace: true, message: '请填写停用原因' }]}>
          <Input.TextArea rows={3} maxLength={200} placeholder="例如：该产线已停产，资料不再使用" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
