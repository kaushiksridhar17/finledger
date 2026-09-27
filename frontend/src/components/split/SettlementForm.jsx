import { useState } from 'react'
import { addSettlement } from '../../api/groups.js'
import { todayIso } from '../../lib/dates.js'
import { paiseToInput, parseRupeesToPaise } from '../../lib/money.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import SelectField from '../SelectField.jsx'
import TextField from '../TextField.jsx'

// Record money paid back between two people. Pass `prefill` ({ fromMemberId, toMemberId, amountPaise })
// to start from a settle-up suggestion.
export default function SettlementForm({ group, prefill, onClose, onSaved }) {
  const self = group.members.find((m) => m.self)
  const firstFriend = group.members.find((m) => !m.self)
  const label = (m) => (m.self ? 'You' : m.name)

  const [form, setForm] = useState({
    fromMemberId: String(prefill?.fromMemberId ?? firstFriend?.id ?? ''),
    toMemberId: String(prefill?.toMemberId ?? self?.id ?? ''),
    amount: prefill ? paiseToInput(prefill.amountPaise) : '',
    date: todayIso(),
    note: '',
  })
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
    const errors = {}
    if (amountPaise === null || amountPaise <= 0) errors.amount = 'Enter an amount like 1200'
    if (form.fromMemberId === form.toMemberId) errors.toMemberId = 'Choose two different people'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setBusy(true)
    try {
      const updated = await addSettlement(group.id, {
        fromMemberId: Number(form.fromMemberId),
        toMemberId: Number(form.toMemberId),
        amountPaise,
        date: form.date,
        note: form.note.trim() || null,
      })
      onSaved(updated)
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {})
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title="Record a payment" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />
        <div className="grid grid-cols-2 gap-3">
          <SelectField label="Who paid" name="fromMemberId" value={form.fromMemberId} onChange={handleChange}>
            {group.members.map((m) => (
              <option key={m.id} value={m.id}>
                {label(m)}
              </option>
            ))}
          </SelectField>
          <SelectField label="Paid to" name="toMemberId" value={form.toMemberId} onChange={handleChange} error={fieldErrors.toMemberId}>
            {group.members.map((m) => (
              <option key={m.id} value={m.id}>
                {label(m)}
              </option>
            ))}
          </SelectField>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <TextField label="Amount (₹)" name="amount" value={form.amount} onChange={handleChange} error={fieldErrors.amount} />
          <TextField label="Date" name="date" type="date" value={form.date} onChange={handleChange} error={fieldErrors.date} />
        </div>
        <TextField label="Note (optional)" name="note" value={form.note} onChange={handleChange} error={fieldErrors.note} />
        <div className="flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
          >
            {busy ? 'Saving...' : 'Record payment'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
