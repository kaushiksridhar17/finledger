// Working out who owes what for a shared expense, in whole paise. This mirrors the backend's
// SplitCalculator so the form can show each person's share live, before anything is saved.
// The backend always re-checks and has the final say.

import { formatPaise } from './money.js'

export const SPLIT_TYPES = [
  { value: 'EQUAL', label: 'Equally' },
  { value: 'EXACT', label: 'Exact amounts' },
  { value: 'PERCENT', label: 'Percentages' },
  { value: 'SHARES', label: 'Shares' },
]

export const PERCENT_SCALE = 10000 // 100% in basis points: 33.33% is 3333
export const MAX_SHARE_WEIGHT = 1000

// Splits total in proportion to weights. Everyone gets their rounded-down share, then the leftover
// paise go one each to the biggest remainders (ties to whoever comes first), so the shares always
// add up to the total exactly.
export function largestRemainder(totalPaise, weights) {
  const weightSum = weights.reduce((sum, w) => sum + w, 0)
  const shares = weights.map((w) => Math.floor((totalPaise * w) / weightSum))
  const remainders = weights.map((w, i) => totalPaise * w - shares[i] * weightSum)
  let leftover = totalPaise - shares.reduce((sum, s) => sum + s, 0)

  const order = weights.map((_, i) => i).sort((a, b) => remainders[b] - remainders[a] || a - b)
  for (let k = 0; leftover > 0; k++, leftover--) shares[order[k]] += 1
  return shares
}

// "33.33" -> 3333 basis points. Up to two decimals, from 0 to 100. Returns null if it isn't one.
export function parsePercent(text) {
  const cleaned = String(text ?? '').replace('%', '').trim()
  if (!/^\d{1,3}(\.\d{1,2})?$/.test(cleaned)) return null
  const [whole, fraction = ''] = cleaned.split('.')
  const basisPoints = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
  return basisPoints <= PERCENT_SCALE ? basisPoints : null
}

// 3333 -> "33.33", 5000 -> "50", 3350 -> "33.5"
export function percentText(basisPoints) {
  const whole = Math.floor(basisPoints / 100)
  const rest = basisPoints % 100
  if (rest === 0) return String(whole)
  return `${whole}.${String(rest).padStart(2, '0').replace(/0$/, '')}`
}

// A whole number of shares from 0 to 1000, or null
export function parseShares(text) {
  const cleaned = String(text ?? '').trim()
  if (!/^\d{1,4}$/.test(cleaned)) return null
  const n = Number(cleaned)
  return n <= MAX_SHARE_WEIGHT ? n : null
}

// people: [{ memberId, included, input }] in member order.
//   EQUAL uses `included`; the others read `input` (rupees, a percentage or a number of shares),
//   where blank means 0 (not part of this expense).
// Returns { shares: [{ memberId, sharePaise, value }], error }, where shares only lists people with
// something to pay and value is what the API expects for that split type.
export function computeSplit(totalPaise, splitType, people) {
  if (!Number.isSafeInteger(totalPaise) || totalPaise <= 0) {
    return { shares: [], error: 'Enter the amount first' }
  }

  if (splitType === 'EQUAL') {
    const included = people.filter((p) => p.included)
    if (included.length === 0) return { shares: [], error: 'Choose who this expense is split between' }
    const amounts = largestRemainder(totalPaise, included.map(() => 1))
    return {
      shares: included.map((p, i) => ({ memberId: p.memberId, sharePaise: amounts[i], value: null })),
      error: null,
    }
  }

  const parse = { EXACT: parseRupeeInput, PERCENT: parsePercent, SHARES: parseShares }[splitType]
  const values = []
  for (const person of people) {
    const text = String(person.input ?? '').trim()
    const value = text === '' ? 0 : parse(text)
    if (value === null) return { shares: [], error: INVALID_INPUT[splitType] }
    values.push(value)
  }

  const inSplit = people.map((p, i) => ({ memberId: p.memberId, value: values[i] })).filter((p) => p.value > 0)
  if (inSplit.length === 0) return { shares: [], error: 'Choose who this expense is split between' }
  const sum = inSplit.reduce((total, p) => total + p.value, 0)

  if (splitType === 'EXACT') {
    if (sum !== totalPaise) {
      return {
        shares: [],
        error: `The amounts add up to ${formatPaise(sum)}, but the expense is ${formatPaise(totalPaise)}`,
      }
    }
    return { shares: inSplit.map((p) => ({ ...p, sharePaise: p.value })), error: null }
  }

  if (splitType === 'PERCENT' && sum !== PERCENT_SCALE) {
    return { shares: [], error: `The percentages add up to ${percentText(sum)}% instead of 100%` }
  }

  const amounts = largestRemainder(totalPaise, inSplit.map((p) => p.value))
  return { shares: inSplit.map((p, i) => ({ ...p, sharePaise: amounts[i] })), error: null }
}

const INVALID_INPUT = {
  EXACT: 'Amounts must look like 450 or 1,299.50',
  PERCENT: 'Percentages must be between 0 and 100, like 25 or 33.33',
  SHARES: 'Shares must be whole numbers, like 1 or 2',
}

// "1,299.50" -> 129950. Same rules as parseRupeesToPaise, but never negative.
function parseRupeeInput(text) {
  const cleaned = text.replaceAll(',', '').replaceAll('₹', '').replaceAll(' ', '')
  if (!/^\d+(\.\d{1,2})?$/.test(cleaned)) return null
  const [whole, fraction = ''] = cleaned.split('.')
  return Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
}
