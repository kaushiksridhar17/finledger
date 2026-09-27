import { MONTH_NAMES } from './months.js'

// Today's date as YYYY-MM-DD in the user's own time zone (what <input type="date"> expects)
export function todayIso() {
  return new Date().toLocaleDateString('en-CA')
}

// "2026-09-27" -> "27 Sep 2026". Read straight from the text so time zones can't shift it by a day.
export function formatDate(isoDate) {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${day} ${MONTH_NAMES[month - 1].slice(0, 3)} ${year}`
}

// A timestamp in the user's time zone: "27 Sep, 9:05 pm". Built by hand because browsers disagree on
// short month names ("Sep" or "Sept").
export function formatDateTime(isoInstant) {
  const date = new Date(isoInstant)
  const hours = date.getHours()
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const clock = `${hours % 12 === 0 ? 12 : hours % 12}:${minutes} ${hours < 12 ? 'am' : 'pm'}`
  return `${date.getDate()} ${MONTH_NAMES[date.getMonth()].slice(0, 3)}, ${clock}`
}
