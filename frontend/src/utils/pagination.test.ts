import { describe, expect, it } from 'vitest'
import { clampPage, createPagination } from './pagination'

describe('pagination utilities', () => {
  it('creates a one-based page state', () => {
    expect(createPagination(50)).toEqual({ page: 1, size: 50, total: 0 })
  })

  it('clamps pages after filters reduce the result count', () => {
    expect(clampPage(8, 31, 20)).toBe(2)
    expect(clampPage(0, 0, 20)).toBe(1)
  })
})
