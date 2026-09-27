import { Navigate, Route, Routes } from 'react-router'
import RequireAuth from './auth/RequireAuth.jsx'
import AppLayout from './components/AppLayout.jsx'
import AccountsPage from './pages/AccountsPage.jsx'
import BudgetsPage from './pages/BudgetsPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import GroupPage from './pages/GroupPage.jsx'
import ImportPage from './pages/ImportPage.jsx'
import InvestmentsPage from './pages/InvestmentsPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import RecurringPage from './pages/RecurringPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import SplitPage from './pages/SplitPage.jsx'
import TransactionsPage from './pages/TransactionsPage.jsx'

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
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
