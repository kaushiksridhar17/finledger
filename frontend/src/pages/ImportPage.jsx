import { useEffect, useState } from 'react'
import { listImports } from '../api/imports.js'
import { listAccounts, listCategories } from '../api/ledger.js'
import ErrorBanner from '../components/ErrorBanner.jsx'
import ImportHistory from '../components/importing/ImportHistory.jsx'
import RulesSection from '../components/importing/RulesSection.jsx'
import UploadForm from '../components/importing/UploadForm.jsx'

const POLL_MS = 1500

export default function ImportPage() {
  const [accounts, setAccounts] = useState(null)
  const [categories, setCategories] = useState([])
  const [imports, setImports] = useState(null)
  const [error, setError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    Promise.all([listAccounts(), listCategories()])
      .then(([accountList, categoryList]) => {
        if (cancelled) return
        setAccounts(accountList)
        setCategories(categoryList)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    listImports()
      .then((data) => {
        if (!cancelled) setImports(data)
      })
      .catch((err) => {
        if (!cancelled) setError(err.message)
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  // Imports run in the background, so check again every 1.5 seconds while any are still going
  const anyRunning = imports?.some((i) => i.status === 'QUEUED' || i.status === 'PROCESSING') ?? false
  useEffect(() => {
    if (!anyRunning) return undefined
    const timer = setInterval(() => setReloadKey((k) => k + 1), POLL_MS)
    return () => clearInterval(timer)
  }, [anyRunning])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">Import a bank statement</h1>
        <p className="mt-1 text-slate-500">
          Download a statement as CSV from your bank's website and upload it here. Transactions are added and sorted
          into categories automatically.
        </p>
      </div>

      <ErrorBanner message={error} />

      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        {accounts ? (
          <UploadForm accounts={accounts} onUploaded={() => setReloadKey((k) => k + 1)} />
        ) : (
          <p className="text-slate-500">Loading...</p>
        )}
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-lg font-semibold text-slate-900">Recent imports</h2>
        {imports ? <ImportHistory imports={imports} /> : <p className="text-slate-500">Loading...</p>}
      </section>

      <section className="rounded-2xl bg-white p-6 shadow-sm ring-1 ring-slate-200">
        <h2 className="mb-2 text-lg font-semibold text-slate-900">Categorisation rules</h2>
        <RulesSection categories={categories} />
      </section>
    </div>
  )
}
