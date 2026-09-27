// Centered card used by the login and register pages
export default function AuthLayout({ title, subtitle, children, footer }) {
  return (
    <main className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <p className="text-center text-sm font-semibold tracking-wide text-emerald-600">FinLedger</p>

        <div className="mt-3 rounded-2xl bg-white p-8 shadow-sm ring-1 ring-slate-200">
          <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
          {subtitle && <p className="mt-1 text-slate-500">{subtitle}</p>}
          <div className="mt-6">{children}</div>
        </div>

        {footer && <p className="mt-4 text-center text-sm text-slate-600">{footer}</p>}
      </div>
    </main>
  )
}
