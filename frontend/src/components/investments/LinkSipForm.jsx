import { useState } from 'react'
import { linkSip } from '../../api/investments.js'
import { formatDate } from '../../lib/dates.js'
import { formatPaise } from '../../lib/money.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import FundSearch from './FundSearch.jsx'

// Which fund is this SIP? Once linked, every matching debit (past and future) becomes a purchase.
export default function LinkSipForm({ suggestion, onClose, onSaved }) {
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function handleLink() {
    if (!selected) {
      setError('Search for the fund and pick it from the list')
      return
    }
    setBusy(true)
    setError(null)
    try {
      onSaved(await linkSip(suggestion.matchKey, selected.schemeCode))
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title="Link this SIP to a fund" onClose={onClose}>
      <div className="space-y-4">
        <div className="rounded-lg bg-slate-50 px-3 py-2 text-sm">
          <p className="font-mono text-xs text-slate-700">{suggestion.label}</p>
          <p className="mt-1 text-slate-600">
            {suggestion.occurrences} debits of about {formatPaise(suggestion.typicalAmountPaise)}, the latest on{' '}
            {formatDate(suggestion.lastDate)}
          </p>
        </div>
        <ErrorBanner message={error} />
        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">Which fund is it?</p>
          <FundSearch initialQuery={suggestion.suggestedQuery} selected={selected} onSelect={setSelected} />
          <p className="mt-1 text-xs text-slate-500">
            Pick the exact plan your SIP is in: Direct or Regular, Growth or IDCW. It's on your fund statement.
          </p>
        </div>
        <div className="flex justify-end">
          <button
            type="button"
            onClick={handleLink}
            disabled={busy}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {busy ? 'Linking...' : 'Link and add purchases'}
          </button>
        </div>
      </div>
    </Modal>
  )
}
