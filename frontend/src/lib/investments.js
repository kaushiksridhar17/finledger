// Formatting for mutual fund numbers. Money stays in paise (see money.js); units and NAVs arrive as
// plain numbers with 3 and up to 5 decimal places, and are only ever displayed, never added up here.

// 0.12345 -> "+12.35%", -0.031 -> "-3.10%", null -> "-" (not enough history for a yearly rate)
export function formatRate(rate) {
  if (rate === null || rate === undefined || !Number.isFinite(rate)) return '-'
  const percent = rate * 100
  const sign = percent > 0 ? '+' : ''
  return `${sign}${percent.toFixed(2)}%`
}

// Gain as a share of what's invested: (200000, 1000000) -> "+20.00%"
export function gainPercent(gainPaise, investedPaise) {
  if (!investedPaise) return '-'
  return formatRate(gainPaise / investedPaise)
}

// 55.581 -> "55.581"
export function formatUnits(units) {
  return Number(units).toFixed(3)
}

// 89.958 -> "₹89.9580". NAVs are usually quoted to 4 decimal places.
export function formatNav(nav) {
  return `₹${Number(nav).toFixed(4)}`
}

// "Parag Parikh Flexi Cap Fund - Direct Plan - Growth" -> { fund: "Parag Parikh Flexi Cap Fund", plan: "Direct Plan - Growth" }
export function splitFundName(name) {
  const [fund, ...rest] = name.split(' - ')
  return { fund, plan: rest.join(' - ') }
}
