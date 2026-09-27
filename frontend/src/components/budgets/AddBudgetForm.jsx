import { useState } from 'react'
import { createBudget } from '../../api/budgets.js'
import { formatPaise, paiseToInput, parseRupeesToPaise } from '../../lib/money.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import SelectField from '../SelectField.jsx'
import TextField from '../TextField.jsx'

// Pick a spending category that doesn't have a budget yet, and a monthly limit.
// Shows what you usually spend in that category to help choose.
export default function AddBudgetForm({ categories, budgeted, suggestions, onClose, onSaved }) {
  const available = categories.filter((c) => c.kind === 'EXPENSE' && !budgeted.has(c.id))

  const [categoryId, setCategoryId] = useState(available[0] ? String(available[0].id) : '')
  const [limit, setLimit] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const suggestion = suggestions.find((s) => String(s.categoryId) === categoryId)

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const paise = parseRupeesToPaise(limit)
    if (paise === null || paise <= 0) {
      setFieldErrors({ limit: 'Enter a monthly amount like 8000' })
      return
    }

    setBusy(true)
    try {
      await createBudget(Number(categoryId), paise)
      onSaved()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title="Add a budget" onClose={onClose}>
      {available.length === 0 ? (
        <p className="text-slate-600">Every spending category already has a budget.</p>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <ErrorBanner message={error} />

          <SelectField label="Category" name="categoryId" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            {available.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </SelectField>

          <div>
            <TextField
              label={'Monthly limit (₹)'}
              name="limit"
              value={limit}
              onChange={(e) => setLimit(e.target.value)}
              error={fieldErrors.limit}
            />
            {suggestion && (
              <p className="mt-1 text-xs text-slate-500">
                You've spent about {formatPaise(suggestion.averageMonthlyPaise)} a month on this lately.{' '}
                <button
                  type="button"
                  onClick={() => setLimit(paiseToInput(Math.round(suggestion.averageMonthlyPaise / 10000) * 10000))}
                  className="font-medium text-emerald-700 hover:text-emerald-800"
                >
                  Use that
                </button>
              </p>
            )}
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={busy}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
            >
              {busy ? 'Saving...' : 'Add budget'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}
