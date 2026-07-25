import { FileTextOutlined, FolderOpenOutlined } from '@ant-design/icons'
import type { DictionaryItem } from '../../../types/dictionary'
import styled from 'styled-components'

const Rail = styled.aside`
  min-width: 0;
  background: #fafcfb;
  border-right: 1px solid #dfe5e2;
`

const RailHeader = styled.div`
  display: flex;
  align-items: center;
  height: 44px;
  padding: 0 14px;
  color: #44534c;
  border-bottom: 1px solid #e5eae7;
  font-size: 12px;
  font-weight: 650;
`

const RailBody = styled.div`
  padding: 8px;
`

const CategoryButton = styled.button<{ $active: boolean }>`
  position: relative;
  display: grid;
  grid-template-columns: 28px minmax(0, 1fr) auto;
  align-items: center;
  width: 100%;
  min-height: 37px;
  padding: 0 9px 0 5px;
  color: ${({ $active }) => ($active ? '#245f54' : '#4e5c56')};
  text-align: left;
  background: ${({ $active }) => ($active ? '#e9f2ef' : 'transparent')};
  border: 0;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  font-weight: ${({ $active }) => ($active ? 650 : 400)};

  &::before {
    position: absolute;
    inset: 7px auto 7px 0;
    width: 3px;
    content: '';
    background: ${({ $active }) => ($active ? '#2f7567' : 'transparent')};
  }

  .anticon {
    justify-self: center;
    color: ${({ $active }) => ($active ? '#2f7567' : '#7c8983')};
  }

  &:hover,
  &:focus-visible {
    background: ${({ $active }) => ($active ? '#e9f2ef' : '#f0f4f2')};
    outline: none;
  }
`

const Count = styled.span`
  color: #89958f;
  font-size: 10px;
`

interface DocumentCategoryNavProps {
  categories: DictionaryItem[]
  selected: string
  total: number
  onSelect: (code: string) => void
}

export function DocumentCategoryNav({ categories, selected, total, onSelect }: DocumentCategoryNavProps) {
  return (
    <Rail aria-label="文档分类">
      <RailHeader>文档分类</RailHeader>
      <RailBody>
        <CategoryButton
          type="button"
          $active={!selected}
          aria-current={!selected ? 'page' : undefined}
          aria-label={`全部文档 ${total}`}
          onClick={() => onSelect('')}
        >
          <FolderOpenOutlined />
          <span>全部文档</span>
          <Count>{total}</Count>
        </CategoryButton>
        {categories.map((category) => (
          <CategoryButton
            key={category.code}
            type="button"
            $active={selected === category.code}
            aria-current={selected === category.code ? 'page' : undefined}
            aria-label={category.name}
            onClick={() => onSelect(category.code)}
          >
            <FileTextOutlined />
            <span>{category.name}</span>
          </CategoryButton>
        ))}
      </RailBody>
    </Rail>
  )
}
