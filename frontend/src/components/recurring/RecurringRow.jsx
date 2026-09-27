import { formatPaise } from '../../lib/money.js'
import { FREQUENCY_LABELS, dueLabel } from '../../lib/recurring.js'

function amountText(item) {
  if (!item.amountVaries) return formatPaise(item.amountPaise)
  return `${formatPaise(item.minAmountPaise)} to ${formatPaise(item.maxAmountPaise)}`
}

// One repeating payment. children are the action buttons for its section.
export default function RecurringRow({ item, children }) {
  const overdue = item.daysUntilDue < 0
  const soon = item.daysUntilDue >= 0 && item.daysUntilDue <= 3

  return (
    <li className="flex flex-wrap items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <p className="flex items-center gap-2 font-medium text-slate-900">
          {item.categoryColor && (
            <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: item.categoryColor }} />
          )}
          {item.name}
        </p>
        <p className="text-xs text-slate-500">
          {FREQUENCY_LABELS[item.frequency]} &middot; {amountText(item)}
          {item.categoryName && <> &middot; {item.categoryName}</>}
          {item.accountName && <> &middot; {item.accountName}</>} &middot; seen {item.occurrences} times
        </p>
      </div>

      <div className="flex items-center gap-3">
        <span
          className={`text-sm ${overdue ? 'text-amber-800' : soon ? 'font-medium text-slate-900' : 'text-slate-600'}`}
        >
          {item.direction === 'IN'
            ? dueLabel(item.daysUntilDue, item.nextDueOn).replace(/^Due/, 'Expected')
            : dueLabel(item.daysUntilDue, item.nextDueOn)}
        </span>
        {children}
      </div>
    </li>
  )
}
