import { Navigate, useLocation } from 'react-router'
import { useAuth } from './useAuth.js'
import FullPageMessage from '../components/FullPageMessage.jsx'

// Wrap any page that needs login. Logged-out users go to /login and come back here afterwards.
export default function RequireAuth({ children }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <FullPageMessage text="Loading..." />
  }

  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  return children
}
