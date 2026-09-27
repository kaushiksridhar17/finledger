import { removeMember } from '../../api/groups.js'
import { formatPaise } from '../../lib/money.js'

// Everyone in the group with where they stand. Balance is always said in words, not just colour.
export default function PeopleCard({ group, onChanged, onEdit, onAdd, onError }) {
  async function remove(member) {
    if (!window.confirm(`Remove ${member.name} from ${group.name}?`)) return
    try {
      onChanged(await removeMember(group.id, member.id))
    } catch (err) {
      onError(err.message)
    }
  }

  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <div className="flex items-center justify-between">
        <h2 className="font-semibold text-slate-900">People</h2>
        <button type="button" onClick={onAdd} className="text-sm font-medium text-emerald-700 hover:text-emerald-800">
          + Add
        </button>
      </div>
      <ul className="mt-2 divide-y divide-slate-100">
        {group.members.map((m) => (
          <li key={m.id} className="flex items-start justify-between gap-3 py-2.5">
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-slate-900">{m.self ? `You (${m.name})` : m.name}</p>
              <p className="truncate font-mono text-xs text-slate-500">{m.upiId ?? 'No UPI ID'}</p>
              <div className="mt-0.5 flex gap-3 text-xs">
                <button type="button" onClick={() => onEdit(m)} className="font-medium text-emerald-700 hover:text-emerald-800">
                  Edit
                </button>
                {m.removable && (
                  <button type="button" onClick={() => remove(m)} className="font-medium text-rose-600 hover:text-rose-700">
                    Remove
                  </button>
                )}
              </div>
            </div>
            <BalanceText balancePaise={m.balancePaise} self={m.self} />
          </li>
        ))}
      </ul>
    </section>
  )
}

export function BalanceText({ balancePaise, self }) {
  if (balancePaise === 0) return <span className="shrink-0 text-sm text-slate-500">Settled</span>
  const owed = balancePaise > 0
  const label = owed ? (self ? 'You get back' : 'Gets back') : self ? 'You owe' : 'Owes'
  return (
    <span className={`shrink-0 text-right text-sm ${owed ? 'text-emerald-700' : 'text-rose-700'}`}>
      <span className="block text-xs">{label}</span>
      <span className="font-semibold tabular-nums">{formatPaise(Math.abs(balancePaise))}</span>
    </span>
  )
}
