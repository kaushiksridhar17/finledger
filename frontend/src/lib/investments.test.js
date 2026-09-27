import { describe, expect, it } from 'vitest'
import { formatNav, formatRate, formatUnits, gainPercent, splitFundName } from './investments.js'

describe('formatRate', () => {
  it('shows a signed percentage with two decimals, or a dash when there is none', () => {
    expect(formatRate(0.12345)).toBe('+12.35%')
    expect(formatRate(-0.031)).toBe('-3.10%')
    expect(formatRate(0)).toBe('0.00%')
    expect(formatRate(null)).toBe('-')
    expect(gainPercent(200000, 1000000)).toBe('+20.00%')
    expect(gainPercent(0, 0)).toBe('-')
  })
})

describe('fund numbers', () => {
  it('formats units to 3 places, NAVs to 4, and splits the plan off the name', () => {
    expect(formatUnits(55.581)).toBe('55.581')
    expect(formatUnits(100)).toBe('100.000')
    expect(formatNav(89.958)).toBe('₹89.9580')
    expect(splitFundName('Parag Parikh Flexi Cap Fund - Direct Plan - Growth')).toEqual({
      fund: 'Parag Parikh Flexi Cap Fund',
      plan: 'Direct Plan - Growth',
    })
    expect(splitFundName('Some Fund')).toEqual({ fund: 'Some Fund', plan: '' })
  })
})
