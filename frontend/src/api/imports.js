import { api } from './client.js'

// Sends the file as multipart form data. Returns straight away with status QUEUED.
export function uploadStatement(accountId, file) {
  const form = new FormData()
  form.append('accountId', accountId)
  form.append('file', file)
  return api('/imports', { method: 'POST', body: form })
}

export function listImports() {
  return api('/imports')
}

// One import with the list of lines that couldn't be read
export function getImport(id) {
  return api(`/imports/${id}`)
}

export function listRules() {
  return api('/category-rules')
}

export function createRule(data) {
  return api('/category-rules', { method: 'POST', body: data })
}

export function deleteRule(id) {
  return api(`/category-rules/${id}`, { method: 'DELETE' })
}
