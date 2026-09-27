import { useCallback, useEffect, useState } from 'react'
import { deleteFundTransaction, getPortfolio, getSipSuggestions, refreshPrices, unlinkSip } from '../api/investments.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import AddFundTransactionForm from '../components/investments/AddFundTransactionForm.jsx'
import LinkSipForm from '../components/investments/LinkSipForm.jsx'
import PortfolioChart from '../components/investments/PortfolioChart.jsx'
import { formatDate } from '../lib/dates.js'
import { formatNav, formatRate, formatUnits, gainPercent, splitFundName } from '../lib/investments.js'
import { formatPaise } from '../lib/money.js'

const SHOWN_TRANSACTIONS = 12

function Tile({ label, value, note, tone = 'text-slate-900' }) {
  return (
    <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-2xl font-semibold tabular-nums ${tone}`}>{value}</p>
      {note && <p className="mt-1 text-xs text-slate-500">{note}</p>}
    </div>
  )
}

// Gains and losses are always written with a sign and a word, never shown by colour alone
function gainTone(paise) {
  if (paise > 0) return 'text-emerald-700'
  if (paise < 0) return 'text-rose-700'
  return 'text-slate-900'
}

function signedPaise(paise) {
  return `${paise > 0 ? '+' : paise < 0 ? '-' : ''}${formatPaise(Math.abs(paise))}`
}

// Mutual funds: what you hold, what it's worth at the latest NAV, and your real yearly return (XIRR).
export default function InvestmentsPage() {
  const [portfolio, setPortfolio] = useState(null)
  const [suggestions, setSuggestions] = useState([])
  const [error, setError] = useState(null)
  const [dialog, setDialog] = useState(null) // { type: 'trade', fund } | { type: 'link', suggestion }
  const [refreshing, setRefreshing] = useState(false)
  const [showAll, setShowAll] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([getPortfolio(), getSipSuggestions()])
      .then(([result, sipSuggestions]) => {
        if (cancelled) return
        setPortfolio(result)
        setSuggestions(sipSuggestions)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  const closeDialog = useCallback(() => setDialog(null), [])

  // Every change returns the new portfolio; suggestions may have changed too, so reload those
  const applyUpdate = useCallback((updated) => {
    setPortfolio(updated)
    setDialog(null)
    setError(null)
    getSipSuggestions().then(setSuggestions).catch(() => {})
  }, [])

  async function act(action, confirmText) {
    if (confirmText && !window.confirm(confirmText)) return
    try {
      applyUpdate(await action())
    } catch (err) {
      setError(err.message)
    }
  }

  async function refresh() {
    setRefreshing(true)
    try {
      applyUpdate(await refreshPrices())
    } catch (err) {
      setError(err.message)
    } finally {
      setRefreshing(false)
    }
  }

  const hasFunds = portfolio && portfolio.funds.length > 0
  const transactions = portfolio?.transactions ?? []
  const shownTransactions = showAll ? transactions : transactions.slice(0, SHOWN_TRANSACTIONS)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Investments</h1>
          <p className="mt-1 text-slate-500">
            Your mutual funds, valued at the latest NAV from AMFI (via mfapi.in), with your real yearly return.
          </p>
        </div>
        <div className="flex gap-2">
          {hasFunds && (
            <button
              type="button"
              onClick={refresh}
              disabled={refreshing}
              className="rounded-lg px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-100 disabled:opacity-60"
            >
              {refreshing ? 'Refreshing...' : 'Refresh prices'}
            </button>
          )}
          <button
            type="button"
            onClick={() => setDialog({ type: 'trade', fund: null })}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
          >
            Add purchase
          </button>
        </div>
      </div>

      <ErrorBanner message={error} />
      {!portfolio && !error && <p className="text-slate-500">Loading...</p>}
      {!portfolio && error && (
        <button
          type="button"
          onClick={() => {
            setError(null)
            setReloadKey((k) => k + 1)
          }}
          className="text-sm font-medium text-emerald-700 hover:text-emerald-800"
        >
          Try again
        </button>
      )}

      {suggestions.length > 0 && (
        <section className="rounded-2xl bg-sky-50 p-5 ring-1 ring-sky-200" aria-label="SIPs found in your bank transactions">
          <h2 className="font-semibold text-slate-900">SIPs found in your bank transactions</h2>
          <p className="text-sm text-slate-600">
            Link each one to its fund and every instalment, past and future, is added as a purchase.
          </p>
          <ul className="mt-3 space-y-2">
            {suggestions.map((s) => (
              <li key={s.matchKey} className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-white px-4 py-3 ring-1 ring-sky-100">
                <div className="min-w-0">
                  <p className="truncate font-mono text-xs text-slate-700">{s.label}</p>
                  <p className="text-sm text-slate-600">
                    {s.occurrences} debits of about {formatPaise(s.typicalAmountPaise)}, latest {formatDate(s.lastDate)}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => setDialog({ type: 'link', suggestion: s })}
                  className="rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
                >
                  Link to a fund
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}

      {portfolio && !hasFunds && suggestions.length === 0 && (
        <div className="rounded-2xl bg-white p-8 text-center text-slate-600 shadow-sm ring-1 ring-slate-200">
          No mutual funds yet. Add a purchase, or import a bank statement: monthly SIP debits are spotted automatically.
        </div>
      )}

      {hasFunds && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Tile label="Current value" value={formatPaise(portfolio.valuePaise)} note={portfolio.asOf && `NAVs as of ${formatDate(portfolio.asOf)}`} />
            <Tile label="Invested" value={formatPaise(portfolio.investedPaise)} note="What your current units cost" />
            <Tile
              label={portfolio.gainPaise >= 0 ? 'Gain' : 'Loss'}
              value={signedPaise(portfolio.gainPaise)}
              note={`${gainPercent(portfolio.gainPaise, portfolio.investedPaise)} on what you invested`}
              tone={gainTone(portfolio.gainPaise)}
            />
            <Tile
              label="Yearly return (XIRR)"
              value={formatRate(portfolio.xirr)}
              note={portfolio.xirr === null ? 'Shown once money has been in for a month' : 'Allows for when each rupee went in'}
              tone={gainTone(portfolio.xirr ?? 0)}
            />
          </div>

          {portfolio.history.length > 1 && (
            <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
              <h2 className="text-lg font-semibold text-slate-900">Value over the last year</h2>
              <p className="text-sm text-slate-500">Month-end value against the money you'd put in by then.</p>
              <div className="mt-4">
                <PortfolioChart history={portfolio.history} />
              </div>
            </section>
          )}

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-slate-900">Your funds</h2>
            <ul className="space-y-3">
              {portfolio.funds.map((fund) => {
                const { fund: fundName, plan } = splitFundName(fund.name)
                const share = portfolio.valuePaise > 0 ? (fund.valuePaise / portfolio.valuePaise) * 100 : 0
                return (
                  <li key={fund.schemeCode} className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
                    <div className="flex flex-wrap items-start justify-between gap-4">
                      <div className="min-w-0">
                        <p className="font-semibold text-slate-900">
                          {fundName}
                          {fund.sip && (
                            <span className="ml-2 rounded-full bg-sky-50 px-2 py-0.5 align-middle text-xs font-medium text-sky-800 ring-1 ring-sky-200">
                              SIP
                            </span>
                          )}
                        </p>
                        <p className="text-sm text-slate-500">
                          {[plan, fund.category?.replace(/^.*Scheme - /, '')].filter(Boolean).join(' · ')}
                        </p>
                        <p className="mt-1 text-sm text-slate-600">
                          {formatUnits(fund.units)} units &times; {formatNav(fund.latestNav)} on {formatDate(fund.navDate)}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-lg font-semibold tabular-nums text-slate-900">{formatPaise(fund.valuePaise)}</p>
                        <p className={`text-sm tabular-nums ${gainTone(fund.gainPaise)}`}>
                          {signedPaise(fund.gainPaise)} ({gainPercent(fund.gainPaise, fund.investedPaise)})
                        </p>
                        <p className="text-xs text-slate-500">
                          XIRR {formatRate(fund.xirr)} &middot; invested {formatPaise(fund.investedPaise)}
                        </p>
                      </div>
                    </div>
                    <div className="mt-3 flex items-center gap-3">
                      <div
                        className="h-1.5 flex-1 rounded-full bg-slate-100"
                        role="img"
                        aria-label={`${share.toFixed(0)}% of your portfolio`}
                      >
                        <div className="h-1.5 rounded-full bg-[#2a78d6]" style={{ width: `${share}%` }} />
                      </div>
                      <span className="w-28 text-right text-xs text-slate-500">{share.toFixed(0)}% of portfolio</span>
                      <button
                        type="button"
                        onClick={() => setDialog({ type: 'trade', fund: { schemeCode: fund.schemeCode, name: fund.name } })}
                        className="text-sm font-medium text-emerald-700 hover:text-emerald-800"
                      >
                        Add purchase
                      </button>
                    </div>
                  </li>
                )
              })}
            </ul>
          </section>
        </>
      )}

      {portfolio && portfolio.sips.length > 0 && (
        <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
          <h2 className="font-semibold text-slate-900">Linked SIPs</h2>
          <p className="text-sm text-slate-500">New debits like these become purchases automatically after each import.</p>
          <ul className="mt-2 divide-y divide-slate-100">
            {portfolio.sips.map((sip) => (
              <li key={sip.id} className="flex flex-wrap items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-slate-900">{splitFundName(sip.schemeName).fund}</p>
                  <p className="truncate font-mono text-xs text-slate-500">{sip.label}</p>
                </div>
                <div className="flex items-center gap-4">
                  <span className="text-sm text-slate-600">
                    {sip.purchases} {sip.purchases === 1 ? 'purchase' : 'purchases'}
                  </span>
                  <button
                    type="button"
                    onClick={() => act(() => unlinkSip(sip.id), 'Unlink this SIP? Its purchases will be removed from your portfolio.')}
                    className="text-sm font-medium text-rose-600 hover:text-rose-700"
                  >
                    Unlink
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {transactions.length > 0 && (
        <section className="overflow-hidden rounded-2xl bg-white shadow-sm ring-1 ring-slate-200">
          <h2 className="px-5 pt-5 font-semibold text-slate-900">Purchases and redemptions</h2>
          <div className="overflow-x-auto">
            <table className="mt-3 w-full min-w-[640px] text-left text-sm">
              <thead className="text-slate-500">
                <tr className="border-b border-slate-100">
                  <th className="px-5 py-2 font-medium">Date</th>
                  <th className="px-2 py-2 font-medium">Fund</th>
                  <th className="px-2 py-2 text-right font-medium">Amount</th>
                  <th className="px-2 py-2 text-right font-medium">Units</th>
                  <th className="px-2 py-2 text-right font-medium">NAV</th>
                  <th className="px-5 py-2 font-medium">
                    <span className="sr-only">Source</span>
                  </th>
                </tr>
              </thead>
              <tbody className="tabular-nums text-slate-700">
                {shownTransactions.map((t) => (
                  <tr key={t.id} className="border-b border-slate-50">
                    <td className="whitespace-nowrap px-5 py-2">{formatDate(t.date)}</td>
                    <td className="max-w-[16rem] truncate px-2 py-2">
                      <span className={t.type === 'SELL' ? 'font-medium text-slate-900' : ''}>{t.type === 'SELL' ? 'Sold ' : ''}</span>
                      {splitFundName(t.schemeName).fund}
                    </td>
                    <td className="px-2 py-2 text-right">{formatPaise(t.amountPaise)}</td>
                    <td className="px-2 py-2 text-right">{formatUnits(t.units)}</td>
                    <td className="px-2 py-2 text-right">{formatNav(t.nav)}</td>
                    <td className="whitespace-nowrap px-5 py-2 text-right">
                      {t.fromBank ? (
                        <span className="text-xs text-slate-500">SIP from bank</span>
                      ) : (
                        <button
                          type="button"
                          onClick={() => act(() => deleteFundTransaction(t.id), 'Delete this entry?')}
                          className="text-xs font-medium text-rose-600 hover:text-rose-700"
                        >
                          Delete
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {transactions.length > SHOWN_TRANSACTIONS && (
            <button
              type="button"
              onClick={() => setShowAll((s) => !s)}
              className="w-full px-5 py-3 text-sm font-medium text-emerald-700 hover:bg-slate-50"
            >
              {showAll ? 'Show fewer' : `Show all ${transactions.length}`}
            </button>
          )}
        </section>
      )}

      {dialog?.type === 'trade' && <AddFundTransactionForm fund={dialog.fund} onClose={closeDialog} onSaved={applyUpdate} />}
      {dialog?.type === 'link' && <LinkSipForm suggestion={dialog.suggestion} onClose={closeDialog} onSaved={applyUpdate} />}

    </div>
  )
}
