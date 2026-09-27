import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getDashboard } from '../api/dashboard.js'
import { upcomingBills } from '../api/recurring.js'
import CategoryBreakdown from '../components/dashboard/CategoryBreakdown.jsx'
import MonthlyChart from '../components/dashboard/MonthlyChart.jsx'
import StatTile from '../components/dashboard/StatTile.jsx'
import ErrorBanner from '../components/ErrorBanner.jsx'
import { formatDate } from '../lib/dates.js'
import { formatPaise } from '../lib/money.js'
import { currentMonth, formatMonth, shiftMonth } from '../lib/months.js'
import { dueLabel } from '../lib/recurring.js'

export default function DashboardPage() {
  const [month, setMonth] = useState(currentMonth)
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [upcoming, setUpcoming] = useState([])

  // Confirmed bills due in the next two weeks
  useEffect(() => {
    let cancelled = false
    upcomingBills(14)
      .then((items) => {
        if (!cancelled) setUpcoming(items)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  // Reload whenever the month changes. The previous month's numbers stay on screen until the new ones arrive.
  useEffect(() => {
    let cancelled = false
    getDashboard(month)
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
  }, [month])

  const isLatestMonth = month >= currentMonth()
  const saved = data ? data.incomePaise - data.spendingPaise : 0
  const savingsRate = data && data.incomePaise > 0 ? Math.round((saved / data.incomePaise) * 100) : null
  const hasAnyData = data && (data.recent.length > 0 || data.netWorthPaise !== 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Dashboard</h1>
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
            disabled={isLatestMonth}
            aria-label="Next month"
            className="rounded-lg px-3 py-1.5 text-slate-600 border border-slate-300 hover:bg-slate-100 disabled:opacity-40"
          >
            &rsaquo;
          </button>
        </div>
      </div>

      <ErrorBanner message={error} />

      {!data && !error && <p className="text-slate-500">Loading...</p>}

      {data && !hasAnyData && (
        <div className="rounded-lg bg-white p-8 text-center border border-slate-200">
          <p className="text-slate-600">
            Nothing here yet.{' '}
            <Link to="/accounts" className="font-medium text-emerald-700 hover:text-emerald-800">
              Add an account
            </Link>{' '}
            and some transactions to see totals and charts.
          </p>
        </div>
      )}

      {data && hasAnyData && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatTile label="Net worth" value={formatPaise(data.netWorthPaise)} note="All active accounts, today" />
            <StatTile label="Income" value={formatPaise(data.incomePaise)} note={formatMonth(month)} />
            <StatTile label="Spending" value={formatPaise(data.spendingPaise)} note="Transfers and SIPs not counted" />
            <StatTile
              label="Saved"
              value={formatPaise(saved)}
              note={savingsRate === null ? 'No income this month' : `${savingsRate}% of income`}
            />
          </div>

          {upcoming.length > 0 && (
            <section className="rounded-lg bg-white p-6 border border-slate-200">
              <div className="mb-3 flex items-baseline justify-between">
                <h2 className="text-lg font-semibold text-slate-900">Coming up in the next 2 weeks</h2>
                <Link to="/recurring" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
                  All bills
                </Link>
              </div>
              <ul className="grid gap-x-8 gap-y-2 sm:grid-cols-2">
                {upcoming.map((bill) => (
                  <li key={bill.id} className="flex items-center justify-between gap-3 text-sm">
                    <span className="min-w-0 truncate">
                      <span className="font-medium text-slate-900">{bill.name}</span>{' '}
                      <span className="text-slate-500">&middot; {dueLabel(bill.daysUntilDue, bill.nextDueOn)}</span>
                    </span>
                    <span className="shrink-0 tabular-nums text-slate-900">
                      {bill.amountVaries ? 'about ' : ''}
                      {formatPaise(bill.amountPaise)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          )}

          <section className="rounded-lg bg-white p-6 border border-slate-200">
            <h2 className="text-lg font-semibold text-slate-900">Income and spending, last 12 months</h2>
            <p className="mb-4 text-sm text-slate-500">Click a month to see it in detail.</p>
            <MonthlyChart months={data.monthly} selectedMonth={month} onSelectMonth={setMonth} />
          </section>

          <div className="grid gap-6 lg:grid-cols-2">
            <section className="rounded-lg bg-white p-6 border border-slate-200">
              <h2 className="mb-4 text-lg font-semibold text-slate-900">Where it went in {formatMonth(month)}</h2>
              <CategoryBreakdown categories={data.spendingByCategory} totalPaise={data.spendingPaise} />
            </section>

            <section className="rounded-lg bg-white p-6 border border-slate-200">
              <div className="mb-4 flex items-baseline justify-between">
                <h2 className="text-lg font-semibold text-slate-900">Recent transactions</h2>
                <Link to="/transactions" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
                  View all
                </Link>
              </div>
              <ul className="divide-y divide-slate-100">
                {data.recent.map((t) => (
                  <li key={t.id} className="flex items-center justify-between gap-3 py-2 text-sm">
                    <span className="min-w-0">
                      <span className="block truncate font-medium text-slate-900">{t.description}</span>
                      <span className="text-xs text-slate-500">
                        {formatDate(t.date)} &middot; {t.categoryName ?? 'Uncategorised'}
                      </span>
                    </span>
                    <span
                      className={`shrink-0 tabular-nums font-medium ${
                        t.amountPaise > 0 ? 'text-emerald-700' : 'text-slate-900'
                      }`}
                    >
                      {t.amountPaise > 0 ? '+' : ''}
                      {formatPaise(t.amountPaise)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          </div>
        </>
      )}
    </div>
  )
}
