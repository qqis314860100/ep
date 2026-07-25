import { FileAddOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Input, Pagination, Skeleton } from 'antd'
import { type FormEvent, useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import styled from 'styled-components'
import { getDictionaryItems } from '../../services/dictionaryService'
import { DocumentCategoryNav } from './components/DocumentCategoryNav'
import { DocumentResultList } from './components/DocumentResultList'
import { useDocumentSearch } from './useDocumentSearch'

const Page = styled.div`
  min-width: 0;
`

const PageBar = styled.header`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 2px 10px;
`

const Heading = styled.h1`
  margin: 0;
  color: #22312b;
  font-size: 18px;
  font-weight: 680;
`

const Meta = styled.div`
  margin-top: 2px;
  color: #7d8883;
  font-size: 11px;
`

const SearchBar = styled.form`
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 7px 10px;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-bottom: 0;
  border-radius: 5px 5px 0 0;

  .ant-input-affix-wrapper {
    width: min(620px, 60vw);
  }
`

const Workspace = styled.div`
  display: grid;
  grid-template-columns: 216px minmax(0, 1fr);
  min-height: calc(100vh - 166px);
  overflow: hidden;
  background: #fff;
  border: 1px solid #dfe5e2;
  border-radius: 0 0 5px 5px;

  @media (max-width: 1080px) {
    grid-template-columns: 196px minmax(0, 1fr);
  }
`

const Main = styled.section`
  min-width: 0;
`

const ResultsBar = styled.div`
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 14px;
  border-bottom: 1px solid #e5eae7;
`

const ResultTitle = styled.div`
  color: #33433b;
  font-size: 13px;
  font-weight: 650;

  span {
    margin-left: 7px;
    color: #89958f;
    font-size: 11px;
    font-weight: 400;
  }
`

const StateArea = styled.div`
  padding: 18px;
`

const Footer = styled.div`
  display: flex;
  justify-content: flex-end;
  padding: 12px 14px;
  border-top: 1px solid #edf0ee;
`

export default function DocumentSearchPage() {
  const navigate = useNavigate()
  const search = useDocumentSearch()
  const [queryInput, setQueryInput] = useState(search.query)
  useEffect(() => setQueryInput(search.query), [search.query])
  const categoryQuery = useQuery({ queryKey: ['document-categories'], queryFn: getDictionaryItems })
  const categories = useMemo(() => (categoryQuery.data ?? [])
    .filter((item) => item.category === 'DOCUMENT_CATEGORY' && item.status === 'ENABLED')
    .sort((left, right) => left.sortOrder - right.sortOrder), [categoryQuery.data])
  const page = search.result.data
  const error = search.result.error instanceof Error ? search.result.error.message : '文档检索失败'

  const submit = (event: FormEvent) => {
    event.preventDefault()
    search.setQuery(queryInput.trim())
  }

  return (
    <Page>
      <PageBar>
        <div>
          <Heading>文档中心</Heading>
          <Meta>规范、说明书与作业资料的统一检索入口</Meta>
        </div>
        <Button type="primary" icon={<FileAddOutlined />} onClick={() => navigate('/documents/new')}>新建文档</Button>
      </PageBar>
      <SearchBar role="search" onSubmit={submit}>
        <Input
          type="search"
          value={queryInput}
          prefix={<SearchOutlined />}
          placeholder="搜索标题、编号、摘要或文件名"
          aria-label="搜索文档"
          onChange={(event) => setQueryInput(event.target.value)}
        />
        <Button htmlType="submit" type="primary">搜索</Button>
        {(search.query || search.category) && <Button type="link" onClick={search.clear}>清空筛选</Button>}
      </SearchBar>
      <Workspace>
        <DocumentCategoryNav
          categories={categories}
          selected={search.category}
          total={page?.meta.total ?? 0}
          onSelect={search.setCategory}
        />
        <Main>
          <ResultsBar>
            <ResultTitle>已发布文档<span>{page?.meta.total ?? 0} 条</span></ResultTitle>
          </ResultsBar>
          {search.result.isLoading && (
            <StateArea><Skeleton active paragraph={{ rows: 8 }} title={false} /></StateArea>
          )}
          {search.result.isError && (
            <StateArea>
              <Alert
                type="error"
                showIcon
                message="无法加载文档"
                description={error}
                action={<Button aria-label="重新加载" icon={<ReloadOutlined />} onClick={() => search.result.refetch()}>重新加载</Button>}
              />
            </StateArea>
          )}
          {page && !search.result.isError && (
            <>
              <DocumentResultList documents={page.data} categories={categories} onOpen={(id) => navigate(`/documents/${id}`)} />
              {page.meta.totalPages > 1 && (
                <Footer>
                  <Pagination
                    size="small"
                    current={page.meta.page}
                    pageSize={page.meta.perPage}
                    total={page.meta.total}
                    showSizeChanger={false}
                    onChange={search.setPage}
                  />
                </Footer>
              )}
            </>
          )}
        </Main>
      </Workspace>
    </Page>
  )
}
