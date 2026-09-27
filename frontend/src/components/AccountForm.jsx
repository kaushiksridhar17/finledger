import { useState } from 'react'
import { createAccount, deleteAccount, updateAccount } from '../api/ledger.js'
import { paiseToInput, parseRupeesToPaise } from '../lib/money.js'
import ErrorBanner from './ErrorBanner.jsx'
import Modal from './Modal.jsx'
import SelectField from './SelectField.jsx'
import TextField from './TextField.jsx'
import { ACCOUNT_TYPES } from '../lib/accountTypes.js'

// Add or edit an account. Pass account=null to add a new one.
export default function AccountForm({ account, onClose, onSaved }) {
  const editing = Boolean(account)

  const [form, setForm] = useState({
    name: account?.name ?? '',
    type: account?.type ?? 'BANK',
    openingBalance: account ? signedInput(account.openingBalancePaise) : '0',
    archived: account?.archived ?? false,
  })
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  function handleChange(event) {
    const { name, value, type, checked } = event.target
    setForm((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    setFieldErrors({})

    const openingBalancePaise = parseRupeesToPaise(form.openingBalance)
    if (openingBalancePaise === null) {
      setFieldErrors({ openingBalance: 'Enter an amount like 5000 or 12,500.50' })
      return
    }

    const body = { name: form.name, type: form.type, openingBalancePaise }
    if (editing) body.archived = form.archived

    setBusy(true)
    try {
      if (editing) {
        await updateAccount(account.id, body)
      } else {
        await createAccount(body)
      }
      onSaved()
    } catch (err) {
      const errors = err.fieldErrors ?? {}
      setFieldErrors({ ...errors, openingBalance: errors.openingBalancePaise })
      setError(Object.keys(errors).length ? null : err.message)
      setBusy(false)
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Delete "${account.name}"? This can't be undone.`)) return
    setBusy(true)
    setError(null)
    try {
      await deleteAccount(account.id)
      onSaved()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title={editing ? 'Edit account' : 'Add account'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />

        <TextField label="Name" name="name" value={form.name} onChange={handleChange} error={fieldErrors.name} />

        <SelectField label="Type" name="type" value={form.type} onChange={handleChange} error={fieldErrors.type}>
          {ACCOUNT_TYPES.map((t) => (
            <option key={t.value} value={t.value}>
              {t.label}
            </option>
          ))}
        </SelectField>

        <div>
          <TextField
            label={'Opening balance (\u20B9)'}
            name="openingBalance"
            value={form.openingBalance}
            onChange={handleChange}
            error={fieldErrors.openingBalance}
          />
          <p className="mt-1 text-xs text-slate-500">
            What the account held before you started tracking it here. Use a minus sign for credit card dues.
          </p>
        </div>

        {editing && (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" name="archived" checked={form.archived} onChange={handleChange} />
            Archived (hidden from totals, history kept)
          </label>
        )}

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

function signedInput(paise) {
  return (paise < 0 ? '-' : '') + paiseToInput(paise)
}
