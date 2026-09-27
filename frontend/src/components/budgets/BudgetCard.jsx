import { useState } from 'react'
import { deleteBudget, updateBudget } from '../../api/budgets.js'
import { formatPaise, paiseToInput, parseRupeesToPaise } from '../../lib/money.js'

// Status is always shown as words next to the colour, never by colour alone
const STATUS = {
  ON_TRACK: { bar: 'bg-emerald-600', text: 'text-emerald-800', label: 'On track' },
  NEAR_LIMIT: { bar: 'bg-amber-400', text: 'text-amber-800', label: 'Almost at the limit' },
  OVER: { bar: 'bg-rose-600', text: 'text-rose-700', label: 'Over budget' },
}

export default function BudgetCard({ budget, editable, onChanged }) {
  const [editing, setEditing] = useState(false)
  const [limit, setLimit] = useState(paiseToInput(budget.limitPaise))
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const status = STATUS[budget.status]
  const width = Math.min(budget.percent, 100)

  async function save(event) {
    event.preventDefault()
    const paise = parseRupeesToPaise(limit)
    if (paise === null || paise <= 0) {
      setError('Enter an amount like 8000')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await updateBudget(budget.id, paise)
      setEditing(false)
      onChanged()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!window.confirm(`Remove the ${budget.categoryName} budget?`)) return
    try {
      await deleteBudget(budget.id)
      onChanged()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="flex items-center gap-2 font-semibold text-slate-900">
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: budget.categoryColor }} />
            {budget.categoryName}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            <span className="font-medium text-slate-900">{formatPaise(budget.spentPaise)}</span> of{' '}
            {formatPaise(budget.limitPaise)}
          </p>
        </div>
        <p className={`text-sm font-medium ${status.text}`}>
          {status.label} &middot; {budget.percent}%
        </p>
      </div>

      <div
        className="mt-3 h-2 rounded-full bg-slate-100"
        role="progressbar"
        aria-valuenow={budget.percent}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-label={`${budget.categoryName} budget used`}
      >
        <div className={`h-2 rounded-full ${status.bar}`} style={{ width: `${width}%` }} />
      </div>

      <p className="mt-2 text-sm text-slate-500">
        {budget.remainingPaise >= 0
          ? `${formatPaise(budget.remainingPaise)} left`
          : `Over by ${formatPaise(-budget.remainingPaise)}`}
      </p>

      {error && <p className="mt-2 text-sm text-rose-700">{error}</p>}

      {editable && !editing && (
        <div className="mt-3 flex gap-3 text-sm">
          <button
            type="button"
            onClick={() => {
              setLimit(paiseToInput(budget.limitPaise))
              setEditing(true)
            }}
            className="font-medium text-emerald-700 hover:text-emerald-800"
          >
            Change limit
          </button>
          <button type="button" onClick={remove} className="font-medium text-rose-600 hover:text-rose-700">
            Remove
          </button>
        </div>
      )}

      {editing && (
        <form onSubmit={save} className="mt-3 flex items-center gap-2 text-sm">
          <span className="text-slate-600">Limit (&#8377;)</span>
          <input
            value={limit}
            onChange={(e) => setLimit(e.target.value)}
            className="w-28 rounded-lg border border-slate-300 px-2 py-1.5 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200"
          />
          <button type="submit" disabled={busy} className="rounded-lg bg-emerald-600 px-3 py-1.5 font-medium text-white disabled:opacity-60">
            Save
          </button>
          <button type="button" onClick={() => setEditing(false)} className="px-2 py-1.5 text-slate-600">
            Cancel
          </button>
        </form>
      )}
    </div>
  )
}
