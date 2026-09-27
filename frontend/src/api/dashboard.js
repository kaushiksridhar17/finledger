import { api } from './client.js'

// month is "YYYY-MM"
export function getDashboard(month) {
  return api(`/dashboard?month=${encodeURIComponent(month)}`)
}
