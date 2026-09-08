import { LockOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, App, Button, Form, Input, Typography } from 'antd'
import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { useAuth } from './useAuth'

const Shell = styled.div`
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 400px;
  background: #102b3d;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
  }
`

const Banner = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  padding: 48px 56px;
  color: #fff;
  background:
    radial-gradient(1200px 500px at -10% -10%, rgba(47, 117, 103, 0.55), transparent 60%),
    radial-gradient(900px 480px at 110% 110%, rgba(60, 120, 201, 0.4), transparent 55%),
    #102b3d;

  h1 { color: #fff; margin: 0; font-size: 30px; letter-spacing: 0.4px; }
  .sub { color: #b9c8d3; font-size: 14px; max-width: 460px; line-height: 1.8; }
  .points { display: grid; gap: 8px; margin-top: 26px; }
  .point { display: flex; gap: 8px; color: #d6e2ea; font-size: 13px; }
  .point::before { content: '✓'; color: #6fd0b1; font-weight: 700; }

  @media (max-width: 860px) { display: none; }
`

const Panel = styled.div`
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7f6;
  padding: 32px;
`

const Card = styled.div`
  width: 100%;
  max-width: 340px;
  background: #fff;
  border: 1px solid #e6e9ef;
  border-radius: 14px;
  padding: 30px 28px 24px;
  box-shadow: 0 10px 30px rgba(16, 43, 61, 0.08);
`

const DemoGrid = styled.div`
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-top: 6px;

  @media (max-width: 420px) {
    grid-template-columns: repeat(2, 1fr);
  }
`

const DemoButton = styled(Button)`
  font-size: 12px;
  padding-inline: 4px;
`

const DEMO_ACCOUNTS = [
  { userId: 'emp-admin', label: '管理员' },
  { userId: 'emp-chen', label: '陈工' },
  { userId: 'emp-li', label: '李工' },
  { userId: 'emp-wang', label: '王工' },
]

interface LoginForm {
  userId: string
  password: string
}

export default function LoginPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()
  const [form] = Form.useForm<LoginForm>()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const submit = async (values: LoginForm) => {
    setSubmitting(true)
    setError(null)
    try {
      const user = await login(values.userId.trim(), values.password)
      void message.success(`欢迎，${user.name}`)
      navigate(from, { replace: true })
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : '登录失败，请稍后重试')
    } finally {
      setSubmitting(false)
    }
  }

  const quickLogin = async (userId: string) => {
    setError(null)
    try {
      await login(userId, 'demo123')
      navigate(from, { replace: true })
    } catch {
      setError('演示账号登录失败')
    }
  }

  return (
    <Shell>
      <Banner>
        <Typography.Text style={{ color: '#8fe0c2', fontWeight: 700, letterSpacing: 2 }}>数模资产中心</Typography.Text>
        <h1>资产数据治理工作台</h1>
        <div className="sub">从资产盘点、扫描问题到整改分派与验收应用，每一步都有明确的关键人，让数据治理闭环不烂尾。</div>
        <div className="points">
          <div className="point">扫描发现问题 → 问题池批量建治理任务</div>
          <div className="point">分派责任人 + 到期日，逾期自动提醒</div>
          <div className="point">业务确认、质量验收全程留痕</div>
        </div>
      </Banner>
      <Panel>
        <Card>
          <Typography.Title level={4} style={{ margin: '0 0 4px' }}>登录</Typography.Title>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>使用员工工号与密码进入</Typography.Text>
          <Form form={form} layout="vertical" initialValues={{ userId: 'emp-admin', password: 'demo123' }} onFinish={submit} style={{ marginTop: 18 }}>
            {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 14 }} />}
            <Form.Item name="userId" label="工号" rules={[{ required: true, message: '请输入工号' }]}>
              <Input prefix={<UserOutlined aria-hidden />} autoComplete="username" aria-label="工号" />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined aria-hidden />} autoComplete="current-password" aria-label="密码" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block loading={submitting} style={{ marginTop: 4 }}>登 录</Button>
          </Form>
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 18, fontSize: 12 }}>演示账号（密码 demo123）</Typography.Text>
          <DemoGrid>
            {DEMO_ACCOUNTS.map(account => (
              <DemoButton key={account.userId} size="small" onClick={() => void quickLogin(account.userId)}>
                {account.label}
              </DemoButton>
            ))}
          </DemoGrid>
        </Card>
      </Panel>
    </Shell>
  )
}
