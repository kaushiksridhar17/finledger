import { api } from './client.js'

// month is "YYYY-MM"
export function listBudgets(month) {
  return api(`/budgets?month=${encodeURIComponent(month)}`)
}

export function budgetSuggestions() {
  return api('/budgets/suggestions')
}

export function createBudget(categoryId, limitPaise) {
  return api('/budgets', { method: 'POST', body: { categoryId, limitPaise } })
}

export function updateBudget(id, limitPaise) {
  return api(`/budgets/${id}`, { method: 'PUT', body: { limitPaise } })
}

export function deleteBudget(id) {
  return api(`/budgets/${id}`, { method: 'DELETE' })
}
