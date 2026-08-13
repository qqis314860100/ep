import { useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Button, Empty, Input, Modal, Select, Space, Table, Tabs, Tag, Typography } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import { getOperationLogs, getSystemUsers, updateUserRoles, updateUserScopes } from './api'
import type { OperationLog, SystemRole, SystemUser, SystemUserScope } from './types'

const Page = styled.div`
  min-width: 0;
  padding: 0 2px;
`

const PageBar = styled.header`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 18px;
  min-height: 44px;
  padding-bottom: 10px;
`

const PageTitle = styled.h1`
  margin: 0;
  color: #22312b;
  font-size: 18px;
  font-weight: 680;
`

const PageMeta = styled.div`
  margin-top: 2px;
  color: #7c8882;
  font-size: 11px;
`

const ScopeRow = styled.div`
  display: grid;
  grid-template-columns: 1fr 1fr auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
`

const roleOptions: { value: SystemRole; label: string }[] = [
  { value: 'NORMAL_USER', label: '普通用户' },
  { value: 'UPLOADER', label: '上传者/资产责任人' },
  { value: 'DOCUMENT_MAINTAINER', label: '文档维护者' },
  { value: 'CONTENT_ADMIN', label: '内容管理员' },
  { value: 'SYSTEM_ADMIN', label: '系统管理员' },
]

const roleColor: Record<SystemRole, string> = {
  NORMAL_USER: 'default',
  UPLOADER: 'blue',
  DOCUMENT_MAINTAINER: 'cyan',
  CONTENT_ADMIN: 'green',
  SYSTEM_ADMIN: 'purple',
}

const actionLabel: Record<string, string> = {
  ROLE_UPDATE: '角色变更',
  SCOPE_UPDATE: '数据范围变更',
}

function scopeText(scope: SystemUserScope) {
  return [scope.base, scope.productLine].filter(Boolean).join(' / ') || '未设置'
}

export function SystemAdminPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<SystemUser>()
  const [editRoles, setEditRoles] = useState<SystemRole[]>([])
  const [editScopes, setEditScopes] = useState<SystemUserScope[]>([])
  const [saving, setSaving] = useState(false)
  const [logAction, setLogAction] = useState<string>()
  const [logPage, setLogPage] = useState(1)

  const usersQuery = useQuery({ queryKey: ['system-users'], queryFn: getSystemUsers })
  const logsQuery = useQuery({
    queryKey: ['operation-logs', logAction, logPage],
    queryFn: () => getOperationLogs({ action: logAction, page: logPage, perPage: 20 }),
  })

  const invalidateUsers = () => queryClient.invalidateQueries({ queryKey: ['system-users'] })

  const openEdit = (user: SystemUser) => {
    setEditing(user)
    setEditRoles(user.roles)
    setEditScopes(user.scopes.map((scope) => ({ ...scope })))
  }

  const saveEdit = async () => {
    if (!editing) return
    setSaving(true)
    try {
      let currentVersion = editing.version
      if (JSON.stringify(editRoles) !== JSON.stringify(editing.roles)) {
        const updated = await updateUserRoles(editing.id, editRoles, currentVersion)
        currentVersion = updated.version
      }
      const normalizedScopes = editScopes
        .filter((scope) => scope.base.trim() || scope.productLine.trim())
        .map((scope) => ({ id: scope.id, base: scope.base.trim(), productLine: scope.productLine.trim() }))
      if (JSON.stringify(normalizedScopes) !== JSON.stringify(editing.scopes)) {
        await updateUserScopes(editing.id, normalizedScopes, currentVersion)
      }
      await invalidateUsers()
      await queryClient.invalidateQueries({ queryKey: ['operation-logs'] })
      setEditing(undefined)
      message.success('权限已更新')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '更新失败')
    } finally {
      setSaving(false)
    }
  }

  const addScope = () => setEditScopes((current) => [...current, { id: 0, base: '', productLine: '' }])

  const userColumns = [
    { title: '姓名', dataIndex: 'name', width: 110, render: (value: string) => <Typography.Text strong>{value}</Typography.Text> },
    { title: '部门', dataIndex: 'department', width: 140 },
    { title: '账号', dataIndex: 'userId', width: 120, render: (value: string) => <Typography.Text type="secondary" style={{ fontFamily: 'monospace' }}>{value}</Typography.Text> },
    {
      title: '角色',
      dataIndex: 'roles',
      render: (roles: SystemRole[]) => (roles.length ? roles.map((role) => <Tag key={role} color={roleColor[role]}>{roleOptions.find((item) => item.value === role)?.label ?? role}</Tag>) : <Typography.Text type="secondary">未分配</Typography.Text>),
    },
    {
      title: '数据范围',
      dataIndex: 'scopes',
      render: (scopes: SystemUserScope[]) => (scopes.length ? scopes.map((scope, index) => <Tag key={index}>{scopeText(scope)}</Tag>) : <Typography.Text type="secondary">全部</Typography.Text>),
    },
    { title: '更新时间', dataIndex: 'updatedAt', width: 120, render: (value: string) => new Intl.DateTimeFormat('zh-CN').format(new Date(value)) },
    { title: '操作', key: 'actions', width: 100, render: (_: unknown, user: SystemUser) => <Button type="link" size="small" onClick={() => openEdit(user)}>编辑权限</Button> },
  ]

  const logColumns = [
    { title: '时间', dataIndex: 'createdAt', width: 180, render: (value: string) => new Intl.DateTimeFormat('zh-CN', { dateStyle: 'short', timeStyle: 'medium' }).format(new Date(value)) },
    { title: '操作人', dataIndex: 'actorUserId', width: 140 },
    { title: '操作类型', dataIndex: 'action', width: 140, render: (value: string) => <Tag color="blue">{actionLabel[value] ?? value}</Tag> },
    { title: '对象', key: 'target', render: (_: unknown, log: OperationLog) => <Typography.Text>{log.targetType} · {log.targetId}</Typography.Text> },
  ]

  const logs = logsQuery.data?.data ?? []
  const logTotal = logsQuery.data?.meta.total ?? 0

  return (
    <Page>
      <PageBar>
        <div><PageTitle>系统管理</PageTitle><PageMeta>管理用户角色、数据范围和操作记录</PageMeta></div>
      </PageBar>
      <Tabs
        items={[
          {
            key: 'users',
            label: '用户与角色',
            children: (
              <Table
                rowKey="id"
                columns={userColumns}
                dataSource={usersQuery.data ?? []}
                loading={usersQuery.isLoading}
                size="middle"
                locale={{ emptyText: <Empty description="暂无用户" /> }}
                pagination={false}
              />
            ),
          },
          {
            key: 'logs',
            label: '操作记录',
            children: (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Space>
                  <Select
                    allowClear
                    placeholder="操作类型"
                    style={{ width: 180 }}
                    value={logAction}
                    onChange={(value) => { setLogAction(value); setLogPage(1) }}
                    options={[
                      { value: 'ROLE_UPDATE', label: '角色变更' },
                      { value: 'SCOPE_UPDATE', label: '数据范围变更' },
                    ]}
                  />
                </Space>
                <Table
                  rowKey="id"
                  columns={logColumns}
                  dataSource={logs}
                  loading={logsQuery.isLoading}
                  size="middle"
                  locale={{ emptyText: <Empty description="暂无操作记录" /> }}
                  pagination={{ current: logPage, pageSize: 20, total: logTotal, showSizeChanger: false, onChange: setLogPage }}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        title={`编辑权限 · ${editing?.name ?? ''}`}
        open={Boolean(editing)}
        width={560}
        okText="保存"
        cancelText="取消"
        okButtonProps={{ loading: saving }}
        onOk={() => void saveEdit()}
        onCancel={() => setEditing(undefined)}
        destroyOnHidden
      >
        <Typography.Paragraph type="secondary" style={{ fontSize: 12 }}>
          角色决定该用户可执行的操作，数据范围限定其可访问的蓝本与基地。
        </Typography.Paragraph>
        <Typography.Text strong style={{ fontSize: 12 }}>角色</Typography.Text>
        <Select
          mode="multiple"
          allowClear
          style={{ width: '100%', margin: '8px 0 16px' }}
          placeholder="选择角色"
          value={editRoles}
          onChange={(value) => setEditRoles(value)}
          options={roleOptions}
        />
        <Space style={{ width: '100%', justifyContent: 'space-between' }}>
          <Typography.Text strong style={{ fontSize: 12 }}>数据范围</Typography.Text>
          <Button type="link" size="small" onClick={addScope}>添加范围</Button>
        </Space>
        <div style={{ marginTop: 8 }}>
          {editScopes.length === 0 ? <Typography.Text type="secondary" style={{ fontSize: 12 }}>留空表示全部范围可访问</Typography.Text>
            : editScopes.map((scope, index) => (
              <ScopeRow key={index}>
                <Input placeholder="基地，如 宁德基地" value={scope.base} onChange={(event) => setEditScopes((current) => current.map((item, i) => i === index ? { ...item, base: event.target.value } : item))} />
                <Input placeholder="蓝本，如 H03" value={scope.productLine} onChange={(event) => setEditScopes((current) => current.map((item, i) => i === index ? { ...item, productLine: event.target.value } : item))} />
                <Button type="text" danger size="small" onClick={() => setEditScopes((current) => current.filter((_, i) => i !== index))}>删除</Button>
              </ScopeRow>
            ))}
        </div>
      </Modal>
    </Page>
  )
}
