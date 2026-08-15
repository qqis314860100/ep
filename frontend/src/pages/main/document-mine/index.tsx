import { BookOutlined, HeartOutlined } from '@ant-design/icons'
import { Button, Empty, List, Segmented, Space, Spin, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { documentCategoryName, formatDocumentTime } from '../../../features/documents/documentPresentation'
import { getDictionaryItems } from '../../../services/dictionaryService'
import { getMyDocuments, getMyFavoriteDocuments } from '../../../services/documentService'

const Header = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin-bottom: 10px;
  padding: 0 2px;
`

const Title = styled.h1`
  margin: 0 0 2px;
  color: #21322c;
  font-size: 18px;
  font-weight: 650;
`

const Surface = styled.div`
  padding: 0 12px;
  background: #fff;
  border: 1px solid #e4e8e3;
  border-radius: 5px;
`

export default function MyDocumentsPage() {
  const navigate = useNavigate()
  const [tab, setTab] = useState('favorites')
  const [status, setStatus] = useState('')
  const favoritesQuery = useQuery({ queryKey: ['document-my-favorites'], queryFn: getMyFavoriteDocuments })
  const mineQuery = useQuery({ queryKey: ['document-mine', status], queryFn: () => getMyDocuments(status || undefined) })
  const categoriesQuery = useQuery({ queryKey: ['document-categories'], queryFn: getDictionaryItems })
  const categories = categoriesQuery.data ?? []
  const documents = tab === 'favorites' ? favoritesQuery.data ?? [] : mineQuery.data ?? []

  return (
    <>
      <Header>
        <div><Title>我的文档</Title><Typography.Text type="secondary">查看本人收藏和维护的知识文档</Typography.Text></div>
        <Button type="primary" icon={<BookOutlined />} onClick={() => navigate('/documents/new')}>新建文档</Button>
      </Header>
      <Tabs
        activeKey={tab}
        onChange={setTab}
        items={[
          { key: 'favorites', label: '我收藏的', children: null },
          { key: 'mine', label: '我维护的', children: null },
        ]}
        tabBarExtraContent={tab === 'mine' ? (
          <Segmented<string> size="small" value={status} onChange={setStatus}
            options={[{ value: '', label: '全部' }, { value: 'DRAFT', label: '草稿' }, { value: 'PUBLISHED', label: '已发布' }, { value: 'DISABLED', label: '已停用' }]} />
        ) : undefined}
      />
      <Surface>
        {documents.length === 0
          ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tab === 'favorites' ? '还没有收藏文档' : '暂无维护的文档'} style={{ padding: '36px 0' }} />
          : <List
            dataSource={documents}
            loading={tab === 'favorites' ? favoritesQuery.isLoading : mineQuery.isLoading}
            locale={{ emptyText: '暂无数据' }}
            renderItem={(document) => (
              <List.Item key={document.id} onClick={() => navigate(`/documents/${document.id}`)} style={{ cursor: 'pointer' }}>
                <List.Item.Meta
                  avatar={<span style={{ color: '#2f7567', fontSize: 18 }}>{tab === 'favorites' ? <HeartOutlined /> : <BookOutlined />}</span>}
                  title={document.title}
                  description={
                    <Space wrap size={6}>
                      <Typography.Text type="secondary">{document.documentNumber}</Typography.Text>
                      <Tag>{documentCategoryName(document.categoryCode, categories)}</Tag>
                      <Tag color={document.status === 'PUBLISHED' ? 'green' : document.status === 'DRAFT' ? 'default' : 'red'}>
                        {document.status === 'PUBLISHED' ? '已发布' : document.status === 'DRAFT' ? '草稿' : '已停用'}
                      </Tag>
                      <Typography.Text type="secondary">{document.currentVersion.versionNumber}</Typography.Text>
                      <Typography.Text type="secondary">{formatDocumentTime(document.updatedAt)}</Typography.Text>
                    </Space>
                  }
                />
              </List.Item>
            )}
          />}
      </Surface>
      <Spin size="small" style={{ display: 'none' }} />
    </>
  )
}
