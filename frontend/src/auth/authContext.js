import { createContext } from 'react'

// Holds { user, status, login, startDemo, register, logout }. Filled in by AuthProvider, read with useAuth().
export const AuthContext = createContext(null)
