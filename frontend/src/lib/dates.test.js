import { describe, expect, it } from 'vitest'
import { formatDate, formatDateTime } from './dates.js'

describe('formatDate', () => {
  it('formats a YYYY-MM-DD date without shifting the day', () => {
    expect(formatDate('2026-09-27')).toBe('27 Sep 2026')
    expect(formatDate('2026-01-01')).toBe('1 Jan 2026')
  })
})

describe('formatDateTime', () => {
  it('shows day, short month and a 12-hour time in the local time zone', () => {
    // Built from local parts, so the test passes in any time zone
    expect(formatDateTime(new Date(2026, 8, 27, 21, 5).toISOString())).toBe('27 Sep, 9:05 pm')
    expect(formatDateTime(new Date(2026, 0, 3, 0, 30).toISOString())).toBe('3 Jan, 12:30 am')
    expect(formatDateTime(new Date(2026, 5, 1, 12, 0).toISOString())).toBe('1 Jun, 12:00 pm')
  })
})
