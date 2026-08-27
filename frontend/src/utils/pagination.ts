export interface PaginationState {
  page: number
  size: number
  total: number
}

export function createPagination(size = 20): PaginationState {
  return { page: 1, size, total: 0 }
}

export function clampPage(page: number, total: number, size: number): number {
  const lastPage = Math.max(Math.ceil(total / Math.max(size, 1)), 1)
  return Math.min(Math.max(page, 1), lastPage)
}
