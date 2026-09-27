import { useEffect, useState } from 'react'
import { createRule, deleteRule, listRules } from '../../api/imports.js'
import ErrorBanner from '../ErrorBanner.jsx'

const inputClass =
  'rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200'

// "When the description contains CHAI POINT, use Food & Dining". Applied to future imports.
export default function RulesSection({ categories }) {
  const [rules, setRules] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [form, setForm] = useState({ matchType: 'CONTAINS', pattern: '', categoryId: '' })
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    let cancelled = false
    listRules()
      .then((data) => {
        if (!cancelled) setRules(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function handleAdd(event) {
    event.preventDefault()
    if (!form.pattern.trim() || !form.categoryId) {
      setError('Enter some text and choose a category')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await createRule({ ...form, categoryId: Number(form.categoryId) })
      setForm((current) => ({ ...current, pattern: '' }))
      setReloadKey((k) => k + 1)
    } catch (err) {
      setError(err.fieldErrors?.pattern ?? err.message)
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete(id) {
    try {
      await deleteRule(id)
      setReloadKey((k) => k + 1)
    } catch (err) {
      setError(err.message)
    }
  }

  const groups = [
    { label: 'Spending', kind: 'EXPENSE' },
    { label: 'Income', kind: 'INCOME' },
    { label: 'Transfers', kind: 'TRANSFER' },
  ]

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-600">
        Common merchants like Swiggy, Uber and Netflix are recognised already. Your own rules apply to future
        imports and are checked first, oldest first.
      </p>

      <ErrorBanner message={error} />

      <form onSubmit={handleAdd} className="flex flex-wrap items-center gap-2 text-sm text-slate-700">
        <span>When the description</span>
        <select name="matchType" value={form.matchType} onChange={handleChange} className={inputClass}>
          <option value="CONTAINS">contains</option>
          <option value="STARTS_WITH">starts with</option>
        </select>
        <input
          name="pattern"
          value={form.pattern}
          onChange={handleChange}
          placeholder="e.g. CHAI POINT"
          maxLength={100}
          className={`${inputClass} w-44`}
        />
        <span>use</span>
        <select name="categoryId" value={form.categoryId} onChange={handleChange} className={inputClass}>
          <option value="">Choose a category</option>
          {groups.map((group) => (
            <optgroup key={group.kind} label={group.label}>
              {categories
                .filter((c) => c.kind === group.kind)
                .map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.name}
                  </option>
                ))}
            </optgroup>
          ))}
        </select>
        <button
          type="submit"
          disabled={busy}
          className="rounded-lg bg-slate-900 px-3 py-2 font-medium text-white hover:bg-slate-700 disabled:opacity-60"
        >
          Add rule
        </button>
      </form>

      {rules && rules.length === 0 && <p className="text-sm text-slate-500">No rules yet.</p>}

      {rules && rules.length > 0 && (
        <ul className="divide-y divide-slate-100 rounded-lg border border-slate-200">
          {rules.map((rule) => (
            <li key={rule.id} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
              <span className="text-slate-700">
                {rule.matchType === 'CONTAINS' ? 'Contains' : 'Starts with'}{' '}
                <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-900">{rule.pattern}</span>{' '}
                &rarr;{' '}
                <span className="inline-flex items-center gap-1.5 font-medium text-slate-900">
                  <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: rule.categoryColor }} />
                  {rule.categoryName}
                </span>
              </span>
              <button
                type="button"
                onClick={() => handleDelete(rule.id)}
                className="text-xs font-medium text-rose-600 hover:text-rose-700"
              >
                Remove
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
