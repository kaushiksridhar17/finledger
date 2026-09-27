import { api } from './client.js'

// ---------------------------------------------------------------- accounts

export function listAccounts() {
  return api('/accounts')
}

export function createAccount(data) {
  return api('/accounts', { method: 'POST', body: data })
}

export function updateAccount(id, data) {
  return api(`/accounts/${id}`, { method: 'PUT', body: data })
}

export function deleteAccount(id) {
  return api(`/accounts/${id}`, { method: 'DELETE' })
}

// ---------------------------------------------------------------- categories

export function listCategories() {
  return api('/categories')
}

export function createCategory(data) {
  return api('/categories', { method: 'POST', body: data })
}

// ---------------------------------------------------------------- transactions

// Empty filters are left out of the URL, so the backend treats them as "no filter"
export function listTransactions(params) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== '' && value !== null && value !== undefined) query.set(key, value)
  }
  return api(`/transactions?${query}`)
}

export function createTransaction(data) {
  return api('/transactions', { method: 'POST', body: data })
}

export function updateTransaction(id, data) {
  return api(`/transactions/${id}`, { method: 'PUT', body: data })
}

export function deleteTransaction(id) {
  return api(`/transactions/${id}`, { method: 'DELETE' })
}
