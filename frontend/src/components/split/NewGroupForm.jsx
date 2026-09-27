import { useState } from 'react'
import { createGroup } from '../../api/groups.js'
import { isValidUpiId } from '../../lib/upi.js'
import ErrorBanner from '../ErrorBanner.jsx'
import Modal from '../Modal.jsx'
import TextField from '../TextField.jsx'

const inputClass =
  'block w-full rounded-lg border border-slate-300 px-3 py-2 text-slate-900 shadow-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200'

// A new group: a name and the friends in it. You're added automatically.
export default function NewGroupForm({ onClose, onCreated }) {
  const [name, setName] = useState('')
  const [friends, setFriends] = useState([
    { name: '', upiId: '' },
    { name: '', upiId: '' },
  ])
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  function setFriend(index, field, value) {
    setFriends((current) => current.map((f, i) => (i === index ? { ...f, [field]: value } : f)))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError(null)

    const filled = friends
      .map((f) => ({ name: f.name.trim(), upiId: f.upiId.trim() }))
      .filter((f) => f.name !== '')
    if (!name.trim()) return setError('Give the group a name, like "Goa trip"')
    if (filled.length === 0) return setError('Add at least one friend')
    const badUpi = filled.find((f) => f.upiId && !isValidUpiId(f.upiId))
    if (badUpi) return setError(`${badUpi.name}'s UPI ID doesn't look right. It should be like name@okaxis.`)

    setBusy(true)
    try {
      const group = await createGroup(name.trim(), filled)
      onCreated(group)
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title="New group" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4" noValidate>
        <ErrorBanner message={error} />
        <TextField label="Group name" name="groupName" value={name} onChange={(e) => setName(e.target.value)} />

        <div>
          <p className="text-sm font-medium text-slate-700">Friends</p>
          <p className="text-xs text-slate-500">
            They don't need an account here. Add a UPI ID if you want to pay them from the app.
          </p>
          <div className="mt-2 space-y-2">
            {friends.map((friend, index) => (
              <div key={index} className="grid grid-cols-2 gap-2">
                <input
                  aria-label={`Friend ${index + 1} name`}
                  placeholder="Name"
                  value={friend.name}
                  onChange={(e) => setFriend(index, 'name', e.target.value)}
                  className={inputClass}
                />
                <input
                  aria-label={`Friend ${index + 1} UPI ID`}
                  placeholder="UPI ID (optional)"
                  value={friend.upiId}
                  onChange={(e) => setFriend(index, 'upiId', e.target.value)}
                  className={inputClass}
                />
              </div>
            ))}
          </div>
          {friends.length < 20 && (
            <button
              type="button"
              onClick={() => setFriends((current) => [...current, { name: '', upiId: '' }])}
              className="mt-2 text-sm font-medium text-emerald-700 hover:text-emerald-800"
            >
              + Add another friend
            </button>
          )}
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={busy}
            className="rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-800 disabled:opacity-60"
          >
            {busy ? 'Creating...' : 'Create group'}
          </button>
        </div>
      </form>
    </Modal>
  )
}
