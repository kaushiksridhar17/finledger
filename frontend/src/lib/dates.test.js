import { describe, expect, it } from 'vitest'
import { formatDate } from './dates.js'

describe('formatDate', () => {
  it('formats a YYYY-MM-DD date without shifting the day', () => {
    expect(formatDate('2026-09-27')).toBe('27 Sep 2026')
    expect(formatDate('2026-01-01')).toBe('1 Jan 2026')
  })
})
