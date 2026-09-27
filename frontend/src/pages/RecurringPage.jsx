import { useCallback, useEffect, useState } from 'react'
import { confirmRecurring, dismissRecurring, getRecurring, scanRecurring } from '../api/recurring.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import RecurringRow from '../components/recurring/RecurringRow.jsx'
import { formatPaise } from '../lib/money.js'

const buttonClass = 'rounded-lg px-3 py-1.5 text-sm font-medium'

export default function RecurringPage() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [message, setMessage] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [scanning, setScanning] = useState(false)

  useEffect(() => {
    let cancelled = false
    getRecurring()
      .then((result) => {
        if (cancelled) return
        setData(result)
        setError(null)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const reload = useCallback(() => setReloadKey((k) => k + 1), [])

  async function scan() {
    setScanning(true)
    setMessage(null)
    try {
      const result = await scanRecurring()
      setMessage(
        result.newSuggestions > 0
          ? `Found ${result.newSuggestions} new repeating ${result.newSuggestions === 1 ? 'payment' : 'payments'}.`
          : 'Nothing new found.',
      )
      reload()
    } catch (err) {
      setError(err.message)
    } finally {
      setScanning(false)
    }
  }

  async function act(action, id) {
    try {
      await action(id)
      reload()
    } catch (err) {
      setError(err.message)
    }
  }

  const items = data?.items ?? []
  const suggested = items.filter((i) => i.status === 'SUGGESTED')
  const bills = items.filter((i) => i.status === 'CONFIRMED' && i.direction === 'OUT')
  const income = items.filter((i) => i.status === 'CONFIRMED' && i.direction === 'IN')
  const dismissed = items.filter((i) => i.status === 'DISMISSED')

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Bills and subscriptions</h1>
          <p className="mt-1 text-slate-500">
            Payments that repeat in your transactions. Confirmed bills get a reminder 3 days before they're due.
          </p>
        </div>
        <button
          type="button"
          onClick={scan}
          disabled={scanning}
          className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
        >
          {scanning ? 'Scanning...' : 'Scan now'}
        </button>
      </div>

      <ErrorBanner message={error} />
      {message && <p className="text-sm text-slate-600">{message}</p>}

      {!data && !error && <p className="text-slate-500">Loading...</p>}

      {data && items.length === 0 && (
        <div className="rounded-lg bg-white p-8 text-center text-slate-600 border border-slate-200">
          Nothing found yet. This needs at least three months of transactions.
        </div>
      )}

      {suggested.length > 0 && (
        <section className="rounded-lg bg-white p-6 shadow-sm border border-sky-200">
          <h2 className="text-lg font-semibold text-slate-900">Found in your transactions</h2>
          <p className="text-sm text-slate-500">Are these regular payments?</p>
          <ul className="mt-2 divide-y divide-slate-100">
            {suggested.map((item) => (
              <RecurringRow key={item.id} item={item}>
                <button
                  type="button"
                  onClick={() => act(confirmRecurring, item.id)}
                  className={`${buttonClass} bg-emerald-700 text-white hover:bg-emerald-800`}
                >
                  Yes, track it
                </button>
                <button
                  type="button"
                  onClick={() => act(dismissRecurring, item.id)}
                  className={`${buttonClass} text-slate-600 border border-slate-300 hover:bg-slate-100`}
                >
                  No
                </button>
              </RecurringRow>
            ))}
          </ul>
        </section>
      )}

      {bills.length > 0 && (
        <section className="rounded-lg bg-white p-6 border border-slate-200">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-lg font-semibold text-slate-900">Your bills and subscriptions</h2>
            <p className="text-sm text-slate-600">
              About <span className="font-semibold text-slate-900">{formatPaise(data.confirmedMonthlyOutPaise)}</span>{' '}
              a month
            </p>
          </div>
          <ul className="mt-2 divide-y divide-slate-100">
            {bills.map((item) => (
              <RecurringRow key={item.id} item={item}>
                <button
                  type="button"
                  onClick={() => act(dismissRecurring, item.id)}
                  className="text-xs font-medium text-slate-500 hover:text-slate-700"
                >
                  Stop tracking
                </button>
              </RecurringRow>
            ))}
          </ul>
        </section>
      )}

      {income.length > 0 && (
        <section className="rounded-lg bg-white p-6 border border-slate-200">
          <h2 className="text-lg font-semibold text-slate-900">Regular income</h2>
          <ul className="mt-2 divide-y divide-slate-100">
            {income.map((item) => (
              <RecurringRow key={item.id} item={item}>
                <button
                  type="button"
                  onClick={() => act(dismissRecurring, item.id)}
                  className="text-xs font-medium text-slate-500 hover:text-slate-700"
                >
                  Stop tracking
                </button>
              </RecurringRow>
            ))}
          </ul>
        </section>
      )}

      {dismissed.length > 0 && (
        <details className="rounded-lg bg-white p-6 border border-slate-200">
          <summary className="cursor-pointer text-sm font-medium text-slate-600">
            Not tracked ({dismissed.length})
          </summary>
          <ul className="mt-2 divide-y divide-slate-100">
            {dismissed.map((item) => (
              <RecurringRow key={item.id} item={item}>
                <button
                  type="button"
                  onClick={() => act(confirmRecurring, item.id)}
                  className="text-xs font-medium text-emerald-700 hover:text-emerald-800"
                >
                  Track it
                </button>
              </RecurringRow>
            ))}
          </ul>
        </details>
      )}
    </div>
  )
}
