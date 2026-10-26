import { createContext, useContext } from 'react'

// Gives every page the login token and the login / logout functions, so they don't have to be passed down.
export const AuthContext = createContext(null)
export const useAuth = () => useContext(AuthContext)
