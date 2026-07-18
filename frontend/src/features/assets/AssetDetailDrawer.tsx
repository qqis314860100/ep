import {
  DownloadOutlined,
  DeleteOutlined,
  EyeOutlined,
  LikeOutlined,
  StarFilled,
  StarOutlined,
  FileZipOutlined,
  LinkOutlined,
} from '@ant-design/icons'
import {
  App as AntdApp,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Input,
  List,
  Popconfirm,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import styled from 'styled-components'
import { useAsset, useAssetRelations, useComments, useFavorite } from '../../hooks/useAssets'
import { addComment, deleteComment, setCommentLike, setFavorite } from '../../services/assetService'
import type { AssetFile, AssetRelation } from '../../types/asset'
import { AssetRelationMap } from './AssetRelationMap'
import {
  formatBytes,
  scopeLabel,
} from './assetPresentation'
import { AssetStatusTag, AssetTypeTag, FileTypeIcon } from './AssetTags'

const DrawerHeader = styled.div`
  min-width: 0;
`

const AssetNumber = styled.div`
  margin-bottom: 3px;
  color: #66736e;
  font-size: 12px;
  font-weight: 500;
`

const AssetName = styled.div`
  overflow: hidden;
  color: #202824;
  font-size: 18px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
`

const Section = styled.section`
  padding: 4px 0 20px;
`

const SectionTitle = styled.h3`
  margin: 0 0 12px;
  color: #2e3a35;
  font-size: 14px;
  font-weight: 600;
`

const relationColumns: ColumnsType<AssetRelation> = [
  {
    title: '关系',
    dataIndex: 'directionLabel',
    width: 82,
    render: (value: string) => <Tag icon={<LinkOutlined />}>{value}</Tag>,
  },
  {
    title: '关联资产',
    dataIndex: 'targetAssetName',
    render: (value: string, record) => (
      <div>
        <Typography.Text strong>{value}</Typography.Text>
        <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
          {record.targetAssetNumber}
        </Typography.Text>
      </div>
    ),
  },
  { title: '适用范围', dataIndex: 'primaryScope', width: 220 },
  {
    title: '状态',
    dataIndex: 'targetAssetStatus',
    width: 100,
    render: (value) => <AssetStatusTag status={value} />,
  },
]

interface AssetDetailDrawerProps {
  assetId?: number
  onClose: () => void
}

export function AssetDetailDrawer({ assetId, onClose }: AssetDetailDrawerProps) {
  const assetQuery = useAsset(assetId)
  const relationsQuery = useAssetRelations(assetId)
  const favoriteQuery = useFavorite(assetId)
  const commentsQuery = useComments(assetId)
  const { message } = AntdApp.useApp()
  const [favoriteSaving, setFavoriteSaving] = useState(false)
  const [commentDraft, setCommentDraft] = useState('')
  const [commentSaving, setCommentSaving] = useState(false)
  const [likedCommentIds, setLikedCommentIds] = useState<Set<number>>(new Set())
  const asset = assetQuery.data

  const toggleFavorite = async () => {
    if (!asset) return
    setFavoriteSaving(true)
    try {
      await setFavorite(asset.id, !favoriteQuery.data)
      await favoriteQuery.refetch()
      message.success(favoriteQuery.data ? '已取消收藏' : '已收藏')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收藏操作失败')
    } finally {
      setFavoriteSaving(false)
    }
  }

  const submitComment = async () => {
    if (!asset || !commentDraft.trim()) {
      message.warning('评论内容不能为空')
      return
    }
    setCommentSaving(true)
    try {
      await addComment(asset.id, commentDraft)
      setCommentDraft('')
      await commentsQuery.refetch()
      message.success('评论已发布')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论发布失败')
    } finally {
      setCommentSaving(false)
    }
  }

  const toggleCommentLike = async (commentId: number) => {
    if (!asset) return
    const liked = likedCommentIds.has(commentId)
    try {
      await setCommentLike(asset.id, commentId, !liked)
      setLikedCommentIds((current) => {
        const next = new Set(current)
        if (liked) next.delete(commentId)
        else next.add(commentId)
        return next
      })
      await commentsQuery.refetch()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '点赞操作失败')
    }
  }

  const removeComment = async (commentId: number) => {
    if (!asset) return
    try {
      await deleteComment(asset.id, commentId)
      await commentsQuery.refetch()
      message.success('评论已删除')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论删除失败')
    }
  }

  const fileColumns: ColumnsType<AssetFile> = [
    {
      title: '文件',
      dataIndex: 'name',
      render: (value: string, record) => (
        <Space>
          <FileTypeIcon format={record.format} />
          <div>
            <Typography.Text>{value}</Typography.Text>
            <Typography.Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
              {record.role}{record.primary ? ' · 主文件' : ''}
            </Typography.Text>
          </div>
        </Space>
      ),
    },
    { title: '格式', dataIndex: 'format', width: 80 },
    {
      title: '大小',
      dataIndex: 'sizeBytes',
      width: 100,
      render: (value: number) => formatBytes(value),
    },
    {
      title: '操作',
      width: 132,
      render: (_, record) => (
        <Space size={4}>
          <Button
            type="text"
            icon={<EyeOutlined />}
            aria-label={`预览 ${record.name}`}
            disabled={!record.previewable}
          />
          <Button type="text" icon={<DownloadOutlined />} aria-label={`下载 ${record.name}`} />
        </Space>
      ),
    },
  ]

  return (
    <Drawer
      open={assetId !== undefined}
      onClose={onClose}
      width={880}
      destroyOnClose
      loading={assetQuery.isLoading}
      title={
        asset ? (
          <DrawerHeader>
            <AssetNumber>{asset.assetNumber}</AssetNumber>
            <AssetName>{asset.name}</AssetName>
          </DrawerHeader>
        ) : (
          '数模资产详情'
        )
      }
      extra={
        <Space>
          <Button
            type={favoriteQuery.data ? 'primary' : 'default'}
            icon={favoriteQuery.data ? <StarFilled /> : <StarOutlined />}
            loading={favoriteSaving}
            disabled={!asset}
            onClick={() => void toggleFavorite()}
          >
            {favoriteQuery.data ? '已收藏' : '收藏'}
          </Button>
          <Button icon={<FileZipOutlined />} disabled={!asset}>
            打包下载
          </Button>
        </Space>
      }
    >
      {asset && (
        <Tabs
          defaultActiveKey="overview"
          items={[
            {
              key: 'overview',
              label: '概览',
              children: (
                <>
                  <Section>
                    <Flex gap={8} wrap="wrap" style={{ marginBottom: 16 }}>
                      <AssetStatusTag status={asset.status} />
                      <AssetTypeTag type={asset.assetType} />
                      {asset.legacy && <Tag>历史资料</Tag>}
                      {asset.specialties.map((specialty) => (
                        <Tag key={specialty}>{specialty}</Tag>
                      ))}
                    </Flex>
                    <Descriptions column={2} size="small" bordered>
                      <Descriptions.Item label="功能说明" span={2}>
                        {asset.description}
                      </Descriptions.Item>
                      <Descriptions.Item label="负责人">{asset.ownerName}</Descriptions.Item>
                      <Descriptions.Item label="归属部门">{asset.ownerDepartment || '待补充'}</Descriptions.Item>
                      <Descriptions.Item label="适用范围" span={2}>
                        {asset.scopes.map((scope, index) => (
                          <div key={`${scopeLabel(scope)}-${index}`}>{scopeLabel(scope)}</div>
                        ))}
                      </Descriptions.Item>
                      <Descriptions.Item label="标签" span={2}>
                        {asset.tags.map((tag) => <Tag key={tag}>{tag}</Tag>)}
                      </Descriptions.Item>
                    </Descriptions>
                  </Section>
                  <Section>
                    <SectionTitle>资产文件</SectionTitle>
                    <Table
                      rowKey="id"
                      columns={fileColumns}
                      dataSource={asset.files}
                      pagination={false}
                      size="small"
                      locale={{ emptyText: <Empty description="暂无文件" /> }}
                    />
                  </Section>
                  <Section>
                    <SectionTitle>评论 {commentsQuery.data?.length ?? 0}</SectionTitle>
                    <Space direction="vertical" size={10} style={{ width: '100%' }}>
                      <Input.TextArea
                        value={commentDraft}
                        onChange={(event) => setCommentDraft(event.target.value)}
                        placeholder="记录使用反馈或补充说明"
                        autoSize={{ minRows: 2, maxRows: 4 }}
                        maxLength={500}
                        showCount
                      />
                      <Flex justify="flex-end">
                        <Button type="primary" loading={commentSaving} onClick={() => void submitComment()}>
                          发布评论
                        </Button>
                      </Flex>
                      <List
                        size="small"
                        dataSource={commentsQuery.data ?? []}
                        loading={commentsQuery.isLoading}
                        locale={{ emptyText: '暂无评论' }}
                        renderItem={(comment) => (
                          <List.Item
                            actions={comment.deleted ? [] : [
                              <Button
                                key="like"
                                type="text"
                                size="small"
                                icon={<LikeOutlined />}
                                onClick={() => void toggleCommentLike(comment.id)}
                              >
                                {comment.likeCount}
                              </Button>,
                              <Popconfirm key="delete" title="删除这条评论？" onConfirm={() => void removeComment(comment.id)}>
                                <Button type="text" size="small" icon={<DeleteOutlined />} aria-label="删除评论" />
                              </Popconfirm>,
                            ]}
                          >
                            <List.Item.Meta
                              title={comment.deleted ? '评论已删除' : `${comment.authorName} · ${new Intl.DateTimeFormat('zh-CN').format(new Date(comment.createdAt))}`}
                              description={comment.deleted ? '该评论已被删除' : comment.content}
                            />
                          </List.Item>
                        )}
                      />
                    </Space>
                  </Section>
                </>
              ),
            },
            {
              key: 'relations',
              label: `关联资产 ${relationsQuery.data?.length ?? 0}`,
              children: (
                <Space direction="vertical" size={16} style={{ width: '100%' }}>
                  <AssetRelationMap asset={asset} relations={relationsQuery.data ?? []} />
                  <Table
                    rowKey="id"
                    columns={relationColumns}
                    dataSource={relationsQuery.data ?? []}
                    loading={relationsQuery.isLoading}
                    pagination={false}
                    size="small"
                  />
                </Space>
              ),
            },
          ]}
        />
      )}
    </Drawer>
  )
}
