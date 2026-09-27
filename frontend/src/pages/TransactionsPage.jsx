import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listAccounts, listCategories, listTransactions } from '../api/ledger.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import TransactionForm from '../components/TransactionForm.jsx'
import { formatDate } from '../lib/dates.js'
import { formatPaise } from '../lib/money.js'

const PAGE_SIZE = 25
const EMPTY_FILTERS = { accountId: '', categoryId: '', direction: '', from: '', to: '', q: '' }

const inputClass =
  'block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200'

export default function TransactionsPage() {
  const [accounts, setAccounts] = useState([])
  const [categories, setCategories] = useState([])

  // draft = what's typed in the filter bar; filters = what was last applied
  const [draft, setDraft] = useState(EMPTY_FILTERS)
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [page, setPage] = useState(0)

  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  // undefined = form closed, null = adding, a transaction object = editing it
  const [editing, setEditing] = useState(undefined)

  // Accounts and categories fill the dropdowns
  useEffect(() => {
    let cancelled = false
    Promise.all([listAccounts(), listCategories()])
      .then(([accountList, categoryList]) => {
        if (cancelled) return
        setAccounts(accountList)
        setCategories(categoryList)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  // Reload the list whenever the applied filters, the page, or the data change
  useEffect(() => {
    let cancelled = false
    listTransactions({ ...filters, page, size: PAGE_SIZE })
      .then((data) => {
        if (cancelled) return
        setResult(data)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [filters, page, reloadKey])

  const closeForm = useCallback(() => setEditing(undefined), [])

  function handleSaved() {
    setEditing(undefined)
    setReloadKey((k) => k + 1)
  }

  function handleDraftChange(event) {
    const { name, value } = event.target
    setDraft((current) => ({ ...current, [name]: value }))
  }

  function applyFilters(event) {
    event.preventDefault()
    setFilters(draft)
    setPage(0)
  }

  function clearFilters() {
    setDraft(EMPTY_FILTERS)
    setFilters(EMPTY_FILTERS)
    setPage(0)
  }

  const noAccounts = accounts.length === 0

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4">
        <h1 className="text-2xl font-semibold text-slate-900">Transactions</h1>
        <button
          type="button"
          onClick={() => setEditing(null)}
          disabled={noAccounts}
          className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-50"
        >
          Add transaction
        </button>
      </div>

      {noAccounts && result && (
        <div className="rounded-lg bg-amber-50 px-4 py-3 text-sm text-amber-800 border border-amber-200">
          You need an account before adding transactions.{' '}
          <Link to="/accounts" className="font-medium underline">
            Add one on the Accounts page
          </Link>
          .
        </div>
      )}

      <form
        onSubmit={applyFilters}
        className="grid gap-3 rounded-lg bg-white p-4 border border-slate-200 sm:grid-cols-2 lg:grid-cols-6"
      >
        <input
          name="q"
          value={draft.q}
          onChange={handleDraftChange}
          placeholder="Search description or merchant"
          className={`${inputClass} lg:col-span-2`}
        />
        <select name="accountId" value={draft.accountId} onChange={handleDraftChange} className={inputClass}>
          <option value="">All accounts</option>
          {accounts.map((a) => (
            <option key={a.id} value={a.id}>
              {a.name}
            </option>
          ))}
        </select>
        <select name="categoryId" value={draft.categoryId} onChange={handleDraftChange} className={inputClass}>
          <option value="">All categories</option>
          {categories.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <select name="direction" value={draft.direction} onChange={handleDraftChange} className={inputClass}>
          <option value="">In and out</option>
          <option value="OUT">Money out</option>
          <option value="IN">Money in</option>
        </select>
        <div className="flex gap-2">
          <button type="submit" className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white hover:bg-slate-700">
            Apply
          </button>
          <button
            type="button"
            onClick={clearFilters}
            className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 border border-slate-300 hover:bg-slate-100"
          >
            Clear
          </button>
        </div>
        <label className="text-sm text-slate-600 lg:col-span-3">
          From
          <input type="date" name="from" value={draft.from} onChange={handleDraftChange} className={`${inputClass} mt-1`} />
        </label>
        <label className="text-sm text-slate-600 lg:col-span-3">
          To
          <input type="date" name="to" value={draft.to} onChange={handleDraftChange} className={`${inputClass} mt-1`} />
        </label>
      </form>

      <ErrorBanner message={error} />

      {!result && !error && <p className="text-slate-500">Loading...</p>}

      {result && result.items.length === 0 && (
        <div className="rounded-lg bg-white p-8 text-center text-slate-600 border border-slate-200">
          No transactions match.
        </div>
      )}

      {result && result.items.length > 0 && (
        <div className="overflow-hidden rounded-lg bg-white border border-slate-200">
          <ul className="divide-y divide-slate-100">
            {result.items.map((t) => (
              <li key={t.id}>
                <button
                  type="button"
                  onClick={() => setEditing(t)}
                  className="flex w-full items-center gap-4 px-4 py-3 text-left hover:bg-slate-50"
                >
                  <span className="w-24 shrink-0 text-sm text-slate-500">{formatDate(t.date)}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium text-slate-900">{t.description}</span>
                    <span className="block truncate text-xs text-slate-500">
                      {t.accountName}
                      {t.merchant ? ` · ${t.merchant}` : ''}
                    </span>
                  </span>
                  <CategoryChip name={t.categoryName} color={t.categoryColor} />
                  <span
                    className={`w-32 shrink-0 text-right font-semibold ${
                      t.amountPaise > 0 ? 'text-emerald-700' : 'text-slate-900'
                    }`}
                  >
                    {t.amountPaise > 0 ? '+' : ''}
                    {formatPaise(t.amountPaise)}
                  </span>
                </button>
              </li>
            ))}
          </ul>

          <div className="flex items-center justify-between border-t border-slate-100 px-4 py-3 text-sm text-slate-600">
            <span>
              Page {result.page + 1} of {Math.max(result.totalPages, 1)} &middot; {result.totalItems} transactions
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((p) => p - 1)}
                disabled={result.page === 0}
                className="rounded-lg px-3 py-1.5 border border-slate-300 hover:bg-slate-100 disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => p + 1)}
                disabled={result.page + 1 >= result.totalPages}
                className="rounded-lg px-3 py-1.5 border border-slate-300 hover:bg-slate-100 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        </div>
      )}

      {editing !== undefined && (
        <TransactionForm
          transaction={editing}
          accounts={accounts}
          categories={categories}
          onClose={closeForm}
          onSaved={handleSaved}
        />
      )}
    </div>
  )
}

function CategoryChip({ name, color }) {
  if (!name) {
    return <span className="hidden shrink-0 rounded-full bg-slate-100 px-2.5 py-0.5 text-xs text-slate-500 sm:inline">Uncategorised</span>
  }
  return (
    <span
      className="hidden shrink-0 rounded-full px-2.5 py-0.5 text-xs font-medium sm:inline"
      style={{ backgroundColor: `${color}22`, color }}
    >
      {name}
    </span>
  )
}
