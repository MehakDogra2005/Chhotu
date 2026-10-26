import { Outlet } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import useFetch from '../useFetch'
import TopBar from './TopBar'
import Sidebar from './Sidebar'

/** The frame every logged-in page sits in: top bar, sidebar, and the page itself in the middle. */
export default function AppLayout() {
  const { logout } = useAuth()
  const { data: me, error, reload } = useFetch('/api/me')

  if (error) return <p className="p-10 text-center text-red-600">{error} <button onClick={logout} className="font-bold underline">Log out</button></p>
  if (!me) return <p className="p-10 text-center text-muted">Loading your shop...</p>

  return (
    // The window is split into a top bar and a body. The sidebar stays in place; only the page itself scrolls.
    <div className="flex h-screen flex-col bg-cream">
      <TopBar me={me} />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <main className="min-w-0 flex-1 overflow-y-auto p-6">
          {/* Every page can read `me` (name, shop) and call reloadMe() after changing it, via useOutletContext() */}
          <Outlet context={{ me, reloadMe: reload }} />
        </main>
      </div>
    </div>
  )
}
