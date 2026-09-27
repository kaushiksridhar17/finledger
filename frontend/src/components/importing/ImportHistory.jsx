import { useState } from 'react'
import { Link } from 'react-router'
import { getImport } from '../../api/imports.js'

const STATUS = {
  QUEUED: { label: 'Waiting', className: 'bg-slate-100 text-slate-700' },
  PROCESSING: { label: 'Importing...', className: 'bg-sky-50 text-sky-800 ring-1 ring-sky-200' },
  COMPLETED: { label: 'Done', className: 'bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200' },
  FAILED: { label: 'Failed', className: 'bg-rose-50 text-rose-800 ring-1 ring-rose-200' },
}

const FORMAT_NAMES = { HDFC: 'HDFC', ICICI: 'ICICI', SBI: 'SBI', TEMPLATE: 'FinLedger template' }

function formatTime(iso) {
  return new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
}

export default function ImportHistory({ imports }) {
  if (imports.length === 0) {
    return <p className="text-slate-500">No imports yet. Upload a statement above.</p>
  }

  return (
    <ul className="divide-y divide-slate-100">
      {imports.map((item) => (
        <ImportRow key={item.id} item={item} />
      ))}
    </ul>
  )
}

function ImportRow({ item }) {
  const [details, setDetails] = useState(null)
  const [open, setOpen] = useState(false)
  const status = STATUS[item.status]

  async function toggleProblems() {
    if (!open && !details) {
      try {
        setDetails(await getImport(item.id))
      } catch {
        setDetails({ rowErrors: [] })
      }
    }
    setOpen((o) => !o)
  }

  return (
    <li className="py-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-medium text-slate-900">{item.fileName}</p>
          <p className="text-xs text-slate-500">
            {item.accountName} &middot; {formatTime(item.createdAt)}
            {item.bankFormat && <> &middot; {FORMAT_NAMES[item.bankFormat] ?? item.bankFormat} format</>}
          </p>
        </div>
        <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${status.className}`}>{status.label}</span>
      </div>

      {item.status === 'COMPLETED' && (
        <p className="mt-1 text-sm text-slate-600">
          <span className="font-medium text-slate-900">{item.rowsImported} imported</span>
          {item.rowsDuplicate > 0 && <> &middot; {item.rowsDuplicate} already imported, skipped</>}
          {item.rowsFailed > 0 && (
            <>
              {' '}
              &middot;{' '}
              <button type="button" onClick={toggleProblems} className="font-medium text-rose-700 underline">
                {item.rowsFailed} {item.rowsFailed === 1 ? 'line' : 'lines'} couldn't be read
              </button>
            </>
          )}
          {item.rowsImported > 0 && (
            <>
              {' '}
              &middot;{' '}
              <Link to="/transactions" className="font-medium text-emerald-600 hover:text-emerald-700">
                View transactions
              </Link>
            </>
          )}
        </p>
      )}

      {item.status === 'FAILED' && <p className="mt-1 text-sm text-rose-700">{item.errorMessage}</p>}

      {open && details && (
        <ul className="mt-2 space-y-1 rounded-lg bg-rose-50 p-3 text-sm text-rose-900">
          {details.rowErrors.map((e) => (
            <li key={e.lineNumber}>
              <span className="font-medium">Line {e.lineNumber}:</span> {e.message}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
