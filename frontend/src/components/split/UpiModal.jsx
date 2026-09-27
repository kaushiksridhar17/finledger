import { QRCodeSVG } from 'qrcode.react'
import { useState } from 'react'
import { formatPaise } from '../../lib/money.js'
import { upiPayLink } from '../../lib/upi.js'
import Modal from '../Modal.jsx'

// A UPI payment request as a QR code and a link.
// payee is who receives the money ({ name, upiId }). When you're paying a friend, scan it with your phone
// (or tap the link on your phone). When a friend is paying you, show them the code.
export default function UpiModal({ title, payee, amountPaise, note, instructions, onMarkPaid, onClose }) {
  const [copied, setCopied] = useState(false)
  const link = upiPayLink({ upiId: payee.upiId, name: payee.name, amountPaise, note })

  async function copy() {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
    } catch {
      setCopied(false)
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <div className="flex flex-col items-center text-center">
        <p className="text-3xl font-semibold tabular-nums text-slate-900">{formatPaise(amountPaise)}</p>
        <p className="mt-1 text-sm text-slate-600">
          to {payee.name} &middot; <span className="font-mono">{payee.upiId}</span>
        </p>

        <div className="mt-4 rounded-xl bg-white p-3 ring-1 ring-slate-200">
          <QRCodeSVG value={link} size={176} marginSize={1} title={`UPI QR code for ${formatPaise(amountPaise)}`} />
        </div>
        <p className="mt-3 max-w-sm text-sm text-slate-600">{instructions}</p>

        <div className="mt-4 flex flex-wrap justify-center gap-2">
          <a
            href={link}
            className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700"
          >
            Open UPI app
          </a>
          <button
            type="button"
            onClick={copy}
            className="rounded-lg px-4 py-2 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-100"
          >
            {copied ? 'Link copied' : 'Copy link'}
          </button>
        </div>

        {onMarkPaid && (
          <button
            type="button"
            onClick={onMarkPaid}
            className="mt-5 text-sm font-medium text-emerald-700 hover:text-emerald-800"
          >
            Done? Mark it as paid
          </button>
        )}
      </div>
    </Modal>
  )
}
