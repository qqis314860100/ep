import { DeleteOutlined, LikeOutlined, SendOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, App as AntdApp, Avatar, Button, Input, Space, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import styled from 'styled-components'
import {
  addDocumentComment,
  getDocumentComments,
  likeDocumentComment,
  removeDocumentComment,
  uploadDocumentFile,
} from '../../../services/documentService'
import type { DocumentComment } from '../../../services/documentService'

const Section = styled.section`
  margin-top: 14px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid #dfe6e2;
  border-radius: 6px;
`

const Heading = styled.h2`
  margin: 0 0 10px;
  color: #2a3b34;
  font-size: 14px;
  font-weight: 650;
`

const Item = styled.div`
  display: flex;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed #edf0ee;

  &:last-child {
    border-bottom: 0;
  }
`

const Body = styled.div`
  flex: 1;
  min-width: 0;
`

const Author = styled.div`
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
`

const Content = styled.div`
  margin-top: 4px;
  color: #3f4d46;
  font-size: 13px;
  line-height: 1.6;
`

const Meta = styled.div`
  margin-top: 5px;
  color: #8b9590;
  font-size: 11px;
`

const Composer = styled.div`
  display: flex;
  gap: 8px;
  align-items: flex-end;
  margin-top: 10px;
`

interface DocumentCommentSectionProps {
  documentId: number
  currentVersionId: number
}

export function DocumentCommentSection({ documentId, currentVersionId }: DocumentCommentSectionProps) {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [content, setContent] = useState('')
  const [imageKeys, setImageKeys] = useState<string[]>([])
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)
  const commentsQuery = useQuery({ queryKey: ['document-comments', documentId], queryFn: () => getDocumentComments(documentId) })
  const refresh = async () => { await queryClient.invalidateQueries({ queryKey: ['document-comments', documentId] }) }

  const addMutation = useMutation({
    mutationFn: () => addDocumentComment(documentId, { versionId: currentVersionId, content: content.trim(), imageKeys }),
    onSuccess: async () => {
      await refresh()
      setContent('')
      setImageKeys([])
    },
    onError: (error) => void message.error(error instanceof Error ? error.message : '评论失败'),
  })
  const likeMutation = useMutation({
    mutationFn: ({ comment, liked }: { comment: DocumentComment; liked: boolean }) => likeDocumentComment(documentId, comment.id, liked),
    onSuccess: async () => { await refresh() },
  })
  const removeMutation = useMutation({
    mutationFn: (comment: DocumentComment) => removeDocumentComment(documentId, comment.id),
    onSuccess: async () => { await refresh(); void message.success('评论已删除') },
    onError: (error) => void message.error(error instanceof Error ? error.message : '删除失败'),
  })

  const pickImage = async (file: File) => {
    setUploading(true)
    try {
      const uploaded = await uploadDocumentFile(file)
      if (imageKeys.length >= 6) {
        void message.warning('评论图片最多 6 张')
        return
      }
      setImageKeys((keys) => [...keys, uploaded.storageKey])
    } catch (error) {
      void message.error(error instanceof Error ? error.message : '图片上传失败')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Section aria-label="文档评论">
      <Heading>评论与讨论</Heading>
      {commentsQuery.isError && <Alert type="error" showIcon message="评论加载失败" style={{ marginBottom: 10 }} />}
      {(commentsQuery.data ?? []).filter((comment) => !comment.deleted).map((comment) => (
        <Item key={comment.id}>
          <Avatar size={30} style={{ background: '#e6efeb', color: '#2f7567' }}>{comment.authorName.slice(0, 1)}</Avatar>
          <Body>
            <Author>
              <Typography.Text strong style={{ fontSize: 12 }}>{comment.authorName}</Typography.Text>
              <Space size={4}>
                <Tag style={{ fontSize: 10, margin: 0 }}>版本 {comment.versionId === currentVersionId ? '当前' : '历史'}</Tag>
                <Button
                  type="text"
                  size="small"
                  icon={<LikeOutlined />}
                  aria-label={comment.likedByCurrentUser ? '取消点赞' : '点赞'}
                  style={{ color: comment.likedByCurrentUser ? '#2f7567' : undefined }}
                  onClick={() => likeMutation.mutate({ comment, liked: !comment.likedByCurrentUser })}
                >{comment.likeCount}</Button>
                <Button type="text" size="small" danger icon={<DeleteOutlined />} aria-label="删除评论" onClick={() => removeMutation.mutate(comment)} />
              </Space>
            </Author>
            <Content>{comment.content}</Content>
            <Meta>{new Date(comment.createdAt).toLocaleString('zh-CN', { hour12: false })}</Meta>
          </Body>
        </Item>
      ))}
      {(commentsQuery.data ?? []).length === 0 && !commentsQuery.isLoading && (
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>还没有评论，发表第一条讨论吧</Typography.Text>
      )}
      <Composer>
        <Input.TextArea rows={2} placeholder="发表评论（评论将记录当前查看的版本）" value={content} maxLength={500}
          onChange={(event) => setContent(event.target.value)} />
        <input ref={fileInput} type="file" accept="image/*" hidden
          onChange={(event) => { const file = event.target.files?.[0]; if (file) void pickImage(file); event.target.value = '' }} />
        <Space direction="vertical" size={4}>
          {imageKeys.length > 0 && <Tag color="green">{imageKeys.length} 张图片</Tag>}
          <Button icon={<UploadOutlined />} loading={uploading} onClick={() => fileInput.current?.click()}>图片</Button>
        </Space>
        <Button type="primary" icon={<SendOutlined />} loading={addMutation.isPending}
          disabled={!content.trim()} onClick={() => addMutation.mutate()}>发表</Button>
      </Composer>
    </Section>
  )
}
