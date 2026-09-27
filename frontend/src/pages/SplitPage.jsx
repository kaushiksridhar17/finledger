import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { listGroups } from '../api/groups.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import NewGroupForm from '../components/split/NewGroupForm.jsx'
import { formatPaise } from '../lib/money.js'

function friendsText(names) {
  if (names.length === 0) return 'Just you'
  if (names.length === 1) return `With ${names[0]}`
  return `With ${names.slice(0, -1).join(', ')} and ${names[names.length - 1]}`
}

function Standing({ balancePaise }) {
  if (balancePaise === 0) return <p className="text-sm text-slate-500">All settled up</p>
  return balancePaise > 0 ? (
    <p className="text-sm text-emerald-700">
      You're owed <span className="font-semibold tabular-nums">{formatPaise(balancePaise)}</span>
    </p>
  ) : (
    <p className="text-sm text-rose-700">
      You owe <span className="font-semibold tabular-nums">{formatPaise(-balancePaise)}</span>
    </p>
  )
}

// Split: every group you share costs with, and where you stand overall
export default function SplitPage() {
  const navigate = useNavigate()
  const [groups, setGroups] = useState(null)
  const [error, setError] = useState(null)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    let cancelled = false
    listGroups()
      .then((result) => {
        if (!cancelled) setGroups(result)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const owedToYou = groups?.reduce((sum, g) => sum + Math.max(0, g.myBalancePaise), 0) ?? 0
  const youOwe = groups?.reduce((sum, g) => sum + Math.max(0, -g.myBalancePaise), 0) ?? 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Split with friends</h1>
          <p className="mt-1 text-slate-500">
            Share trip and flat costs, see who owes whom, and settle up in as few payments as possible.
          </p>
        </div>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
        >
          New group
        </button>
      </div>

      <ErrorBanner message={error} />
      {!groups && !error && <p className="text-slate-500">Loading...</p>}

      {groups && groups.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm text-slate-500">Friends owe you</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-emerald-700">{formatPaise(owedToYou)}</p>
          </div>
          <div className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
            <p className="text-sm text-slate-500">You owe friends</p>
            <p className="mt-1 text-2xl font-semibold tabular-nums text-rose-700">{formatPaise(youOwe)}</p>
          </div>
        </div>
      )}

      {groups && groups.length === 0 && (
        <div className="rounded-2xl bg-white p-8 text-center text-slate-600 shadow-sm ring-1 ring-slate-200">
          No groups yet. Make one for a trip, your flat, or a dinner, and add the friends you're splitting with.
        </div>
      )}

      {groups && groups.length > 0 && (
        <ul className="grid gap-4 sm:grid-cols-2">
          {groups.map((group) => (
            <li key={group.id}>
              <Link
                to={`/split/${group.id}`}
                className="block rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200 hover:ring-emerald-300"
              >
                <p className="font-semibold text-slate-900">{group.name}</p>
                <p className="mt-0.5 truncate text-sm text-slate-500">{friendsText(group.friendNames)}</p>
                <div className="mt-3 flex items-end justify-between gap-3">
                  <Standing balancePaise={group.myBalancePaise} />
                  <p className="text-xs text-slate-500">{formatPaise(group.totalSpentPaise)} spent</p>
                </div>
              </Link>
            </li>
          ))}
        </ul>
      )}

      {creating && (
        <NewGroupForm onClose={() => setCreating(false)} onCreated={(group) => navigate(`/split/${group.id}`)} />
      )}
    </div>
  )
}
