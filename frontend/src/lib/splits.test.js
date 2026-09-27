import { describe, expect, it } from 'vitest'
import { computeSplit, largestRemainder, parsePercent, percentText } from './splits.js'

const people = (...ids) => ids.map((memberId) => ({ memberId, included: true, input: '' }))

describe('largestRemainder', () => {
  it('always adds up to the total, giving spare paise to the first people', () => {
    expect(largestRemainder(10000, [1, 1, 1])).toEqual([3334, 3333, 3333])
    expect(largestRemainder(9999, [3333, 3333, 3334])).toEqual([3333, 3333, 3333])
    expect(largestRemainder(300000, [2, 1, 0])).toEqual([200000, 100000, 0])
  })
})

describe('computeSplit', () => {
  it('splits equally between the people who are ticked', () => {
    const everyone = people(1, 2, 3)
    everyone[2].included = false
    expect(computeSplit(10001, 'EQUAL', everyone)).toEqual({
      shares: [
        { memberId: 1, sharePaise: 5001, value: null },
        { memberId: 2, sharePaise: 5000, value: null },
      ],
      error: null,
    })
  })

  it('checks exact amounts and percentages add up, and leaves out blanks', () => {
    const exact = people(1, 2, 3)
    exact[0].input = '1,500'
    exact[1].input = '400'
    expect(computeSplit(200000, 'EXACT', exact).error).toBe('The amounts add up to ₹1,900.00, but the expense is ₹2,000.00')

    exact[1].input = '500'
    expect(computeSplit(200000, 'EXACT', exact).shares).toEqual([
      { memberId: 1, sharePaise: 150000, value: 150000 },
      { memberId: 2, sharePaise: 50000, value: 50000 },
    ])

    const percent = people(1, 2)
    percent[0].input = '50'
    percent[1].input = '40'
    expect(computeSplit(10000, 'PERCENT', percent).error).toBe('The percentages add up to 90% instead of 100%')
  })

  it('splits by shares and refuses nonsense', () => {
    const nights = people(1, 2)
    nights[0].input = '2'
    nights[1].input = '1'
    expect(computeSplit(300000, 'SHARES', nights).shares.map((s) => s.sharePaise)).toEqual([200000, 100000])

    nights[1].input = 'abc'
    expect(computeSplit(300000, 'SHARES', nights).error).toBe('Shares must be whole numbers, like 1 or 2')
    expect(computeSplit(0, 'EQUAL', people(1)).error).toBe('Enter the amount first')
  })
})

describe('percentages', () => {
  it('parse to basis points and print back the way people write them', () => {
    expect(parsePercent('33.33')).toBe(3333)
    expect(parsePercent('50%')).toBe(5000)
    expect(parsePercent('100.5')).toBeNull()
    expect(parsePercent('1.234')).toBeNull()
    expect(percentText(3333)).toBe('33.33')
    expect(percentText(3350)).toBe('33.5')
    expect(percentText(9000)).toBe('90')
  })
})
