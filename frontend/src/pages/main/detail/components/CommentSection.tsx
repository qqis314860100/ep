import { DeleteOutlined, LikeOutlined } from '@ant-design/icons'
import { App as AntdApp, Button, Flex, Input, List, Popconfirm, Typography } from 'antd'
import { useState } from 'react'
import styled from 'styled-components'
import { useComments } from '../../../../hooks/useAssets'
import { addComment, deleteComment, setCommentLike } from '../../../../services/assetService'

const Section = styled.section`
  padding: 22px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 8px;
`

interface CommentSectionProps {
  assetId: number
}

export function CommentSection({ assetId }: CommentSectionProps) {
  const { message } = AntdApp.useApp()
  const commentsQuery = useComments(assetId)
  const [draft, setDraft] = useState('')
  const [saving, setSaving] = useState(false)
  const [liked, setLiked] = useState<Set<number>>(new Set())

  const publish = async () => {
    if (!draft.trim()) return message.warning('评论内容不能为空')
    setSaving(true)
    try {
      await addComment(assetId, draft)
      setDraft('')
      await commentsQuery.refetch()
      message.success('评论已发布')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论发布失败')
    } finally {
      setSaving(false)
    }
  }

  const toggleLike = async (commentId: number) => {
    const nextLiked = !liked.has(commentId)
    await setCommentLike(assetId, commentId, nextLiked)
    setLiked((current) => {
      const next = new Set(current)
      if (nextLiked) next.add(commentId)
      else next.delete(commentId)
      return next
    })
    await commentsQuery.refetch()
  }

  return (
    <Section>
      <Typography.Title level={4} style={{ marginTop: 0 }}>使用评论 <Typography.Text type="secondary" style={{ fontSize: 13 }}>{commentsQuery.data?.length ?? 0}</Typography.Text></Typography.Title>
      <Input.TextArea value={draft} onChange={(event) => setDraft(event.target.value)} placeholder="记录使用反馈或补充说明" maxLength={500} showCount autoSize={{ minRows: 3, maxRows: 5 }} />
      <Flex justify="flex-end" style={{ marginTop: 10 }}><Button type="primary" loading={saving} onClick={() => void publish()}>发布评论</Button></Flex>
      <List
        style={{ marginTop: 16 }}
        dataSource={commentsQuery.data ?? []}
        loading={commentsQuery.isLoading}
        locale={{ emptyText: '暂无评论' }}
        renderItem={(comment) => (
          <List.Item actions={comment.deleted ? [] : [
            <Button key="like" type="text" icon={<LikeOutlined />} onClick={() => void toggleLike(comment.id)}>{comment.likeCount}</Button>,
            <Popconfirm key="delete" title="删除这条评论？" onConfirm={() => void deleteComment(assetId, comment.id).then(() => commentsQuery.refetch())}><Button type="text" icon={<DeleteOutlined />} aria-label="删除评论" /></Popconfirm>,
          ]}>
            <List.Item.Meta title={comment.deleted ? '评论已删除' : `${comment.authorName} · ${new Intl.DateTimeFormat('zh-CN').format(new Date(comment.createdAt))}`} description={comment.deleted ? '该评论已被删除' : comment.content} />
          </List.Item>
        )}
      />
    </Section>
  )
}
