import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../auth/useAuth.js'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/transactions', label: 'Transactions' },
  { to: '/accounts', label: 'Accounts' },
  { to: '/import', label: 'Import' },
]

// Header and navigation shared by every logged-in page. The current page renders in <Outlet />.
export default function AppLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="min-h-screen bg-slate-50">
      {user?.demo && (
        <div className="bg-amber-50 px-4 py-2 text-center text-sm text-amber-900 ring-1 ring-amber-200">
          You're exploring a demo account filled with sample data. Change anything you like; it's deleted
          automatically within a day.
        </div>
      )}

      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-6">
            <span className="text-lg font-semibold text-emerald-600">FinLedger</span>
            <nav className="flex gap-1">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-1.5 text-sm font-medium ${
                      isActive ? 'bg-emerald-50 text-emerald-700' : 'text-slate-600 hover:bg-slate-100'
                    }`
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <span className="hidden text-sm text-slate-600 sm:inline">{user?.name}</span>
            <button
              type="button"
              onClick={handleLogout}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-slate-700 ring-1 ring-slate-300 hover:bg-slate-100"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
