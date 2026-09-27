// Money travels between frontend and backend as whole paise (integers), never as rupees with decimals.
// Floating point can't store amounts like 0.1 exactly, so all arithmetic stays in integer paise.

const RUPEE = '₹'

const formatter = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

// 12345650 -> "Rs 1,23,456.50" (with the rupee sign and Indian digit grouping)
export function formatPaise(paise) {
  return formatter.format(paise / 100)
}

// Text a person typed -> paise. Accepts "450", "1,299.5", "-20.75", "Rs 1,000".
// Returns null when it isn't a valid amount with at most 2 decimal places.
// Works on the text itself, so "0.1" becomes exactly 10 paise.
export function parseRupeesToPaise(text) {
  const cleaned = String(text ?? '')
    .replaceAll(RUPEE, '')
    .replaceAll(',', '')
    .replaceAll(' ', '')

  if (!/^-?\d+(\.\d{1,2})?$/.test(cleaned)) return null

  const negative = cleaned.startsWith('-')
  const [whole, fraction = ''] = cleaned.replace('-', '').split('.')
  const paise = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))

  if (!Number.isSafeInteger(paise)) return null
  return negative ? -paise : paise
}

// 123450 -> "1234.50", for putting an existing amount back into an input box. Always positive.
export function paiseToInput(paise) {
  const abs = Math.abs(paise)
  return `${Math.floor(abs / 100)}.${String(abs % 100).padStart(2, '0')}`
}
