import { useState } from 'react'
import { addMember, updateMember } from '../../api/groups.js'
import { isValidUpiId } from '../../lib/upi.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import TextField from '../TextField.jsx'

// Add a friend, or change someone's name or UPI ID (including your own, so friends can pay you)
export default function MemberForm({ groupId, member, onClose, onSaved }) {
  const editing = Boolean(member)
  const [name, setName] = useState(member?.name ?? '')
  const [upiId, setUpiId] = useState(member?.upiId ?? '')
  const [fieldErrors, setFieldErrors] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const title = !editing ? 'Add someone' : member.self ? 'Your details' : `Edit ${member.name}`

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)
    const errors = {}
    if (!name.trim()) errors.name = 'Enter a name'
    if (upiId.trim() && !isValidUpiId(upiId)) errors.upiId = 'That doesn\'t look like a UPI ID, e.g. name@okaxis'
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return

    setBusy(true)
    try {
      const body = { name: name.trim(), upiId: upiId.trim() }
      const group = editing ? await updateMember(groupId, member.id, body) : await addMember(groupId, body)
      onSaved(group)
    } catch (err) {
      setFieldErrors(err.fieldErrors ?? {})
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />
        <TextField label="Name" name="memberName" value={name} onChange={(e) => setName(e.target.value)} error={fieldErrors.name} />
        <div>
          <TextField
            label="UPI ID (optional)"
            name="upiId"
            value={upiId}
            onChange={(e) => setUpiId(e.target.value)}
            error={fieldErrors.upiId}
          />
          <p className="mt-1 text-xs text-slate-500">
            {member?.self
              ? 'Your UPI ID lets you show friends a QR code to pay you back.'
              : 'Used for "Pay with UPI", and to spot their repayments in your bank statement.'}
          </p>
        </div>
        <div className="flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60"
          >
            {busy ? 'Saving...' : 'Save'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
