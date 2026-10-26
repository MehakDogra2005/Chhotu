import { Outlet } from 'react-router-dom'
import { useAuth } from '../AuthContext'
import useFetch from '../useFetch'
import TopBar from './TopBar'
import Sidebar from './Sidebar'
import BottomNav from './BottomNav'
import { AssistantProvider } from './AssistantContext'

/** The frame every logged-in page sits in: top bar, menu, and the page itself in the middle. */
export default function AppLayout() {
  const { logout } = useAuth()
  const { data: me, error, reload } = useFetch('/api/me')

  if (error) return <p className="p-10 text-center text-red-600">{error} <button onClick={logout} className="font-bold underline">Log out</button></p>
  if (!me) return <p className="p-10 text-center text-muted">Loading your shop...</p>

  return (
    // The window is split into top bar, body and (on a phone) bottom bar. Only the page itself scrolls.
    <AssistantProvider speakReplies={me.voiceReplies}>
      <div className="flex h-dvh flex-col bg-cream">
        <TopBar me={me} />
        <div className="flex min-h-0 flex-1">
          <Sidebar />
          <main className="min-w-0 flex-1 overflow-y-auto p-4 lg:p-6">
            {/* Every page can read `me` (name, shop) and call reloadMe() after changing it, via useOutletContext() */}
            <Outlet context={{ me, reloadMe: reload }} />
          </main>
        </div>
        <BottomNav />
      </div>
    </AssistantProvider>
  )
}
