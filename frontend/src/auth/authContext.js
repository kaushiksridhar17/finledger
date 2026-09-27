import { createContext } from 'react'

// Holds { user, status, login, register, logout }. Filled in by AuthProvider, read with useAuth().
export const AuthContext = createContext(null)
