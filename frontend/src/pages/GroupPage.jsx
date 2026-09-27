import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { deleteGroup, getGroup, renameGroup } from '../api/groups.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import ActivityList from '../components/split/ActivityList.jsx'
import ExpenseForm from '../components/split/ExpenseForm.jsx'
import MemberForm from '../components/split/MemberForm.jsx'
import PeopleCard from '../components/split/PeopleCard.jsx'
import SettleUpCard from '../components/split/SettleUpCard.jsx'
import SettlementForm from '../components/split/SettlementForm.jsx'
import SuggestionBanner from '../components/split/SuggestionBanner.jsx'
import { formatPaise } from '../lib/money.js'

function Tile({ label, value, hint, tone = 'text-slate-900' }) {
  return (
    <div className="rounded-2xl bg-white p-4 shadow-sm ring-1 ring-slate-200">
      <p className="text-sm text-slate-500">{label}</p>
      <p className={`mt-1 text-xl font-semibold tabular-nums ${tone}`}>{value}</p>
      {hint && <p className="text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

// One group: repayments spotted in your bank, totals, activity, the settle-up plan and the people in it.
// Every change returns the whole updated group from the API, so the page just swaps it in.
export default function GroupPage() {
  const { groupId } = useParams()
  const navigate = useNavigate()
  const [group, setGroup] = useState(null)
  const [error, setError] = useState(null)
  const [dialog, setDialog] = useState(null) // { type: 'expense' | 'payment' | 'member', ... }

  useEffect(() => {
    let cancelled = false
    getGroup(groupId)
      .then((result) => {
        if (!cancelled) setGroup(result)
      })
      .catch((err) => {
        if (!cancelled) setError(err.status === 404 ? 'This group no longer exists.' : err.message)
      })
    return () => {
      cancelled = true
    }
  }, [groupId])

  const closeDialog = useCallback(() => setDialog(null), [])
  const applyUpdate = useCallback((updated) => {
    setGroup(updated)
    setError(null)
    setDialog(null)
  }, [])

  async function rename() {
    const name = window.prompt('Rename this group', group.name)
    if (!name || !name.trim() || name.trim() === group.name) return
    try {
      applyUpdate(await renameGroup(group.id, name.trim()))
    } catch (err) {
      setError(err.message)
    }
  }

  async function remove() {
    if (!window.confirm(`Delete ${group.name} with all its expenses and payments? This can't be undone.`)) return
    try {
      await deleteGroup(group.id)
      navigate('/split', { replace: true })
    } catch (err) {
      setError(err.message)
    }
  }

  if (!group) {
    return (
      <div className="space-y-4">
        <Link to="/split" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          &larr; All groups
        </Link>
        <ErrorBanner message={error} />
        {!error && <p className="text-slate-500">Loading...</p>}
      </div>
    )
  }

  const friends = group.members.filter((m) => !m.self).map((m) => m.name)
  const balance = group.myBalancePaise

  return (
    <div className="space-y-6">
      <div>
        <Link to="/split" className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          &larr; All groups
        </Link>
        <div className="mt-2 flex flex-wrap items-end justify-between gap-4">
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold text-slate-900">{group.name}</h1>
            <p className="mt-1 truncate text-slate-500">With {friends.join(', ')}</p>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" onClick={rename} className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100">
              Rename
            </button>
            <button type="button" onClick={remove} className="rounded-lg px-3 py-2 text-sm font-medium text-rose-600 hover:bg-rose-50">
              Delete
            </button>
            <button
              type="button"
              onClick={() => setDialog({ type: 'payment' })}
              className="rounded-lg px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-100"
            >
              Record a payment
            </button>
            <button
              type="button"
              onClick={() => setDialog({ type: 'expense' })}
              className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
            >
              Add expense
            </button>
          </div>
        </div>
      </div>

      <ErrorBanner message={error} />
      <SuggestionBanner group={group} onChanged={applyUpdate} onError={setError} />

      <div className="grid gap-4 sm:grid-cols-3">
        <Tile label="Group spending" value={formatPaise(group.totalSpentPaise)} />
        <Tile label="Your share" value={formatPaise(group.mySharePaise)} />
        {balance === 0 ? (
          <Tile label="Your balance" value="Settled up" />
        ) : balance > 0 ? (
          <Tile label="You're owed" value={formatPaise(balance)} tone="text-emerald-700" hint="Friends owe you this" />
        ) : (
          <Tile label="You owe" value={formatPaise(-balance)} tone="text-rose-700" hint="You owe friends this" />
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-3 lg:col-span-2">
          <h2 className="text-lg font-semibold text-slate-900">Activity</h2>
          <ActivityList
            group={group}
            onEditExpense={(expense) => setDialog({ type: 'expense', expense })}
            onChanged={applyUpdate}
            onError={setError}
          />
        </div>
        {/* On phones the settle-up plan comes before the activity list */}
        <div className="order-first space-y-4 lg:order-none">
          <SettleUpCard
            group={group}
            onChanged={applyUpdate}
            onEditMember={(member) => setDialog({ type: 'member', member })}
            onError={setError}
          />
          <PeopleCard
            group={group}
            onChanged={applyUpdate}
            onEdit={(member) => setDialog({ type: 'member', member })}
            onAdd={() => setDialog({ type: 'member', member: null })}
            onError={setError}
          />
        </div>
      </div>

      {dialog?.type === 'expense' && (
        <ExpenseForm group={group} expense={dialog.expense ?? null} onClose={closeDialog} onSaved={applyUpdate} />
      )}
      {dialog?.type === 'payment' && <SettlementForm group={group} onClose={closeDialog} onSaved={applyUpdate} />}
      {dialog?.type === 'member' && (
        <MemberForm groupId={group.id} member={dialog.member} onClose={closeDialog} onSaved={applyUpdate} />
      )}
    </div>
  )
}
