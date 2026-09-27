// Talks to the Spring Boot API.
//
// The access token lives only in this module's memory, never in localStorage, so a malicious script
// on the page can't read it from storage. The refresh token is an HttpOnly cookie the browser handles
// by itself; JavaScript never sees it.
//
// When a request comes back 401, we refresh once and retry, so the user never notices their
// 15-minute access token expiring.

let accessToken = null
let refreshPromise = null
let onSessionExpired = () => {}

export class ApiError extends Error {
  constructor(status, body) {
    super(messageFor(status, body))
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }

  // Per-field messages from a 400 validation error, e.g. { email: "Email is not valid" }
  get fieldErrors() {
    return this.body?.errors ?? {}
  }
}

function messageFor(status, body) {
  if (body?.detail) return body.detail
  if (status === 0) return 'Could not reach the server. Is the backend running?'
  if (status >= 500) return 'The server had a problem. Please try again.'
  return `Request failed with status ${status}`
}

// Called by AuthProvider so the app can show the login page when a session can't be renewed
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler
}

async function readBody(response) {
  if (response.status === 204) return null
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

// body can be a plain object (sent as JSON) or FormData (a file upload; the browser sets its own Content-Type)
async function send(path, { method = 'GET', body, auth = true } = {}) {
  const isForm = body instanceof FormData
  const headers = {}
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json'
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`

  let payload
  if (isForm) payload = body
  else if (body !== undefined) payload = JSON.stringify(body)

  try {
    return await fetch(`/api${path}`, {
      method,
      headers,
      body: payload,
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, null)
  }
}

// Swaps the refresh cookie for a new access token.
// If several requests expire at once, they all wait on the same refresh instead of each starting one.
export function refreshSession() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await send('/auth/refresh', { method: 'POST', auth: false })
      const data = await readBody(response)
      if (!response.ok) {
        accessToken = null
        throw new ApiError(response.status, data)
      }
      accessToken = data.accessToken
      return data
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

// Main helper for every API call. Pass auth: false for public endpoints like login.
export async function api(path, options = {}) {
  let response = await send(path, options)

  if (response.status === 401 && options.auth !== false) {
    try {
      await refreshSession()
    } catch {
      onSessionExpired()
      throw new ApiError(401, { detail: 'Your session has expired. Please log in again.' })
    }
    response = await send(path, options)
  }

  const data = await readBody(response)
  if (!response.ok) throw new ApiError(response.status, data)
  return data
}

export async function login(email, password) {
  const data = await api('/auth/login', { method: 'POST', body: { email, password }, auth: false })
  accessToken = data.accessToken
  return data
}

// "Try the demo": the server creates a throwaway user full of sample data and logs us in as them
export async function startDemo() {
  const data = await api('/auth/demo', { method: 'POST', auth: false })
  accessToken = data.accessToken
  return data
}

export function register(name, email, password) {
  return api('/auth/register', { method: 'POST', body: { name, email, password }, auth: false })
}

export async function logout() {
  try {
    await api('/auth/logout', { method: 'POST', auth: false })
  } finally {
    accessToken = null
  }
}

export function getMe() {
  return api('/users/me')
}
