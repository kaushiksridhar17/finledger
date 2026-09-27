import { formatPaise } from '../../lib/money.js'

const BAR_COLOR = '#2a78d6'
const MAX_ROWS = 7

// Where the month's money went: a ranked list with a bar for each category.
// Past 7 rows, the smaller categories are folded into "Everything else" so the list stays readable.
export default function CategoryBreakdown({ categories, totalPaise }) {
  if (categories.length === 0) {
    return <p className="text-slate-500">No spending recorded this month.</p>
  }

  const rows =
    categories.length > MAX_ROWS
      ? [
          ...categories.slice(0, MAX_ROWS - 1),
          {
            categoryId: 'rest',
            name: 'Everything else',
            color: '#94a3b8',
            amountPaise: categories.slice(MAX_ROWS - 1).reduce((sum, c) => sum + c.amountPaise, 0),
          },
        ]
      : categories

  const largest = rows[0].amountPaise

  return (
    <ul className="space-y-3">
      {rows.map((c) => {
        const share = totalPaise > 0 ? Math.round((c.amountPaise / totalPaise) * 100) : 0
        return (
          <li key={c.categoryId ?? 'none'}>
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="flex items-center gap-2 text-slate-700">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: c.color }} />
                {c.name}
              </span>
              <span className="tabular-nums text-slate-900">
                {formatPaise(c.amountPaise)} <span className="text-slate-400">&middot; {share}%</span>
              </span>
            </div>
            <div className="mt-1 h-1.5 rounded-full bg-slate-100">
              <div
                className="h-1.5 rounded-full"
                style={{ width: `${Math.max((c.amountPaise / largest) * 100, 1)}%`, backgroundColor: BAR_COLOR }}
              />
            </div>
          </li>
        )
      })}
    </ul>
  )
}
