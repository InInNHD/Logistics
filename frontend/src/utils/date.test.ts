import { describe, expect, it } from 'vitest'
import { recentDayLabels, toLocalDateTimeValue } from './date'

describe('date utilities', () => {
  it('serializes a date with local calendar fields', () => {
    const localDate = new Date(2026, 0, 2, 3, 4, 5)
    expect(toLocalDateTimeValue(localDate)).toBe('2026-01-02T03:04:05')
  })

  it('builds dynamic labels ending in today', () => {
    const labels = recentDayLabels(7, new Date(2026, 7, 16, 12))
    expect(labels).toHaveLength(7)
    expect(labels[labels.length - 1]).toBe('今天')
    expect(new Set(labels.slice(0, -1)).size).toBe(6)
  })
})
