import { useEffect, useState } from 'react'
import { searchFunds } from '../../api/investments.js'
import { splitFundName } from '../../lib/investments.js'

const inputClass =
  'block w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 shadow-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200'

// Type part of a fund's name, pick it from the list. Searches mfapi.in through our API, 300 ms after typing stops.
export default function FundSearch({ initialQuery = '', selected, onSelect }) {
  const [query, setQuery] = useState(initialQuery)
  const [results, setResults] = useState([])
  const [status, setStatus] = useState('idle') // idle | searching | done | error
  const [error, setError] = useState(null)

  useEffect(() => {
    const trimmed = query.trim()
    if (trimmed.length < 3) return undefined
    let cancelled = false
    const timer = setTimeout(() => {
      setStatus('searching')
      searchFunds(trimmed)
        .then((hits) => {
          if (cancelled) return
          setResults(hits)
          setStatus('done')
        })
        .catch((err) => {
          if (cancelled) return
          setError(err.message)
          setStatus('error')
        })
    }, 300)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [query])

  if (selected) {
    const { fund, plan } = splitFundName(selected.name)
    return (
      <div className="flex items-start justify-between gap-3 rounded-lg bg-emerald-50 px-3 py-2 border border-emerald-200">
        <div className="min-w-0">
          <p className="font-medium text-slate-900">{fund}</p>
          <p className="text-xs text-slate-600">
            {plan} &middot; scheme {selected.schemeCode}
          </p>
        </div>
        <button type="button" onClick={() => onSelect(null)} className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          Change
        </button>
      </div>
    )
  }

  const trimmed = query.trim()
  return (
    <div>
      <input
        aria-label="Search for a fund"
        placeholder="Search by name, e.g. parag parikh flexi"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        className={inputClass}
      />
      {trimmed.length > 0 && trimmed.length < 3 && <p className="mt-1 text-xs text-slate-500">Keep typing...</p>}
      {trimmed.length >= 3 && status === 'searching' && <p className="mt-1 text-xs text-slate-500">Searching...</p>}
      {trimmed.length >= 3 && status === 'error' && <p className="mt-1 text-sm text-rose-700">{error}</p>}
      {trimmed.length >= 3 && status === 'done' && results.length === 0 && (
        <p className="mt-1 text-sm text-slate-500">No funds match. Try fewer words, like "parag flexi".</p>
      )}
      {trimmed.length >= 3 && results.length > 0 && (
        <ul className="mt-2 max-h-56 divide-y divide-slate-100 overflow-y-auto rounded-lg border border-slate-200" aria-label="Matching funds">
          {results.map((hit) => {
            const { fund, plan } = splitFundName(hit.name)
            return (
              <li key={hit.schemeCode}>
                <button type="button" onClick={() => onSelect(hit)} className="block w-full px-3 py-2 text-left hover:bg-slate-50">
                  <span className="block text-sm font-medium text-slate-900">{fund}</span>
                  <span className="block text-xs text-slate-500">{plan || `Scheme ${hit.schemeCode}`}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
