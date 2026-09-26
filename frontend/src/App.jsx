import { useEffect, useState } from 'react'

function StatusRow({ label, value, ok }) {
  let valueColor = 'text-slate-900'
  if (ok === true) valueColor = 'text-emerald-600'
  if (ok === false) valueColor = 'text-rose-600'

  return (
    <div className="flex items-center justify-between py-2 border-b border-slate-100 last:border-b-0">
      <span className="text-slate-600">{label}</span>
      <span className={`font-medium ${valueColor}`}>{value}</span>
    </div>
  )
}

function App() {
  const [status, setStatus] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/api/status')
      .then((res) => {
        if (!res.ok) throw new Error(`Backend returned ${res.status}`)
        return res.json()
      })
      .then((data) => setStatus(data))
      .catch(() => setError('Could not reach the backend. Is Spring Boot running?'))
  }, [])

  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="max-w-md w-full rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200">
        <h1 className="text-2xl font-semibold text-slate-900">FinLedger</h1>
        <p className="mt-1 text-slate-500">System status</p>

        <div className="mt-6">
          {error && <p className="text-rose-600">{error}</p>}

          {!error && !status && <p className="text-slate-500">Checking...</p>}

          {status && (
            <div>
              <StatusRow label="Frontend" value="UP" ok={true} />
              <StatusRow label="API" value={status.api} ok={status.api === 'UP'} />
              <StatusRow label="Database" value={status.database} ok={status.database === 'UP'} />
              <StatusRow label="MySQL version" value={status.databaseVersion ?? 'Unknown'} />
              <StatusRow
                label="Server time"
                value={new Date(status.serverTime).toLocaleTimeString()}
              />
            </div>
          )}
        </div>
      </div>
    </main>
  )
}

export default App