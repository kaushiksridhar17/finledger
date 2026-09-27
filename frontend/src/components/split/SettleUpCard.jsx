import { useState } from 'react'
import { addSettlement } from '../../api/groups.js'
import { todayIso } from '../../lib/dates.js'
import { formatPaise } from '../../lib/money.js'
import UpiModal from './UpiModal.jsx'

const linkButton = 'text-sm font-medium text-emerald-700 hover:text-emerald-800'

// The fewest payments that settle the whole group, each with a way to pay or ask over UPI
export default function SettleUpCard({ group, onChanged, onEditMember, onError }) {
  const [upiFor, setUpiFor] = useState(null)
  const byId = new Map(group.members.map((m) => [m.id, m]))
  const self = group.members.find((m) => m.self)
  const nameOf = (id) => (byId.get(id)?.self ? 'You' : byId.get(id)?.name)

  async function markPaid(transfer) {
    const from = nameOf(transfer.fromMemberId)
    const to = byId.get(transfer.toMemberId)?.self ? 'you' : nameOf(transfer.toMemberId)
    if (!window.confirm(`Record that ${from} paid ${to} ${formatPaise(transfer.amountPaise)}?`)) return
    setUpiFor(null)
    try {
      onChanged(
        await addSettlement(group.id, {
          fromMemberId: transfer.fromMemberId,
          toMemberId: transfer.toMemberId,
          amountPaise: transfer.amountPaise,
          date: todayIso(),
          note: 'Settled up',
        }),
      )
    } catch (err) {
      onError(err.message)
    }
  }

  return (
    <section className="rounded-2xl bg-white p-5 shadow-sm ring-1 ring-slate-200">
      <h2 className="font-semibold text-slate-900">Settle up</h2>
      {group.settleUp.length === 0 ? (
        <p className="mt-2 text-sm text-slate-600">Everyone's settled up.</p>
      ) : (
        <>
          <p className="text-xs text-slate-500">
            {group.settleUp.length === 1 ? 'One payment' : `${group.settleUp.length} payments`} settle everything.
          </p>
          <ul className="mt-3 space-y-3">
            {group.settleUp.map((t) => {
              const payer = byId.get(t.fromMemberId)
              const payee = byId.get(t.toMemberId)
              const youPay = payer?.self
              const youGet = payee?.self
              return (
                <li key={`${t.fromMemberId}-${t.toMemberId}`} className="rounded-xl bg-slate-50 px-3 py-2.5">
                  <p className="text-sm text-slate-800">
                    <span className="font-medium">{nameOf(t.fromMemberId)}</span> {youPay ? 'pay' : 'pays'}{' '}
                    <span className="font-medium">{youGet ? 'you' : nameOf(t.toMemberId)}</span>
                  </p>
                  <p className="text-lg font-semibold tabular-nums text-slate-900">{formatPaise(t.amountPaise)}</p>
                  <div className="mt-1 flex flex-wrap gap-x-4 gap-y-1">
                    {youPay && payee?.upiId && (
                      <button type="button" onClick={() => setUpiFor(t)} className={linkButton}>
                        Pay with UPI
                      </button>
                    )}
                    {youGet && self?.upiId && (
                      <button type="button" onClick={() => setUpiFor(t)} className={linkButton}>
                        Ask with UPI QR
                      </button>
                    )}
                    {youGet && !self?.upiId && (
                      <button type="button" onClick={() => onEditMember(self)} className={linkButton}>
                        Add your UPI ID to ask
                      </button>
                    )}
                    <button type="button" onClick={() => markPaid(t)} className="text-sm font-medium text-slate-600 hover:text-slate-900">
                      Mark as paid
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        </>
      )}

      {upiFor && (
        <UpiModal
          title={byId.get(upiFor.fromMemberId)?.self ? `Pay ${nameOf(upiFor.toMemberId)}` : `Ask ${nameOf(upiFor.fromMemberId)} to pay you`}
          payee={byId.get(upiFor.toMemberId)}
          amountPaise={upiFor.amountPaise}
          note={group.name}
          instructions={
            byId.get(upiFor.fromMemberId)?.self
              ? 'Scan with GPay, PhonePe, Paytm or any UPI app, or open this page on your phone and tap the button.'
              : `Show this to ${nameOf(upiFor.fromMemberId)}, or copy the link and send it to them.`
          }
          onMarkPaid={() => markPaid(upiFor)}
          onClose={() => setUpiFor(null)}
        />
      )}
    </section>
  )
}
