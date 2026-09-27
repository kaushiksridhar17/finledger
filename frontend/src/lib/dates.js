// Today's date as YYYY-MM-DD in the user's own time zone (what <input type="date"> expects)
export function todayIso() {
  return new Date().toLocaleDateString('en-CA')
}

// "2026-09-27" -> "27 Sep 2026". Parsed as a plain date so time zones can't shift it by a day.
export function formatDate(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  })
}
