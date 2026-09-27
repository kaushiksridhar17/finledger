import { useCallback, useEffect, useState } from 'react'
import { listAccounts } from '../api/ledger.js'
import AccountForm from '../components/AccountForm.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'
import { accountTypeLabel } from '../lib/accountTypes.js'
import { formatPaise } from '../lib/money.js'

export default function AccountsPage() {
  const [accounts, setAccounts] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  // undefined = form closed, null = adding a new account, an account object = editing it
  const [editing, setEditing] = useState(undefined)

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
  }, [reloadKey])

  const closeForm = useCallback(() => setEditing(undefined), [])

  function handleSaved() {
    setEditing(undefined)
    setReloadKey((k) => k + 1)
  }

  const active = accounts?.filter((a) => !a.archived) ?? []
  const archived = accounts?.filter((a) => a.archived) ?? []
  const total = active.reduce((sum, a) => sum + a.balancePaise, 0)

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Accounts</h1>
          {accounts && (
            <p className="mt-1 text-slate-500">
              Total across active accounts: <span className="font-semibold text-slate-900">{formatPaise(total)}</span>
            </p>
          )}
        </div>
        <button
          type="button"
          onClick={() => setEditing(null)}
          className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800"
        >
          Add account
        </button>
      </div>

      <ErrorBanner message={error} />

      {!accounts && !error && <p className="text-slate-500">Loading...</p>}

      {accounts && accounts.length === 0 && (
        <div className="rounded-lg bg-white p-8 text-center border border-slate-200">
          <p className="text-slate-600">No accounts yet. Add a bank account, card, wallet or cash.</p>
        </div>
      )}

      {active.length > 0 && <AccountGrid accounts={active} onEdit={setEditing} />}

      {archived.length > 0 && (
        <div>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Archived</h2>
          <AccountGrid accounts={archived} onEdit={setEditing} />
        </div>
      )}

      {editing !== undefined && <AccountForm account={editing} onClose={closeForm} onSaved={handleSaved} />}
    </div>
  )
}

function AccountGrid({ accounts, onEdit }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {accounts.map((account) => (
        <button
          key={account.id}
          type="button"
          onClick={() => onEdit(account)}
          className={`rounded-lg bg-white p-5 text-left border border-slate-200 hover:ring-emerald-300 ${
            account.archived ? 'opacity-60' : ''
          }`}
        >
          <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{accountTypeLabel(account.type)}</p>
          <p className="mt-1 font-semibold text-slate-900">{account.name}</p>
          <p className={`mt-3 text-xl font-semibold ${account.balancePaise < 0 ? 'text-rose-600' : 'text-slate-900'}`}>
            {formatPaise(account.balancePaise)}
          </p>
        </button>
      ))}
    </div>
  )
}
