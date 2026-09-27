import { useCallback, useEffect, useState } from 'react'
import { budgetSuggestions, listBudgets } from '../api/budgets.js'
import { listCategories } from '../api/ledger.js'
import AddBudgetForm from '../components/budgets/AddBudgetForm.jsx'
import BudgetCard from '../components/budgets/BudgetCard.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'
import { formatPaise } from '../lib/money.js'
import { currentMonth, formatMonth, shiftMonth } from '../lib/months.js'

export default function BudgetsPage() {
  const [month, setMonth] = useState(currentMonth)
  const [budgets, setBudgets] = useState(null)
  const [categories, setCategories] = useState([])
  const [suggestions, setSuggestions] = useState([])
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [adding, setAdding] = useState(false)

  useEffect(() => {
    let cancelled = false
    Promise.all([listCategories(), budgetSuggestions()])
      .then(([categoryList, suggestionList]) => {
        if (cancelled) return
        setCategories(categoryList)
        setSuggestions(suggestionList)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    listBudgets(month)
      .then((data) => {
        if (cancelled) return
        setBudgets(data)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [month, reloadKey])

  const reload = useCallback(() => setReloadKey((k) => k + 1), [])
  const closeForm = useCallback(() => setAdding(false), [])

  const isThisMonth = month === currentMonth()
  const totalLimit = budgets?.reduce((sum, b) => sum + b.limitPaise, 0) ?? 0
  const totalSpent = budgets?.reduce((sum, b) => sum + b.spentPaise, 0) ?? 0
  const budgeted = new Set(budgets?.map((b) => b.categoryId) ?? [])

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Budgets</h1>
          <p className="mt-1 text-slate-500">
            Monthly limits by category. You're notified at 80% and again if you go over.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setMonth((m) => shiftMonth(m, -1))}
            aria-label="Previous month"
            className="rounded-lg px-3 py-1.5 text-slate-600 border border-slate-300 hover:bg-slate-100"
          >
            &lsaquo;
          </button>
          <span className="w-40 text-center font-medium text-slate-900">{formatMonth(month)}</span>
          <button
            type="button"
            onClick={() => setMonth((m) => shiftMonth(m, 1))}
            disabled={isThisMonth}
            aria-label="Next month"
            className="rounded-lg px-3 py-1.5 text-slate-600 border border-slate-300 hover:bg-slate-100 disabled:opacity-40"
          >
            &rsaquo;
          </button>
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="ml-2 rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800"
          >
            Add budget
          </button>
        </div>
      </div>

      <ErrorBanner message={error} />

      {!budgets && !error && <p className="text-slate-500">Loading...</p>}

      {budgets && budgets.length === 0 && (
        <div className="rounded-lg bg-white p-8 text-center text-slate-600 border border-slate-200">
          No budgets yet.
        </div>
      )}

      {budgets && budgets.length > 0 && (
        <>
          <p className="text-slate-600">
            {isThisMonth ? 'So far this month' : `In ${formatMonth(month)}`}:{' '}
            <span className="font-semibold text-slate-900">{formatPaise(totalSpent)}</span> spent of{' '}
            <span className="font-semibold text-slate-900">{formatPaise(totalLimit)}</span> budgeted.
          </p>
          <div className="grid gap-4 sm:grid-cols-2">
            {budgets.map((budget) => (
              <BudgetCard key={budget.id} budget={budget} editable={isThisMonth} onChanged={reload} />
            ))}
          </div>
        </>
      )}

      {adding && (
        <AddBudgetForm
          categories={categories}
          budgeted={budgeted}
          suggestions={suggestions}
          onClose={closeForm}
          onSaved={() => {
            setAdding(false)
            reload()
          }}
        />
      )}
    </div>
  )
}
