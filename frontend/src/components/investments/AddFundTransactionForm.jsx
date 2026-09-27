import { useState } from 'react'
import { addFundTransaction } from '../../api/investments.js'
import { todayIso } from '../../lib/dates.js'
import { parseRupeesToPaise } from '../../lib/money.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import TextField from '../TextField.jsx'
import FundSearch from './FundSearch.jsx'

// Record a purchase or redemption. Units are worked out from that day's NAV unless you type them in.
// Pass `fund` ({ schemeCode, name }) to start with a fund already chosen, e.g. "Buy more".
export default function AddFundTransactionForm({ fund = null, onClose, onSaved }) {
  const [selected, setSelected] = useState(fund)
  const [form, setForm] = useState({ type: 'BUY', date: todayIso(), amount: '', units: '' })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    const amountPaise = parseRupeesToPaise(form.amount)
    const unitsText = form.units.trim()
    const errors = {}
    if (!selected) errors.fund = 'Search for the fund and pick it from the list'
    if (amountPaise === null || amountPaise <= 0) errors.amount = 'Enter an amount like 5000'
    if (unitsText && !/^\d+(\.\d{1,3})?$/.test(unitsText)) errors.units = 'Units look like 55.581 (up to 3 decimals)'
    if (!form.date) errors.date = 'Choose a date'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setBusy(true)
    try {
      const portfolio = await addFundTransaction({
        schemeCode: selected.schemeCode,
        type: form.type,
        date: form.date,
        amountPaise,
        units: unitsText ? Number(unitsText) : null,
      })
      onSaved(portfolio)
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {})
      setError(err.message)
      setBusy(false)
    }
  }

  const selling = form.type === 'SELL'
  return (
    <Modal title={selling ? 'Record a redemption' : 'Record a purchase'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />

        <div className="flex gap-1 rounded-lg bg-slate-100 p-1" role="group" aria-label="Buy or sell">
          {[
            ['BUY', 'Bought'],
            ['SELL', 'Sold (redeemed)'],
          ].map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={form.type === value}
              onClick={() => setForm((current) => ({ ...current, type: value }))}
              className={`flex-1 rounded-md px-2 py-1.5 text-sm font-medium ${
                form.type === value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">Fund</p>
          <FundSearch selected={selected} onSelect={setSelected} />
          {fieldErrors.fund && <p className="mt-1 text-sm text-rose-600">{fieldErrors.fund}</p>}
        </div>

        <div className="grid grid-cols-2 gap-3">
          <TextField
            label={selling ? 'Amount received (₹)' : 'Amount invested (₹)'}
            name="amount"
            value={form.amount}
            onChange={handleChange}
            error={fieldErrors.amount ?? fieldErrors.amountPaise}
          />
          <TextField label="Date" name="date" type="date" value={form.date} onChange={handleChange} error={fieldErrors.date} />
        </div>

        <div>
          <TextField label="Units (optional)" name="units" value={form.units} onChange={handleChange} error={fieldErrors.units} />
          <p className="mt-1 text-xs text-slate-500">
            Leave blank to work them out from that day's NAV, or copy them from your statement to match it exactly.
          </p>
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {busy ? 'Saving...' : selling ? 'Record redemption' : 'Record purchase'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
