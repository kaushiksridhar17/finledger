import { acceptMatch, dismissMatch } from '../../api/groups.js'
import { formatDate } from '../../lib/dates.js'
import { formatPaise } from '../../lib/money.js'

// "Kabir may have paid you back": money in your bank that matches what a friend owes you
export default function SuggestionBanner({ group, onChanged, onError }) {
  if (group.suggestions.length === 0) return null

  async function act(action, id) {
    try {
      onChanged(await action(group.id, id))
    } catch (err) {
      onError(err.message)
    }
  }

  return (
    <section className="space-y-2" aria-label="Possible repayments">
      {group.suggestions.map((s) => (
        <div key={s.id} className="flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-sky-50 px-4 py-3 ring-1 ring-sky-200">
          <div className="min-w-0">
            <p className="font-medium text-slate-900">
              {s.memberName} may have paid you back {formatPaise(s.amountPaise)}
            </p>
            <p className="truncate text-sm text-slate-600">
              Came into your account on {formatDate(s.date)}: <span className="font-mono text-xs">{s.description}</span>
            </p>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => act(acceptMatch, s.id)}
              className="rounded-lg bg-emerald-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-emerald-700"
            >
              Yes, record it
            </button>
            <button
              type="button"
              onClick={() => act(dismissMatch, s.id)}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-600 ring-1 ring-slate-300 hover:bg-white"
            >
              Not this
            </button>
          </div>
        </div>
      ))}
    </section>
  )
}
