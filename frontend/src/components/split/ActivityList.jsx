import { deleteSettlement } from '../../api/groups.js'
import { MONTH_NAMES } from '../../lib/months.js'
import { formatPaise } from '../../lib/money.js'

const SPLIT_LABELS = { EQUAL: 'split equally', EXACT: 'exact amounts', PERCENT: 'by percentage', SHARES: 'by shares' }

function DateChip({ isoDate }) {
  const [, month, day] = isoDate.split('-').map(Number)
  return (
    <div className="w-11 shrink-0 text-center leading-tight">
      <p className="text-xs uppercase text-slate-500">{MONTH_NAMES[month - 1].slice(0, 3)}</p>
      <p className="text-lg font-semibold text-slate-900">{day}</p>
    </div>
  )
}

// What this expense means for you: lent, borrowed or not involved
function YourPart({ expense, selfId }) {
  const myShare = expense.shares.find((s) => s.memberId === selfId)?.sharePaise ?? 0
  if (expense.paidByMemberId === selfId) {
    const lent = expense.amountPaise - myShare
    if (lent === 0) return <span className="text-sm text-slate-500">Just you</span>
    return (
      <span className="text-right text-sm text-emerald-700">
        <span className="block text-xs">You lent</span>
        <span className="font-semibold tabular-nums">{formatPaise(lent)}</span>
      </span>
    )
  }
  if (myShare === 0) return <span className="text-sm text-slate-500">Not involved</span>
  return (
    <span className="text-right text-sm text-rose-700">
      <span className="block text-xs">You borrowed</span>
      <span className="font-semibold tabular-nums">{formatPaise(myShare)}</span>
    </span>
  )
}

// Expenses and repayments together, newest first
export default function ActivityList({ group, onEditExpense, onChanged, onError }) {
  const self = group.members.find((m) => m.self)
  const nameOf = (id, name) => (id === self?.id ? 'You' : name)

  const items = [
    ...group.expenses.map((e) => ({ kind: 'expense', key: `e${e.id}`, date: e.date, item: e })),
    ...group.settlements.map((s) => ({ kind: 'payment', key: `p${s.id}`, date: s.date, item: s })),
  ].sort((a, b) => b.date.localeCompare(a.date) || b.item.id - a.item.id)

  async function undo(payment) {
    if (!window.confirm('Remove this payment? The amount will be owed again.')) return
    try {
      onChanged(await deleteSettlement(group.id, payment.id))
    } catch (err) {
      onError(err.message)
    }
  }

  if (items.length === 0) {
    return (
      <div className="rounded-lg bg-white p-8 text-center text-slate-600 border border-slate-200">
        No expenses yet.
      </div>
    )
  }

  return (
    <ul className="divide-y divide-slate-100 overflow-hidden rounded-lg bg-white border border-slate-200">
      {items.map(({ kind, key, date, item }) =>
        kind === 'expense' ? (
          <li key={key}>
            <button
              type="button"
              onClick={() => onEditExpense(item)}
              className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-slate-50"
            >
              <DateChip isoDate={date} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-medium text-slate-900">{item.description}</p>
                <p className="text-sm text-slate-500">
                  {nameOf(item.paidByMemberId, item.paidByName)} paid {formatPaise(item.amountPaise)} &middot;{' '}
                  {SPLIT_LABELS[item.splitType]}
                </p>
              </div>
              <YourPart expense={item} selfId={self?.id} />
            </button>
          </li>
        ) : (
          <li key={key} className="flex items-center gap-3 bg-slate-50/60 px-4 py-3">
            <DateChip isoDate={date} />
            <div className="min-w-0 flex-1">
              <p className="text-sm text-slate-800">
                <span className="font-medium">{nameOf(item.fromMemberId, item.fromName)}</span> paid{' '}
                <span className="font-medium">{item.toMemberId === self?.id ? 'you' : item.toName}</span>{' '}
                <span className="font-semibold tabular-nums">{formatPaise(item.amountPaise)}</span>
              </p>
              <p className="text-xs text-slate-500">
                {item.method === 'MATCHED' ? 'Matched to a bank transaction' : item.note || 'Recorded by you'}
              </p>
            </div>
            <button type="button" onClick={() => undo(item)} className="text-sm font-medium text-slate-500 hover:text-rose-700">
              Undo
            </button>
          </li>
        ),
      )}
    </ul>
  )
}
