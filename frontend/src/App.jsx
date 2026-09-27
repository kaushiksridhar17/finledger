import { lazy } from 'react'
import { Route, Routes } from 'react-router'
import RequireAuth from './auth/RequireAuth.jsx'
import AppLayout from './components/AppLayout.jsx'
import LoginPage from './pages/LoginPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'

// Each page is its own file in the build and only downloads when it's first opened.
// The chart library comes with the Dashboard and Investments pages, so logging in doesn't wait for it.
const AccountsPage = lazy(() => import('./pages/AccountsPage.jsx'))
const BudgetsPage = lazy(() => import('./pages/BudgetsPage.jsx'))
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'))
const GroupPage = lazy(() => import('./pages/GroupPage.jsx'))
const ImportPage = lazy(() => import('./pages/ImportPage.jsx'))
const InvestmentsPage = lazy(() => import('./pages/InvestmentsPage.jsx'))
const RecurringPage = lazy(() => import('./pages/RecurringPage.jsx'))
const SplitPage = lazy(() => import('./pages/SplitPage.jsx'))
const TransactionsPage = lazy(() => import('./pages/TransactionsPage.jsx'))

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Every page inside here needs login and shares the header and navigation */}
      <Route
        path="/"
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<DashboardPage />} />
        <Route path="transactions" element={<TransactionsPage />} />
        <Route path="budgets" element={<BudgetsPage />} />
        <Route path="recurring" element={<RecurringPage />} />
        <Route path="split" element={<SplitPage />} />
        <Route path="split/:groupId" element={<GroupPage />} />
        <Route path="investments" element={<InvestmentsPage />} />
        <Route path="accounts" element={<AccountsPage />} />
        <Route path="import" element={<ImportPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}

export default App
