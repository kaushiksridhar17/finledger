import { api } from './client.js'

export function getNotifications() {
  return api('/notifications')
}

export function markNotificationRead(id) {
  return api(`/notifications/${id}/read`, { method: 'POST' })
}

export function markAllNotificationsRead() {
  return api('/notifications/read-all', { method: 'POST' })
}
