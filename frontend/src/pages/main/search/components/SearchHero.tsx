import { SearchOutlined } from '@ant-design/icons'
import { Button, Input } from 'antd'
import styled from 'styled-components'

const Hero = styled.section`
  padding: 24px 30px 22px;
  color: #fff;
  background: #214f43;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(33, 79, 67, 0.14);
`

const Title = styled.h1`
  margin: 0 0 7px;
  color: #fff;
  font-size: 25px;
  font-weight: 650;
`

const Copy = styled.p`
  max-width: 620px;
  margin: 0;
  color: #c7ded5;
  font-size: 14px;
  line-height: 1.7;
`

const SearchRow = styled.div`
  display: flex;
  gap: 10px;
  max-width: 780px;
  margin-top: 16px;

  .ant-input-affix-wrapper {
    height: 46px;
    padding-inline: 16px;
    background: #fff;
    border: 0;
    border-radius: 7px;
  }

  .ant-btn {
    height: 46px;
    padding-inline: 24px;
    color: #214f43;
    background: #e7b56e;
    border: 0;
    border-radius: 7px;
    font-weight: 600;
  }
`

const QuickLinks = styled.div`
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
`

const QuickLink = styled.button`
  padding: 5px 10px;
  color: #d4e6df;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 5px;
  cursor: pointer;
  font-size: 12px;
`

interface SearchHeroProps {
  query: string
  onQueryChange: (value: string) => void
  onSearch: () => void
  onQuickSearch: (value: string) => void
}

export function SearchHero({ query, onQueryChange, onSearch, onQuickSearch }: SearchHeroProps) {
  return (
    <Hero>
      <Title>找到正在使用的图纸和数模</Title>
      <Copy>按资料编号、名称、功能说明、基地和拉线快速定位生产资料。已经应用于产线的文件，在这里集中保存、预览和复用。</Copy>
      <SearchRow>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="搜索资料编号、名称、功能说明或文件名"
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          onPressEnter={onSearch}
        />
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>搜索</Button>
      </SearchRow>
      <QuickLinks aria-label="常用搜索">
        {['可预览资料', '三维模型', 'PDF 图纸', '标准设备模块'].map((item) => (
          <QuickLink key={item} onClick={() => onQuickSearch(item)}>{item}</QuickLink>
        ))}
      </QuickLinks>
    </Hero>
  )
}
