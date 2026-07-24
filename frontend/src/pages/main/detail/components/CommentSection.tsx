import {
  DeleteOutlined,
  LikeFilled,
  LikeOutlined,
  PictureOutlined,
  SendOutlined,
} from '@ant-design/icons'
import {
  App as AntdApp,
  Avatar,
  Button,
  Empty,
  Image,
  Input,
  Pagination,
  Popconfirm,
  Skeleton,
  Tooltip,
  Upload,
} from 'antd'
import type { UploadFile } from 'antd'
import { useEffect, useState } from 'react'
import styled from 'styled-components'
import { useComments } from '../../../../hooks/useAssets'
import { addComment, deleteComment, setCommentLike } from '../../../../services/assetService'

const Section = styled.section`
  min-width: 0;
`

const Heading = styled.div`
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
`

const Title = styled.h2`
  margin: 0;
  color: #20312b;
  font-size: 16px;
  font-weight: 650;
`

const Count = styled.span`
  margin-left: 6px;
  color: #7b8982;
  font-size: 12px;
  font-weight: 400;
`

const Composer = styled.div`
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  padding: 14px;
  background: #fff;
  border: 1px solid #dfe5e1;
  border-radius: 6px;

  .ant-input {
    padding: 0;
    border: 0;
    box-shadow: none;
    resize: none;
  }

  .ant-upload-wrapper.ant-upload-picture-card-wrapper .ant-upload-list.ant-upload-list-picture-card {
    gap: 8px;
  }

  .ant-upload-wrapper.ant-upload-picture-card-wrapper .ant-upload-list.ant-upload-list-picture-card .ant-upload-list-item-container,
  .ant-upload-wrapper.ant-upload-picture-card-wrapper .ant-upload.ant-upload-select {
    width: 64px;
    height: 64px;
  }
`

const ComposerBody = styled.div`
  min-width: 0;
`

const ComposerFooter = styled.div`
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
`

const Hint = styled.span`
  color: #87928d;
  font-size: 11px;
`

const Feed = styled.div`
  margin-top: 6px;
`

const PaginationBar = styled.div`
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
`

const CommentItem = styled.article`
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 10px;
  padding: 16px 2px;
  border-bottom: 1px solid #e8ece9;
`

const CommentBody = styled.div`
  min-width: 0;
`

const CommentHeader = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
`

const Author = styled.span`
  color: #2b3c35;
  font-size: 13px;
  font-weight: 650;
`

const Time = styled.time`
  margin-left: 8px;
  color: #8a958f;
  font-size: 11px;
`

const Content = styled.p`
  margin: 7px 0 0;
  color: #4a5852;
  font-size: 13px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
`

const ImageStrip = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-top: 10px;

  .ant-image,
  img {
    display: block;
    width: 76px;
    height: 76px;
    object-fit: cover;
    border-radius: 4px;
  }
`

const CommentActions = styled.div`
  display: flex;
  align-items: center;
  gap: 2px;
  margin-top: 7px;
`

const Deleted = styled.div`
  color: #939d98;
  font-size: 12px;
  font-style: italic;
`

const Loading = styled.div`
  padding: 18px 0;
`

interface CommentSectionProps {
  assetId: number
}

const COMMENTS_PER_PAGE = 5

function formatCommentTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

export function CommentSection({ assetId }: CommentSectionProps) {
  const { message } = AntdApp.useApp()
  const commentsQuery = useComments(assetId)
  const [draft, setDraft] = useState('')
  const [imageFiles, setImageFiles] = useState<UploadFile[]>([])
  const [saving, setSaving] = useState(false)
  const [activeCommentId, setActiveCommentId] = useState<number>()
  const [page, setPage] = useState(1)

  const publish = async () => {
    const images = imageFiles.flatMap((file) => file.originFileObj ? [file.originFileObj] : [])
    if (!draft.trim() && images.length === 0) return message.warning('请输入评论内容或添加图片')
    setSaving(true)
    try {
      await addComment(assetId, draft.trim(), images)
      setDraft('')
      setImageFiles([])
      await commentsQuery.refetch()
      setPage(1)
      message.success('评论已发布')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论发布失败')
    } finally {
      setSaving(false)
    }
  }

  const toggleLike = async (commentId: number, liked: boolean) => {
    setActiveCommentId(commentId)
    try {
      await setCommentLike(assetId, commentId, !liked)
      await commentsQuery.refetch()
    } catch (error) {
      message.error(error instanceof Error ? error.message : '点赞操作失败')
    } finally {
      setActiveCommentId(undefined)
    }
  }

  const removeComment = async (commentId: number) => {
    setActiveCommentId(commentId)
    try {
      await deleteComment(assetId, commentId)
      await commentsQuery.refetch()
      message.success('评论已删除')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评论删除失败')
    } finally {
      setActiveCommentId(undefined)
    }
  }

  const beforeImageUpload = (file: File) => {
    if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
      message.error('仅支持 PNG、JPG、JPEG 和 WEBP 图片')
      return Upload.LIST_IGNORE
    }
    if (file.size > 10 * 1024 * 1024) {
      message.error('单张图片不能超过 10 MB')
      return Upload.LIST_IGNORE
    }
    return false
  }

  const comments = commentsQuery.data ?? []
  const pageCount = Math.max(1, Math.ceil(comments.length / COMMENTS_PER_PAGE))
  const visibleComments = comments.slice((page - 1) * COMMENTS_PER_PAGE, page * COMMENTS_PER_PAGE)

  useEffect(() => {
    setPage(1)
  }, [assetId])

  useEffect(() => {
    if (page > pageCount) setPage(pageCount)
  }, [page, pageCount])

  return (
    <Section>
      <Heading>
        <Title>使用评论 <Count>{comments.length}</Count></Title>
        <Hint>最新发布优先</Hint>
      </Heading>

      <Composer>
        <Avatar size={34} style={{ background: '#2f7567' }}>陈</Avatar>
        <ComposerBody>
          <Input.TextArea
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="记录这份资料的使用反馈、注意事项或补充说明"
            maxLength={500}
            autoSize={{ minRows: 2, maxRows: 5 }}
            aria-label="评论内容"
          />
          <ComposerFooter>
            <Upload
              accept="image/png,image/jpeg,image/webp"
              beforeUpload={beforeImageUpload}
              fileList={imageFiles}
              listType="picture-card"
              maxCount={6}
              multiple
              onChange={({ fileList }) => setImageFiles(fileList.slice(0, 6))}
            >
              {imageFiles.length < 6 && <Tooltip title="添加评论图片"><PictureOutlined /><span className="sr-only">添加评论图片</span></Tooltip>}
            </Upload>
            <Button type="primary" icon={<SendOutlined />} loading={saving} onClick={() => void publish()}>
              发布
            </Button>
          </ComposerFooter>
        </ComposerBody>
      </Composer>

      {commentsQuery.isLoading ? (
        <Loading><Skeleton active avatar paragraph={{ rows: 2 }} /></Loading>
      ) : commentsQuery.isError ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="评论加载失败">
          <Button size="small" onClick={() => void commentsQuery.refetch()}>重试</Button>
        </Empty>
      ) : comments.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无使用反馈" style={{ padding: '22px 0 8px' }} />
      ) : (
        <Feed>
          {visibleComments.map((comment) => (
            <CommentItem key={comment.id}>
              <Avatar size={34} style={{ background: comment.deleted ? '#aeb7b2' : '#607d73' }}>
                {comment.deleted ? '-' : comment.authorName.slice(0, 1)}
              </Avatar>
              <CommentBody>
                {comment.deleted ? (
                  <Deleted>该评论已由作者删除</Deleted>
                ) : (
                  <>
                    <CommentHeader>
                      <div><Author>{comment.authorName}</Author><Time>{formatCommentTime(comment.createdAt)}</Time></div>
                    </CommentHeader>
                    {comment.content && <Content>{comment.content}</Content>}
                    {comment.images.length > 0 && (
                      <Image.PreviewGroup>
                        <ImageStrip>
                          {comment.images.map((image) => <Image key={image.key} src={image.url} alt="评论附件" />)}
                        </ImageStrip>
                      </Image.PreviewGroup>
                    )}
                    <CommentActions>
                      <Button
                        type="text"
                        size="small"
                        icon={comment.likedByCurrentUser ? <LikeFilled /> : <LikeOutlined />}
                        loading={activeCommentId === comment.id}
                        style={{ color: comment.likedByCurrentUser ? '#2f7567' : undefined }}
                        onClick={() => void toggleLike(comment.id, comment.likedByCurrentUser)}
                      >
                        {comment.likeCount || '点赞'}
                      </Button>
                      {comment.canDelete && (
                        <Popconfirm title="删除这条评论？" description="删除后内容不可恢复" okText="删除" cancelText="取消" onConfirm={() => void removeComment(comment.id)}>
                          <Button type="text" size="small" icon={<DeleteOutlined />}>删除</Button>
                        </Popconfirm>
                      )}
                    </CommentActions>
                  </>
                )}
              </CommentBody>
            </CommentItem>
          ))}
          {comments.length > COMMENTS_PER_PAGE && (
            <PaginationBar>
              <Pagination
                current={page}
                pageSize={COMMENTS_PER_PAGE}
                total={comments.length}
                showSizeChanger={false}
                showTotal={(total) => `共 ${total} 条`}
                onChange={setPage}
              />
            </PaginationBar>
          )}
        </Feed>
      )}
    </Section>
  )
}
