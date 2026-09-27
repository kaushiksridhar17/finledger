import { Suspense } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.js'
import ErrorBoundary from './ErrorBoundary.jsx'
import Logo from './Logo.jsx'
import NotificationBell from './NotificationBell.jsx'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/transactions', label: 'Transactions' },
  { to: '/budgets', label: 'Budgets' },
  { to: '/recurring', label: 'Bills' },
  { to: '/split', label: 'Split' },
  { to: '/investments', label: 'Investments' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/import', label: 'Import' },
]

// Header and navigation shared by every logged-in page. The current page renders in <Outlet />,
// inside an error boundary (a crash shows a message, not a blank page) and Suspense (pages load on demand).
export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {user?.demo && (
        <div data-demo-banner className="border-b border-amber-200 bg-amber-50 px-4 py-2 text-center text-sm text-amber-900">
          This is a demo account with sample data. It's deleted after 24 hours.
        </div>
      )}

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex min-w-0 items-center gap-6">
            <Link to="/" className="shrink-0" aria-label="FinLedger home">
              <Logo />
            </Link>
            <nav className="flex gap-1 overflow-x-auto">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `whitespace-nowrap rounded-md px-2.5 py-1.5 text-sm font-medium ${
                      isActive ? 'bg-emerald-50 text-emerald-700' : 'text-slate-600 hover:bg-slate-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex shrink-0 items-center gap-3">
            <NotificationBell />
            <span className="hidden text-sm text-slate-600 lg:inline">{user?.name}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-700 border border-slate-300 hover:bg-slate-100"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <ErrorBoundary key={location.pathname}>
          <Suspense fallback={<p className="text-slate-500">Loading...</p>}>
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>
    </div>
  )
}
