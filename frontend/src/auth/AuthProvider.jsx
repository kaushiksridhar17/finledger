import { useCallback, useEffect, useMemo, useState } from 'react'
import { AuthContext } from './authContext.js'
import * as client from '../api/client.js'

// status is one of:
//   'loading'       - checking the refresh cookie on first page load
//   'authenticated' - logged in, user is set
//   'anonymous'     - not logged in
export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [status, setStatus] = useState('loading')

  useEffect(() => {
    client.setSessionExpiredHandler(() => {
      setUser(null)
      setStatus('anonymous')
    })

    // On page load, try the refresh cookie. If it works, the user stays logged in across reloads.
    let cancelled = false
    client
      .refreshSession()
      .then((data) => {
        if (cancelled) return
        setUser(data.user)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        setUser(null)
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (email, password) => {
    const data = await client.login(email, password)
    setUser(data.user)
    setStatus('authenticated')
    return data.user
  }, [])

  const register = useCallback(
    async (name, email, password) => {
      await client.register(name, email, password)
      return login(email, password)
    },
    [login],
  )

  const logout = useCallback(async () => {
    try {
      await client.logout()
    } finally {
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo(
    () => ({ user, status, login, register, logout }),
    [user, status, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
