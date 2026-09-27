// Months travel as "YYYY-MM" strings, the same format the backend uses.

// Fixed names rather than toLocaleDateString, whose short forms differ between browsers ("Sep" vs "Sept")
export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

function toKey(year, monthIndex) {
  const date = new Date(year, monthIndex, 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

function parts(month) {
  const [year, monthNumber] = month.split('-').map(Number)
  return { year, monthIndex: monthNumber - 1 }
}

// The current month in the user's own time zone
export function currentMonth() {
  const now = new Date()
  return toKey(now.getFullYear(), now.getMonth())
}

// shiftMonth("2026-01", -1) -> "2025-12"
export function shiftMonth(month, delta) {
  const { year, monthIndex } = parts(month)
  return toKey(year, monthIndex + delta)
}

// "2026-09" -> "September 2026"
export function formatMonth(month) {
  const { year, monthIndex } = parts(month)
  return `${MONTH_NAMES[monthIndex]} ${year}`
}

// "2026-09" -> "Sep"
export function shortMonth(month) {
  return MONTH_NAMES[parts(month).monthIndex].slice(0, 3)
}
