import { useState } from 'react'
import { createTransaction, deleteTransaction, updateTransaction } from '../api/ledger.js'
import { todayIso } from '../lib/dates.js'
import { paiseToInput, parseRupeesToPaise } from '../lib/money.js'
import ErrorBanner from './ErrorBanner.jsx'
import Modal from './Modal.jsx'
import SelectField from './SelectField.jsx'
import TextField from './TextField.jsx'

// Add or edit a transaction. Pass transaction=null to add a new one.
// The person types a positive amount and picks "Money out" or "Money in"; we store it signed.
export default function TransactionForm({ transaction, accounts, categories, onClose, onSaved }) {
  const editing = Boolean(transaction)
  const activeAccounts = accounts.filter((a) => !a.archived || a.id === transaction?.accountId)

  const [form, setForm] = useState({
    direction: transaction && transaction.amountPaise > 0 ? 'IN' : 'OUT',
    amount: transaction ? paiseToInput(transaction.amountPaise) : '',
    date: transaction?.date ?? todayIso(),
    accountId: String(transaction?.accountId ?? activeAccounts[0]?.id ?? ''),
    categoryId: transaction?.categoryId ? String(transaction.categoryId) : '',
    description: transaction?.description ?? '',
    merchant: transaction?.merchant ?? '',
    notes: transaction?.notes ?? '',
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // Spending categories for money out, income categories for money in, transfers for both
  const mainKind = form.direction === 'OUT' ? 'EXPENSE' : 'INCOME'
  const mainCategories = categories.filter((c) => c.kind === mainKind)
  const transferCategories = categories.filter((c) => c.kind === 'TRANSFER')

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function setDirection(direction) {
    // A spending category makes no sense on income, so clear it when switching
    setForm((current) => {
      const selected = categories.find((c) => String(c.id) === current.categoryId)
      const stillFits = selected && (selected.kind === 'TRANSFER' || selected.kind === (direction === 'OUT' ? 'EXPENSE' : 'INCOME'))
      return { ...current, direction, categoryId: stillFits ? current.categoryId : '' }
    })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const paise = parseRupeesToPaise(form.amount)
    if (paise === null || paise <= 0) {
      setFieldErrors({ amount: 'Enter a positive amount like 450 or 1,299.50' })
      return
    }

    const body = {
      accountId: form.accountId ? Number(form.accountId) : null,
      categoryId: form.categoryId ? Number(form.categoryId) : null,
      amountPaise: form.direction === 'OUT' ? -paise : paise,
      date: form.date || null,
      description: form.description,
      merchant: form.merchant,
      notes: form.notes,
    }

    setBusy(true)
    try {
      if (editing) {
        await updateTransaction(transaction.id, body)
      } else {
        await createTransaction(body)
      }
      onSaved()
    } catch (err) {
      const errors = err.fieldErrors ?? {}
      setFieldErrors({ ...errors, amount: errors.amountPaise })
      setError(Object.keys(errors).length ? null : err.message)
      setBusy(false)
    }
  }

  async function handleDelete() {
    if (!window.confirm('Delete this transaction?')) return
    setBusy(true)
    setError(null)
    try {
      await deleteTransaction(transaction.id)
      onSaved()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title={editing ? 'Edit transaction' : 'Add transaction'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />

        <div className="grid grid-cols-2 gap-2 rounded-lg bg-slate-100 p-1">
          {[
            { value: 'OUT', label: 'Money out' },
            { value: 'IN', label: 'Money in' },
          ].map((option) => (
            <button
              key={option.value}
              type="button"
              onClick={() => setDirection(option.value)}
              className={`rounded-md px-3 py-1.5 text-sm font-medium ${
                form.direction === option.value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600'
              }`}
            >
              {option.label}
            </button>
          ))}
        </div>

        <div className="grid grid-cols-2 gap-4">
          <TextField
            label={'Amount (₹)'}
            name="amount"
            value={form.amount}
            onChange={handleChange}
            error={fieldErrors.amount}
          />
          <TextField label="Date" name="date" type="date" value={form.date} onChange={handleChange} error={fieldErrors.date} />
        </div>

        <TextField
          label="Description"
          name="description"
          value={form.description}
          onChange={handleChange}
          error={fieldErrors.description}
        />

        <div className="grid grid-cols-2 gap-4">
          <SelectField label="Account" name="accountId" value={form.accountId} onChange={handleChange} error={fieldErrors.accountId}>
            {activeAccounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </SelectField>

          <SelectField label="Category" name="categoryId" value={form.categoryId} onChange={handleChange} error={fieldErrors.categoryId}>
            <option value="">Uncategorised</option>
            <optgroup label={form.direction === 'OUT' ? 'Spending' : 'Income'}>
              {mainCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </optgroup>
            <optgroup label="Transfers">
              {transferCategories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </optgroup>
          </SelectField>
        </div>

        <TextField label="Merchant (optional)" name="merchant" value={form.merchant} onChange={handleChange} error={fieldErrors.merchant} />
        <TextField label="Notes (optional)" name="notes" value={form.notes} onChange={handleChange} error={fieldErrors.notes} />

        <div className="flex items-center justify-between pt-2">
          {editing ? (
            <button
              type="button"
              onClick={handleDelete}
              disabled={busy}
              className="text-sm font-medium text-rose-600 hover:text-rose-700 disabled:opacity-60"
            >
              Delete
            </button>
          ) : (
            <span />
          )}
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
          >
            {busy ? 'Saving...' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
