import { useState } from 'react'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthContext } from './AuthContext'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/login/LoginPage'
import Home from './pages/home/Home'
import Inventory from './pages/inventory/Inventory'
import BusinessAdvisor from './pages/advisor/BusinessAdvisor'
import Customers from './pages/customers/Customers'
import Settings from './pages/settings/Settings'
import Help from './pages/help/Help'
import Conversations from './pages/conversations/Conversations'

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('token'))

  const auth = {
    token,
    login: (newToken) => { localStorage.setItem('token', newToken); setToken(newToken) },
    logout: () => { localStorage.removeItem('token'); setToken(null) },
  }

  return (
    <AuthContext.Provider value={auth}>
      <BrowserRouter>
        {token ? (
          <Routes>
            {/* Every page inside this route gets the same top bar and sidebar */}
            <Route element={<AppLayout />}>
              <Route index element={<Home />} />
              <Route path="inventory" element={<Inventory />} />
              <Route path="advisor" element={<BusinessAdvisor />} />
              <Route path="customers" element={<Customers />} />
              <Route path="settings" element={<Settings />} />
              <Route path="help" element={<Help />} />
              <Route path="conversations" element={<Conversations />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Route>
          </Routes>
        ) : (
          <LoginPage />
        )}
      </BrowserRouter>
    </AuthContext.Provider>
  )
}
