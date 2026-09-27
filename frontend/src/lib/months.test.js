import { describe, expect, it } from 'vitest'
import { formatMonth, shiftMonth, shortMonth } from './months.js'

describe('shiftMonth', () => {
  it('moves forwards and backwards across year boundaries', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2025-12', 1)).toBe('2026-01')
    expect(shiftMonth('2026-09', -11)).toBe('2025-10')
    expect(shiftMonth('2026-09', 0)).toBe('2026-09')
  })
})

describe('formatMonth and shortMonth', () => {
  it('turns YYYY-MM into readable text', () => {
    expect(formatMonth('2026-09')).toBe('September 2026')
    expect(shortMonth('2026-09')).toBe('Sep')
  })
})
