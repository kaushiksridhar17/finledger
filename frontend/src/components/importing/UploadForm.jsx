import { useState } from 'react'
import { uploadStatement } from '../../api/imports.js'
import ErrorBanner from '../ErrorBanner.jsx'

const MAX_BYTES = 2 * 1024 * 1024

export default function UploadForm({ accounts, onUploaded }) {
  const activeAccounts = accounts.filter((a) => !a.archived)

  const [accountId, setAccountId] = useState('')
  const [file, setFile] = useState(null)
  const [inputKey, setInputKey] = useState(0)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  // Default to the first account once the list has loaded
  const selectedAccount = accountId || (activeAccounts[0] ? String(activeAccounts[0].id) : '')

  function handleFile(event) {
    const chosen = event.target.files?.[0] ?? null
    setError(null)
    if (chosen && chosen.size > MAX_BYTES) {
      setError('That file is larger than 2 MB. Try a shorter date range.')
      setFile(null)
      return
    }
    setFile(chosen)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!file) {
      setError('Choose a CSV file first')
      return
    }

    setBusy(true)
    setError(null)
    try {
      await uploadStatement(selectedAccount, file)
      setFile(null)
      setInputKey((k) => k + 1) // clears the file picker
      onUploaded()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  if (activeAccounts.length === 0) {
    return <p className="text-slate-600">Add an account on the Accounts page first, then import its statement here.</p>
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <ErrorBanner message={error} />

      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block text-sm font-medium text-slate-700">
          Import into
          <select
            value={selectedAccount}
            onChange={(e) => setAccountId(e.target.value)}
            className="mt-1 block w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-200"
          >
            {activeAccounts.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </label>

        <label className="block text-sm font-medium text-slate-700">
          Statement file (.csv)
          <input
            key={inputKey}
            type="file"
            accept=".csv,text/csv"
            onChange={handleFile}
            className="mt-1 block w-full text-sm text-slate-700 file:mr-3 file:rounded-lg file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm file:font-medium file:text-slate-700 hover:file:bg-slate-200"
          />
        </label>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-xs text-slate-500">
          Reads HDFC, ICICI and SBI statement CSVs, or{' '}
          <a href="/sample-statement.csv" download className="font-medium text-emerald-600 hover:text-emerald-700">
            our simple template
          </a>{' '}
          (Date, Description, Amount). Rows already imported are skipped automatically.
        </p>
        <button
          type="submit"
          disabled={busy || !file}
          className="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-50"
        >
          {busy ? 'Uploading...' : 'Import'}
        </button>
      </div>
    </form>
  )
}
