import { api } from './client.js'

export function getRecurring() {
  return api('/recurring')
}

export function upcomingBills(days = 14) {
  return api(`/recurring/upcoming?days=${days}`)
}

export function scanRecurring() {
  return api('/recurring/scan', { method: 'POST' })
}

export function confirmRecurring(id) {
  return api(`/recurring/${id}/confirm`, { method: 'POST' })
}

export function dismissRecurring(id) {
  return api(`/recurring/${id}/dismiss`, { method: 'POST' })
}
