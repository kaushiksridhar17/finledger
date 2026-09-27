import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getMe } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import StatusCard from '../components/StatusCard.jsx'

function formatDate(isoString) {
  return new Date(isoString).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })
}

// Placeholder dashboard. Phase 3 replaces the middle with real accounts and transactions.
export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [me, setMe] = useState(null)
  const [meError, setMeError] = useState(null)
  const [checking, setChecking] = useState(false)
  const [checkedAt, setCheckedAt] = useState(null)

  useEffect(() => {
    let cancelled = false
    getMe()
      .then((data) => {
        if (cancelled) return
        setMe(data)
        setCheckedAt(new Date())
      })
      .catch((err) => {
        if (!cancelled) setMeError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Calls a protected endpoint. If the access token has expired, client.js refreshes it first.
  async function checkSession() {
    setChecking(true)
    setMeError(null)
    try {
      setMe(await getMe())
      setCheckedAt(new Date())
    } catch (err) {
      setMeError(err.message)
    } finally {
      setChecking(false)
    }
  }

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-4xl items-center justify-between px-4 py-3">
          <span className="text-lg font-semibold text-emerald-600">FinLedger</span>
          <div className="flex items-center gap-4">
            <span className="text-sm text-slate-600">{user?.name}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-100"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-4xl space-y-6 px-4 py-8">
        <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
          <h1 className="text-2xl font-semibold text-slate-900">Welcome, {user?.name}</h1>
          <p className="mt-1 text-slate-500">
            Accounts, transactions and budgets arrive in the next phase. For now this page proves login works.
          </p>

          <div className="mt-6 space-y-2 text-sm">
            <ErrorBanner message={meError} />
            {me && (
              <>
                <p>
                  <span className="text-slate-500">Email:</span>{' '}
                  <span className="font-medium text-slate-900">{me.email}</span>
                </p>
                <p>
                  <span className="text-slate-500">Member since:</span>{' '}
                  <span className="font-medium text-slate-900">{formatDate(me.createdAt)}</span>
                </p>
                <p>
                  <span className="text-slate-500">Base currency:</span>{' '}
                  <span className="font-medium text-slate-900">{me.baseCurrency}</span>
                </p>
              </>
            )}
            {checkedAt && (
              <p className="text-slate-400">Last checked with the server at {checkedAt.toLocaleTimeString()}</p>
            )}
          </div>

          <button
            type="button"
            onClick={checkSession}
            disabled={checking}
            className="mt-4 rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {checking ? 'Checking...' : 'Check my session'}
          </button>
        </section>

        <StatusCard />
      </main>
    </div>
  )
}
