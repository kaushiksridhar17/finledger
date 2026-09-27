import { describe, expect, it } from 'vitest'
import { formatCompactPaise, formatPaise, paiseToInput, parseRupeesToPaise } from './money.js'

const RUPEE = '₹'

describe('parseRupeesToPaise', () => {
  it('parses whole rupees', () => {
    expect(parseRupeesToPaise('450')).toBe(45000)
  })

  it('parses one and two decimal places exactly', () => {
    expect(parseRupeesToPaise('1299.5')).toBe(129950)
    expect(parseRupeesToPaise('0.1')).toBe(10)
    expect(parseRupeesToPaise('0.07')).toBe(7)
  })

  it('ignores commas, spaces and the rupee sign', () => {
    expect(parseRupeesToPaise('1,23,456.50')).toBe(12345650)
    expect(parseRupeesToPaise(`${RUPEE} 1,000`)).toBe(100000)
  })

  it('keeps the minus sign', () => {
    expect(parseRupeesToPaise('-20.75')).toBe(-2075)
  })

  it('rejects anything that is not a plain amount', () => {
    expect(parseRupeesToPaise('')).toBeNull()
    expect(parseRupeesToPaise('abc')).toBeNull()
    expect(parseRupeesToPaise('1.234')).toBeNull()
    expect(parseRupeesToPaise('1.2.3')).toBeNull()
    expect(parseRupeesToPaise(null)).toBeNull()
  })
})

describe('formatPaise', () => {
  it('uses Indian digit grouping and two decimals', () => {
    expect(formatPaise(12345650)).toBe(`${RUPEE}1,23,456.50`)
    expect(formatPaise(5)).toBe(`${RUPEE}0.05`)
  })
})

describe('paiseToInput', () => {
  it('turns paise back into an editable rupee string', () => {
    expect(paiseToInput(123450)).toBe('1234.50')
    expect(paiseToInput(-2075)).toBe('20.75')
    expect(paiseToInput(7)).toBe('0.07')
  })
})

describe('formatCompactPaise', () => {
  it('shortens thousands to k and lakhs to L', () => {
    expect(formatCompactPaise(45000)).toBe(`${RUPEE}450`)
    expect(formatCompactPaise(8500000)).toBe(`${RUPEE}85k`)
    expect(formatCompactPaise(1250000)).toBe(`${RUPEE}12.5k`)
    expect(formatCompactPaise(12000000)).toBe(`${RUPEE}1.2L`)
    expect(formatCompactPaise(0)).toBe(`${RUPEE}0`)
  })
})
