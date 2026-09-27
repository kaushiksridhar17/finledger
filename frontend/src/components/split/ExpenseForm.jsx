import { useState } from 'react'
import { addExpense, deleteExpense, updateExpense } from '../../api/groups.js'
import { todayIso } from '../../lib/dates.js'
import { formatPaise, paiseToInput, parseRupeesToPaise } from '../../lib/money.js'
import { SPLIT_TYPES, computeSplit, percentText } from '../../lib/splits.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import SelectField from '../SelectField.jsx'
import TextField from '../TextField.jsx'

const smallInput =
  'w-24 rounded-lg border border-slate-300 px-2 py-1.5 text-right text-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200'

// What was typed for each person when editing an existing expense
function inputFor(splitType, share) {
  if (!share || share.value == null) return ''
  if (splitType === 'EXACT') return paiseToInput(share.value)
  if (splitType === 'PERCENT') return percentText(share.value)
  return String(share.value)
}

function initialPeople(members, expense) {
  return members.map((m) => {
    const share = expense?.shares.find((s) => s.memberId === m.id)
    return {
      memberId: m.id,
      included: expense ? Boolean(share) : true,
      input: expense ? inputFor(expense.splitType, share) : '',
    }
  })
}

// Add or edit a shared expense: who paid, how much, and how it's split. Each person's share is
// worked out live as you type, and the form won't save a split that doesn't add up.
export default function ExpenseForm({ group, expense, onClose, onSaved }) {
  const editing = Boolean(expense)
  const self = group.members.find((m) => m.self)
  const nameOf = (id) => {
    const member = group.members.find((m) => m.id === id)
    return member?.self ? 'You' : member?.name
  }
  const inputLabel = (id) => {
    const what = { EXACT: 'amount', PERCENT: 'percentage', SHARES: 'shares' }[form.splitType]
    const member = group.members.find((m) => m.id === id)
    return member?.self ? `Your ${what}` : `${member?.name}'s ${what}`
  }

  const [form, setForm] = useState({
    description: expense?.description ?? '',
    amount: expense ? paiseToInput(expense.amountPaise) : '',
    date: expense?.date ?? todayIso(),
    paidBy: String(expense?.paidByMemberId ?? self?.id ?? ''),
    splitType: expense?.splitType ?? 'EQUAL',
  })
  const [people, setPeople] = useState(() => initialPeople(group.members, expense))
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const totalPaise = parseRupeesToPaise(form.amount)
  const split = computeSplit(totalPaise ?? 0, form.splitType, people)
  const shareOf = new Map(split.shares.map((s) => [s.memberId, s.sharePaise]))

  function handleChange(event) {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
  }

  function chooseSplitType(splitType) {
    setForm((current) => ({ ...current, splitType }))
    // One share each is the natural starting point; other inputs start empty
    setPeople((current) => current.map((p) => ({ ...p, input: splitType === 'SHARES' ? '1' : '' })))
  }

  function setPerson(memberId, changes) {
    setPeople((current) => current.map((p) => (p.memberId === memberId ? { ...p, ...changes } : p)))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)

    const errors = {}
    if (!form.description.trim()) errors.description = 'Say what it was for'
    if (totalPaise === null || totalPaise <= 0) errors.amount = 'Enter an amount like 2400 or 1,299.50'
    if (!form.date) errors.date = 'Choose a date'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    // The split's problem is already shown under the list of people
    if (split.error) return

    const body = {
      description: form.description.trim(),
      amountPaise: totalPaise,
      date: form.date,
      paidByMemberId: Number(form.paidBy),
      splitType: form.splitType,
      shares: split.shares.map((s) => ({ memberId: s.memberId, value: s.value })),
    }

    setBusy(true)
    try {
      const updated = editing
        ? await updateExpense(group.id, expense.id, body)
        : await addExpense(group.id, body)
      onSaved(updated)
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {})
      setError(err.message)
      setBusy(false)
    }
  }

  async function handleDelete() {
    if (!window.confirm(`Delete "${expense.description}"? Everyone's balance will change.`)) return
    setBusy(true)
    try {
      onSaved(await deleteExpense(group.id, expense.id))
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  const assigned = split.shares.reduce((sum, s) => sum + s.sharePaise, 0)

  return (
    <Modal title={editing ? 'Edit expense' : 'Add an expense'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />

        <TextField
          label="What was it for?"
          name="description"
          value={form.description}
          onChange={handleChange}
          error={fieldErrors.description}
        />

        <div className="grid grid-cols-2 gap-3">
          <TextField label="Amount (₹)" name="amount" value={form.amount} onChange={handleChange} error={fieldErrors.amount} />
          <TextField label="Date" name="date" type="date" value={form.date} onChange={handleChange} error={fieldErrors.date} />
        </div>

        <SelectField label="Paid by" name="paidBy" value={form.paidBy} onChange={handleChange} error={fieldErrors.paidByMemberId}>
          {group.members.map((m) => (
            <option key={m.id} value={m.id}>
              {m.self ? 'You' : m.name}
            </option>
          ))}
        </SelectField>

        <div>
          <p className="text-sm font-medium text-slate-700">Split</p>
          <div className="mt-1 flex flex-wrap gap-1 rounded-lg bg-slate-100 p-1" role="group" aria-label="How to split">
            {SPLIT_TYPES.map((type) => (
              <button
                key={type.value}
                type="button"
                onClick={() => chooseSplitType(type.value)}
                aria-pressed={form.splitType === type.value}
                className={`flex-1 whitespace-nowrap rounded-md px-2 py-1.5 text-sm font-medium ${
                  form.splitType === type.value ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-600 hover:text-slate-900'
                }`}
              >
                {type.label}
              </button>
            ))}
          </div>

          <ul className="mt-3 divide-y divide-slate-100 rounded-lg border border-slate-200">
            {people.map((person) => (
              <li key={person.memberId} className="flex items-center justify-between gap-3 px-3 py-2">
                {form.splitType === 'EQUAL' ? (
                  <label className="flex items-center gap-2 text-sm text-slate-800">
                    <input
                      type="checkbox"
                      checked={person.included}
                      onChange={(e) => setPerson(person.memberId, { included: e.target.checked })}
                      className="h-4 w-4 accent-emerald-600"
                    />
                    {nameOf(person.memberId)}
                  </label>
                ) : (
                  <span className="text-sm text-slate-800">{nameOf(person.memberId)}</span>
                )}

                <div className="flex items-center gap-3">
                  {form.splitType !== 'EQUAL' && (
                    <span className="flex items-center gap-1 text-sm text-slate-500">
                      {form.splitType === 'EXACT' && '₹'}
                      <input
                        aria-label={inputLabel(person.memberId)}
                        inputMode="decimal"
                        value={person.input}
                        onChange={(e) => setPerson(person.memberId, { input: e.target.value })}
                        placeholder="0"
                        className={smallInput}
                      />
                      {form.splitType === 'PERCENT' && '%'}
                    </span>
                  )}
                  <span className="w-24 text-right text-sm font-medium tabular-nums text-slate-900">
                    {shareOf.has(person.memberId) ? formatPaise(shareOf.get(person.memberId)) : '-'}
                  </span>
                </div>
              </li>
            ))}
          </ul>

          {totalPaise > 0 && (
            <p className={`mt-2 text-sm ${split.error ? 'text-rose-700' : 'text-slate-500'}`}>
              {split.error ?? `${formatPaise(assigned)} split between ${split.shares.length} ${split.shares.length === 1 ? 'person' : 'people'}.`}
            </p>
          )}
        </div>

        <div className="flex items-center justify-between">
          {editing ? (
            <button type="button" onClick={handleDelete} disabled={busy} className="text-sm font-medium text-rose-600 hover:text-rose-700">
              Delete expense
            </button>
          ) : (
            <span />
          )}
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
          >
            {busy ? 'Saving...' : editing ? 'Save changes' : 'Add expense'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
