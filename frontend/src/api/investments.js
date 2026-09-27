import { api } from './client.js'

// Changes return the whole updated portfolio, so the page redraws straight from the response.

export function getPortfolio() {
  return api('/investments')
}

export function searchFunds(query) {
  return api(`/investments/search?q=${encodeURIComponent(query)}`)
}

// trade: { schemeCode, type: 'BUY' | 'SELL', date, amountPaise, units (optional) }
export function addFundTransaction(trade) {
  return api('/investments/transactions', { method: 'POST', body: trade })
}

export function deleteFundTransaction(id) {
  return api(`/investments/transactions/${id}`, { method: 'DELETE' })
}

export function getSipSuggestions() {
  return api('/investments/sip-suggestions')
}

export function linkSip(matchKey, schemeCode) {
  return api('/investments/sip-links', { method: 'POST', body: { matchKey, schemeCode } })
}

export function unlinkSip(id) {
  return api(`/investments/sip-links/${id}`, { method: 'DELETE' })
}

export function refreshPrices() {
  return api('/investments/refresh', { method: 'POST' })
}
