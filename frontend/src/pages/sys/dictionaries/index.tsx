import {
  ApartmentOutlined,
  DatabaseOutlined,
  EditOutlined,
  EllipsisOutlined,
  MergeCellsOutlined,
  PlusOutlined,
  SearchOutlined,
  StopOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Button,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Radio,
  Select,
  Skeleton,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useRef, useState } from 'react'
import styled from 'styled-components'
import {
  createDictionaryItem,
  getDictionaryCategories,
  getDictionaryItems,
  mergeDictionaryItem,
  updateDictionaryItem,
} from '../../../services/dictionaryService'
import type { DictionaryCategory, DictionaryItem, DictionaryStatus, SaveDictionaryItemInput } from '../../../types/dictionary'

const Page = styled.div`
  min-width: 0;
`

const Header = styled.header`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dfe5e1;
`

const Title = styled.h1`
  margin: 0;
  color: #20312a;
  font-size: 22px;
  font-weight: 680;
`

const HeaderMeta = styled.div`
  display: flex;
  gap: 20px;
  color: #718079;
  font-size: 12px;

  strong {
    margin-right: 4px;
    color: #2d4138;
    font-size: 18px;
    font-weight: 650;
  }
`

const Workspace = styled.div`
  display: grid;
  grid-template-columns: 222px minmax(0, 1fr);
  min-height: 640px;
  margin-top: 18px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #dce3df;
  border-radius: 6px;
`

const Sidebar = styled.aside`
  padding: 14px 10px;
  background: #f7f9f7;
  border-right: 1px solid #dce3df;
`

const GroupName = styled.div`
  padding: 12px 10px 5px;
  color: #8a958f;
  font-size: 10px;
  font-weight: 650;
`

const CategoryButton = styled.button<{ $active: boolean }>`
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 7px;
  align-items: center;
  width: 100%;
  min-height: 38px;
  padding: 6px 9px;
  color: ${({ $active }) => ($active ? '#1f5548' : '#536159')};
  text-align: left;
  background: ${({ $active }) => ($active ? '#e5efeb' : 'transparent')};
  border: 1px solid ${({ $active }) => ($active ? '#b8d0c7' : 'transparent')};
  border-radius: 5px;
  cursor: pointer;

  &:hover,
  &:focus-visible {
    background: #edf3f0;
    outline: none;
  }
`

const CategoryCount = styled.span`
  color: #8a958f;
  font-size: 10px;
`

const Content = styled.section`
  min-width: 0;
  padding: 18px;
`

const ContentHeader = styled.div`
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 14px;
`

const CategoryTitle = styled.h2`
  margin: 0;
  color: #263831;
  font-size: 17px;
  font-weight: 650;
`

const CategoryDescription = styled.div`
  margin-top: 3px;
  color: #7b8781;
  font-size: 11px;
`

const Toolbar = styled.div`
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;

  .ant-input-affix-wrapper {
    width: 230px;
  }
`

const ItemName = styled.div`
  color: #2b3d35;
  font-size: 13px;
  font-weight: 650;
`

const ItemCode = styled.div`
  margin-top: 2px;
  color: #89938e;
  font-family: "SFMono-Regular", Consolas, monospace;
  font-size: 10px;
`

const UsedCount = styled.span`
  color: #3f5048;
  font-variant-numeric: tabular-nums;
`

const DrawerFooter = styled.div`
  display: flex;
  justify-content: flex-end;
  gap: 8px;
`

const RelationFields = styled.div`
  padding: 12px;
  background: #f6f8f6;
  border: 1px solid #e0e6e2;
  border-radius: 5px;
`

const statusLabels: Record<DictionaryStatus, { text: string; color: string }> = {
  ENABLED: { text: '启用', color: 'green' },
  DISABLED: { text: '停用', color: 'default' },
  MERGED: { text: '已合并', color: 'gold' },
}

function categoryIcon(category: DictionaryCategory) {
  if (category.groupName === '产品体系' || category.groupName === '生产体系') return <ApartmentOutlined />
  return <DatabaseOutlined />
}

export default function DictionaryPage() {
  const { message, modal } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const categoriesQuery = useQuery({ queryKey: ['dictionary-categories'], queryFn: getDictionaryCategories })
  const itemsQuery = useQuery({ queryKey: ['dictionary-items'], queryFn: getDictionaryItems })
  const categories = useMemo(() => categoriesQuery.data ?? [], [categoriesQuery.data])
  const items = useMemo(() => itemsQuery.data ?? [], [itemsQuery.data])
  const [selectedCategoryCode, setSelectedCategoryCode] = useState('PLATFORM_FAMILY')
  const selectedCategoryRef = useRef('PLATFORM_FAMILY')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<DictionaryStatus | 'ALL'>('ALL')
  const [parentId, setParentId] = useState<number>()
  const [editing, setEditing] = useState<DictionaryItem>()
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [mergeSource, setMergeSource] = useState<DictionaryItem>()
  const [mergeTargetId, setMergeTargetId] = useState<number>()
  const [form] = Form.useForm<SaveDictionaryItemInput>()

  const selectedCategory = categories.find((category) => category.code === selectedCategoryCode) ?? categories[0]
  const parentCategory = selectedCategory?.parentCategory
  const parentOptions = items
    .filter((item) => item.category === parentCategory && item.status === 'ENABLED')
    .map((item) => ({ value: item.id, label: item.name }))
  const itemById = useMemo(() => new Map(items.map((item) => [item.id, item])), [items])
  const visibleItems = items.filter((item) => {
    const normalized = query.trim().toLowerCase()
    return item.category === selectedCategoryCode
      && (status === 'ALL' || item.status === status)
      && (!parentId || item.parentId === parentId)
      && (!normalized || item.name.toLowerCase().includes(normalized) || item.code.toLowerCase().includes(normalized))
  })

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['dictionary-items'] })
  const saveMutation = useMutation({
    mutationFn: (values: SaveDictionaryItemInput) => editing
      ? updateDictionaryItem(editing.id, values)
      : createDictionaryItem(values),
    onSuccess: async () => {
      await refresh()
      setDrawerOpen(false)
      message.success(editing ? '字典项已更新' : '字典项已新增')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '保存失败'),
  })
  const mergeMutation = useMutation({
    mutationFn: () => mergeDictionaryItem(mergeSource!.id, mergeTargetId!, mergeSource!.version),
    onSuccess: async () => {
      await refresh()
      setMergeSource(undefined)
      setMergeTargetId(undefined)
      message.success('字典项已合并并保留历史引用')
    },
    onError: (error) => message.error(error instanceof Error ? error.message : '合并失败'),
  })

  const openCreate = () => {
    const categoryCode = selectedCategoryRef.current
    const categoryItems = items.filter((item) => item.category === categoryCode)
    setEditing(undefined)
    form.setFieldsValue({
      category: categoryCode,
      status: 'ENABLED',
      sortOrder: Math.max(0, ...categoryItems.map((item) => item.sortOrder)) + 10,
      directional: false,
      allowDuplicate: false,
      version: 0,
      parentId,
    })
    setDrawerOpen(true)
  }

  const openEdit = (item: DictionaryItem) => {
    setEditing(item)
    form.setFieldsValue({ ...item })
    setDrawerOpen(true)
  }

  const toggleStatus = (item: DictionaryItem) => {
    const nextStatus: DictionaryStatus = item.status === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    modal.confirm({
      title: nextStatus === 'DISABLED' ? `停用“${item.name}”？` : `重新启用“${item.name}”？`,
      content: item.usageCount > 0 && nextStatus === 'DISABLED'
        ? `该值已被 ${item.usageCount} 份资产使用。停用后历史资产保留，新上传和编辑时不可再选择。`
        : undefined,
      okText: nextStatus === 'DISABLED' ? '确认停用' : '确认启用',
      okButtonProps: { danger: nextStatus === 'DISABLED' },
      onOk: async () => {
        try {
          await updateDictionaryItem(item.id, { ...item, status: nextStatus })
          await refresh()
          message.success(nextStatus === 'DISABLED' ? '已停用' : '已启用')
        } catch (error) {
          message.error(error instanceof Error ? error.message : '状态更新失败')
          throw error
        }
      },
    })
  }

  const columns: ColumnsType<DictionaryItem> = [
    {
      title: '字典项',
      dataIndex: 'name',
      width: 240,
      render: (_, item) => <div><ItemName>{item.name}</ItemName><ItemCode>{item.code}</ItemCode></div>,
    },
    ...(parentCategory ? [{
      title: '上级',
      dataIndex: 'parentId',
      width: 130,
      render: (value: number | undefined) => value ? itemById.get(value)?.name ?? '上级已停用' : '-',
    }] : []),
    {
      title: '状态',
      dataIndex: 'status',
      width: 88,
      render: (value: DictionaryStatus) => <Tag color={statusLabels[value].color}>{statusLabels[value].text}</Tag>,
    },
    {
      title: '引用资产',
      dataIndex: 'usageCount',
      width: 90,
      align: 'right',
      render: (value: number) => <UsedCount>{value}</UsedCount>,
    },
    { title: '排序', dataIndex: 'sortOrder', width: 72, align: 'right' },
    {
      title: '说明',
      dataIndex: 'description',
      ellipsis: true,
      render: (value: string | undefined, item) => item.category === 'RELATION_TYPE'
        ? `${item.forwardName} / ${item.reverseName} · ${item.directional ? '有方向' : '无方向'}`
        : value || '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 98,
      align: 'right',
      render: (_, item) => item.status === 'MERGED' ? <span style={{ color: '#909a95' }}>只读</span> : (
        <Space size={2}>
          <Tooltip title="编辑"><Button type="text" size="small" icon={<EditOutlined />} onClick={() => openEdit(item)} aria-label={`编辑 ${item.name}`} /></Tooltip>
          <Dropdown menu={{ items: [
            { key: 'toggle', icon: <StopOutlined />, label: item.status === 'ENABLED' ? '停用' : '启用', onClick: () => toggleStatus(item) },
            { key: 'merge', icon: <MergeCellsOutlined />, label: '合并到...', onClick: () => setMergeSource(item) },
          ] }} trigger={['click']}>
            <Button type="text" size="small" icon={<EllipsisOutlined />} aria-label={`${item.name}更多操作`} />
          </Dropdown>
        </Space>
      ),
    },
  ]

  const groups = Array.from(new Set(categories.map((category) => category.groupName)))
  const enabledCount = items.filter((item) => item.status === 'ENABLED').length
  const usedCount = items.filter((item) => item.usageCount > 0).length

  return (
    <Page>
      <Header>
        <Title>基础数据管理</Title>
        <HeaderMeta>
          <span><strong>{items.length}</strong>字典项</span>
          <span><strong>{enabledCount}</strong>启用</span>
          <span><strong>{usedCount}</strong>已引用</span>
        </HeaderMeta>
      </Header>

      <Workspace>
        <Sidebar aria-label="字典分类">
          {groups.map((group) => (
            <div key={group}>
              <GroupName>{group}</GroupName>
              {categories.filter((category) => category.groupName === group).map((category) => (
                <CategoryButton
                  key={category.code}
                  type="button"
                  $active={category.code === selectedCategoryCode}
                  onClick={() => {
                    selectedCategoryRef.current = category.code
                    setSelectedCategoryCode(category.code)
                    setParentId(undefined)
                    setQuery('')
                    setStatus('ALL')
                  }}
                >
                  {categoryIcon(category)}
                  <span>{category.name}</span>
                  <CategoryCount>{items.filter((item) => item.category === category.code).length}</CategoryCount>
                </CategoryButton>
              ))}
            </div>
          ))}
        </Sidebar>

        <Content>
          <ContentHeader>
            <div>
              <CategoryTitle>{selectedCategory?.name ?? '字典项'}</CategoryTitle>
              <CategoryDescription>{selectedCategory?.description}</CategoryDescription>
            </div>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>新增字典项</Button>
          </ContentHeader>

          <Toolbar>
            <Input value={query} allowClear prefix={<SearchOutlined />} placeholder="搜索名称或编码" onChange={(event) => setQuery(event.target.value)} />
            {parentCategory && (
              <Select
                allowClear
                value={parentId}
                placeholder={`全部${categories.find((item) => item.code === parentCategory)?.name ?? '上级'}`}
                options={parentOptions}
                style={{ width: 170 }}
                onChange={setParentId}
              />
            )}
            <Radio.Group
              value={status}
              optionType="button"
              buttonStyle="solid"
              size="small"
              options={[{ label: '全部', value: 'ALL' }, { label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }, { label: '已合并', value: 'MERGED' }]}
              onChange={(event) => setStatus(event.target.value as DictionaryStatus | 'ALL')}
            />
          </Toolbar>

          {categoriesQuery.isLoading || itemsQuery.isLoading ? <Skeleton active paragraph={{ rows: 10 }} />
            : categoriesQuery.isError || itemsQuery.isError ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="字典数据加载失败">
                <Button onClick={() => { void categoriesQuery.refetch(); void itemsQuery.refetch() }}>重试</Button>
              </Empty>
            ) : (
            <Table<DictionaryItem>
              rowKey="id"
              size="small"
              columns={columns}
              dataSource={visibleItems}
              pagination={{ pageSize: 12, showSizeChanger: false, showTotal: (total) => `共 ${total} 项` }}
              locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前分类暂无字典项" /> }}
              scroll={{ x: 760 }}
            />
          )}
        </Content>
      </Workspace>

      <Drawer
        title={editing ? `编辑 ${editing.name}` : `新增${selectedCategory?.name ?? '字典项'}`}
        width={440}
        open={drawerOpen}
        destroyOnHidden
        onClose={() => setDrawerOpen(false)}
        footer={<DrawerFooter><Button onClick={() => setDrawerOpen(false)}>取消</Button><Button type="primary" loading={saveMutation.isPending} onClick={() => form.submit()}>保存</Button></DrawerFooter>}
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveMutation.mutate(values)}>
          <Form.Item name="category" hidden><Input /></Form.Item>
          <Form.Item name="version" hidden><InputNumber /></Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input maxLength={100} /></Form.Item>
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入编码' }, { pattern: /^[A-Za-z0-9_-]+$/, message: '仅支持字母、数字、下划线和短横线' }]}><Input maxLength={80} /></Form.Item>
          {parentCategory && <Form.Item name="parentId" label="上级字典项" rules={[{ required: true, message: '请选择上级字典项' }]}><Select showSearch optionFilterProp="label" options={parentOptions} /></Form.Item>}
          <Space size={12} align="start" style={{ width: '100%' }}>
            <Form.Item name="status" label="状态"><Select style={{ width: 160 }} options={[{ value: 'ENABLED', label: '启用' }, { value: 'DISABLED', label: '停用' }]} /></Form.Item>
            <Form.Item name="sortOrder" label="排序" rules={[{ required: true }]}><InputNumber min={0} max={9999} style={{ width: 160 }} /></Form.Item>
          </Space>
          <Form.Item name="description" label="说明"><Input.TextArea maxLength={300} autoSize={{ minRows: 3, maxRows: 5 }} /></Form.Item>
          {selectedCategoryCode === 'RELATION_TYPE' && (
            <RelationFields>
              <Form.Item name="forwardName" label="正向名称" rules={[{ required: true }]}><Input placeholder="例如：包含" /></Form.Item>
              <Form.Item name="reverseName" label="反向名称" rules={[{ required: true }]}><Input placeholder="例如：属于" /></Form.Item>
              <Space size={28}>
                <Form.Item name="directional" label="有方向" valuePropName="checked"><Switch /></Form.Item>
                <Form.Item name="allowDuplicate" label="允许重复关系" valuePropName="checked"><Switch /></Form.Item>
              </Space>
            </RelationFields>
          )}
        </Form>
      </Drawer>

      <Modal
        title={mergeSource ? `合并“${mergeSource.name}”` : '合并字典项'}
        open={Boolean(mergeSource)}
        okText="确认合并"
        okButtonProps={{ danger: true, disabled: !mergeTargetId }}
        confirmLoading={mergeMutation.isPending}
        onCancel={() => { setMergeSource(undefined); setMergeTargetId(undefined) }}
        onOk={() => mergeMutation.mutate()}
      >
        <p style={{ color: '#59675f', lineHeight: 1.7 }}>
          {mergeSource?.usageCount ? `当前值被 ${mergeSource.usageCount} 份资产引用。` : '当前值尚未被资产引用。'}合并后原值保留为历史记录，新业务统一使用目标值。
        </p>
        <Select
          showSearch
          optionFilterProp="label"
          placeholder="选择合并目标"
          value={mergeTargetId}
          style={{ width: '100%' }}
          options={items.filter((item) => item.category === mergeSource?.category && item.id !== mergeSource?.id && item.status === 'ENABLED').map((item) => ({ value: item.id, label: `${item.name} (${item.code})` }))}
          onChange={setMergeTargetId}
        />
      </Modal>
    </Page>
  )
}
