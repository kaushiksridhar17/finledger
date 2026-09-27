import { api } from './client.js'

// Every change returns the whole updated group, so pages can redraw straight from the response.

export function listGroups() {
  return api('/groups')
}

export function getGroup(id) {
  return api(`/groups/${id}`)
}

// friends: [{ name, upiId }]
export function createGroup(name, friends) {
  return api('/groups', { method: 'POST', body: { name, friends } })
}

export function renameGroup(id, name) {
  return api(`/groups/${id}`, { method: 'PUT', body: { name } })
}

export function deleteGroup(id) {
  return api(`/groups/${id}`, { method: 'DELETE' })
}

export function addMember(groupId, member) {
  return api(`/groups/${groupId}/members`, { method: 'POST', body: member })
}

export function updateMember(groupId, memberId, member) {
  return api(`/groups/${groupId}/members/${memberId}`, { method: 'PUT', body: member })
}

export function removeMember(groupId, memberId) {
  return api(`/groups/${groupId}/members/${memberId}`, { method: 'DELETE' })
}

// expense: { description, amountPaise, date, paidByMemberId, splitType, shares: [{ memberId, value }] }
export function addExpense(groupId, expense) {
  return api(`/groups/${groupId}/expenses`, { method: 'POST', body: expense })
}

export function updateExpense(groupId, expenseId, expense) {
  return api(`/groups/${groupId}/expenses/${expenseId}`, { method: 'PUT', body: expense })
}

export function deleteExpense(groupId, expenseId) {
  return api(`/groups/${groupId}/expenses/${expenseId}`, { method: 'DELETE' })
}

// payment: { fromMemberId, toMemberId, amountPaise, date, note }
export function addSettlement(groupId, payment) {
  return api(`/groups/${groupId}/settlements`, { method: 'POST', body: payment })
}

export function deleteSettlement(groupId, settlementId) {
  return api(`/groups/${groupId}/settlements/${settlementId}`, { method: 'DELETE' })
}

export function acceptMatch(groupId, matchId) {
  return api(`/groups/${groupId}/matches/${matchId}/accept`, { method: 'POST' })
}

export function dismissMatch(groupId, matchId) {
  return api(`/groups/${groupId}/matches/${matchId}/dismiss`, { method: 'POST' })
}
