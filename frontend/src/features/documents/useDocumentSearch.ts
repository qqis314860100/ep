import { useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { searchDocuments } from '../../services/documentService'

const perPage = 20

export function useDocumentSearch() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = searchParams.get('q') ?? ''
  const category = searchParams.get('category') ?? ''
  const parsedPage = Number(searchParams.get('page') ?? '1')
  const page = Number.isFinite(parsedPage) && parsedPage > 0 ? Math.floor(parsedPage) : 1
  const params = { query, category, page, perPage }
  const result = useQuery({
    queryKey: ['documents', params],
    queryFn: () => searchDocuments(params),
    placeholderData: (previousData) => previousData,
  })

  const replaceFilters = (next: { query?: string; category?: string; page?: number }) => {
    const updated = new URLSearchParams()
    const nextQuery = next.query ?? query
    const nextCategory = next.category ?? category
    const nextPage = next.page ?? 1
    if (nextQuery) updated.set('q', nextQuery)
    if (nextCategory) updated.set('category', nextCategory)
    if (nextPage > 1) updated.set('page', String(nextPage))
    setSearchParams(updated)
  }

  return {
    query,
    category,
    page,
    result,
    setQuery: (value: string) => replaceFilters({ query: value, page: 1 }),
    setCategory: (value: string) => replaceFilters({ category: value, page: 1 }),
    setPage: (value: number) => replaceFilters({ page: value }),
    clear: () => setSearchParams(new URLSearchParams()),
  }
}
