import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { searchDocuments } from '../../services/documentService'

const perPageOptions = [10, 20, 50]
const defaultPerPage = 20

export function useDocumentSearch() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? ''
  const parsedPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(parsedPage) && parsedPage > 0 ? Math.floor(parsedPage) : 1
  const parsedSize = Number(searchParams.get('size') ?? '')
  const perPage = perPageOptions.includes(parsedSize) ? parsedSize : defaultPerPage
  const params = { query, category, page, perPage }
  const result = useQuery({
    queryKey: ['documents', params],
    queryFn: () => searchDocuments(params),
    placeholderData: (previousData) => previousData,
  })

  const replaceFilters = (next: { query?: string; category?: string; page?: number; size?: number }) => {
    const updated = new URLSearchParams()
    const nextQuery = next.query ?? query
    const nextCategory = next.category ?? category
    const nextSize = next.size ?? perPage
    const nextPage = next.page ?? 1
    if (nextQuery) updated.set('q', nextQuery)
    if (nextCategory) updated.set('category', nextCategory)
    if (nextSize !== defaultPerPage) updated.set('size', String(nextSize))
    if (nextPage > 1) updated.set('page', String(nextPage))
    setSearchParams(updated)
  }

  return {
    query,
    category,
    page,
    perPage,
    result,
    setQuery: (value: string) => replaceFilters({ query: value, page: 1 }),
    setCategory: (value: string) => replaceFilters({ category: value, page: 1 }),
    setPage: (value: number) => replaceFilters({ page: value }),
    setPageSize: (value: number) => replaceFilters({ size: value, page: 1 }),
    clear: () => setSearchParams(new URLSearchParams()),
  }
}
