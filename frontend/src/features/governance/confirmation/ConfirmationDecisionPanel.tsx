import { Button, Form, Input, Radio, Space, Typography } from 'antd'
import { useEffect, useState } from 'react'
import type { ConfirmationDecision } from '../types'

type Values = { decision: 'APPROVED' | 'REJECTED'; comment?: string }

export function ConfirmationDecisionPanel({ decision, loading, onSave }: { decision?: ConfirmationDecision; loading: boolean; onSave: (values: Values) => void }) {
  const [form] = Form.useForm<Values>()
  const [commentError, setCommentError] = useState(false)
  const selected = Form.useWatch('decision', form)
  useEffect(() => { form.setFieldsValue({ decision: decision?.decision ?? 'APPROVED', comment: decision?.comment ?? '' }); setCommentError(false) }, [decision, form])
  const submit = (values: Values) => {
    if (values.decision === 'REJECTED' && !values.comment?.trim()) {
      setCommentError(true)
      return
    }
    setCommentError(false)
    onSave(values)
  }
  return <Form form={form} layout="vertical" onFinish={submit} onValuesChange={() => setCommentError(false)}>
    <Typography.Title level={5}>确认决定</Typography.Title>
    <Form.Item name="decision" label="处理结果" rules={[{ required: true }]}><Radio.Group><Radio.Button value="APPROVED">通过当前项</Radio.Button><Radio.Button value="REJECTED">退回当前项</Radio.Button></Radio.Group></Form.Item>
    {selected === 'REJECTED' && <><Form.Item name="comment" label="退回意见"><Input.TextArea rows={3} /></Form.Item>{commentError && <Typography.Text type="danger" role="alert" style={{ display: 'block', margin: '-18px 0 16px' }}>请填写退回意见</Typography.Text>}</>}
    <Space><Button type="primary" htmlType="submit" loading={loading}>保存决定</Button></Space>
  </Form>
}
