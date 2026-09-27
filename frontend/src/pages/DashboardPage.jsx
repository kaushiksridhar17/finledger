import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listAccounts } from '../api/ledger.js'
import { useAuth } from '../auth/useAuth.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import StatusCard from '../components/StatusCard.jsx'
import { accountTypeLabel } from '../lib/accountTypes.js'
import { formatPaise } from '../lib/money.js'

// Balances for now. Charts for spending by category and monthly trends come in the next part of phase 3.
export default function DashboardPage() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    listAccounts()
      .then((data) => {
        if (!cancelled) setAccounts(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const active = accounts?.filter((a) => !a.archived) ?? []
  const total = active.reduce((sum, a) => sum + a.balancePaise, 0)

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Hi, {user?.name}</h1>
        <p className="mt-1 text-slate-500">Here's where your money is right now.</p>
      </div>

      <ErrorBanner message={error} />

      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold text-slate-900">Balances</h2>
          {accounts && <span className="text-2xl font-semibold text-slate-900">{formatPaise(total)}</span>}
        </div>

        {!accounts && !error && <p className="mt-4 text-slate-500">Loading...</p>}

        {accounts && active.length === 0 && (
          <p className="mt-4 text-slate-600">
            No accounts yet.{' '}
            <Link to="/accounts" className="font-medium text-emerald-600 hover:text-emerald-700">
              Add your first account
            </Link>
            .
          </p>
        )}

        {active.length > 0 && (
          <ul className="mt-4 divide-y divide-slate-100">
            {active.map((a) => (
              <li key={a.id} className="flex items-center justify-between py-2">
                <span>
                  <span className="font-medium text-slate-900">{a.name}</span>{' '}
                  <span className="text-xs text-slate-500">{accountTypeLabel(a.type)}</span>
                </span>
                <span className={`font-medium ${a.balancePaise < 0 ? 'text-rose-600' : 'text-slate-900'}`}>
                  {formatPaise(a.balancePaise)}
                </span>
              </li>
            ))}
          </ul>
        )}

        {active.length > 0 && (
          <Link
            to="/transactions"
            className="mt-4 inline-block rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
          >
            View transactions
          </Link>
        )}
      </section>

      <StatusCard />
    </div>
  )
}
