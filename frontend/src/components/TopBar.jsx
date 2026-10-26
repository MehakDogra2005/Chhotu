import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AudioLines, ChevronDown, CircleHelp, LogOut, Mic } from 'lucide-react'
import { hideBroken } from './Logo'
import { useAuth } from '../AuthContext'
import { useAssistant } from './AssistantContext'
import NotificationsMenu from './NotificationsMenu'

export default function TopBar({ me }) {
  const { logout } = useAuth()
  const { ask } = useAssistant()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="flex items-center justify-between gap-3 border-b border-edge bg-card px-4 py-3 lg:px-6">
      <div className="flex min-w-0 items-center gap-3">
        <img src="/images/logo.jpg" alt="" className="h-10 mix-blend-multiply lg:h-12" onError={hideBroken} />
        <div className="min-w-0">
          <div className="text-[18px] font-extrabold leading-tight tracking-tight lg:text-[21px]">Dukaan Saathi</div>
          <div className="hidden text-[12.5px] text-muted sm:block">Aapki dukaan ka AI Saathi</div>
        </div>
      </div>

      <div className="hidden text-center lg:block">
        <button onClick={() => ask({ listen: true })} className="flex items-center gap-3 rounded-full border border-[#cfe9d8] bg-panel py-1.5 pl-1.5 pr-5 text-[14px] font-semibold hover:border-brand">
          <span className="grid h-10 w-10 place-items-center rounded-full bg-brand text-white"><Mic size={20} /></span>
          Tap to speak
          <AudioLines size={20} className="text-brand" />
        </button>
        <div className="mt-1 text-[12px] text-muted">Tap the mic, speak, then tap it again to stop</div>
      </div>

      <div className="flex items-center gap-3 sm:gap-5">
        <label className="relative hidden items-center rounded-xl border border-edge bg-white px-4 py-2 text-[14px] font-medium shadow-sm sm:flex">
          <select className="appearance-none bg-transparent pr-6 outline-none">
            <option>Hindi</option>
            <option>English</option>
            <option>Hinglish</option>
          </select>
          <ChevronDown size={15} className="pointer-events-none absolute right-3" />
        </label>

        <NotificationsMenu count={me.alerts} />

        <div className="relative">
          <button onClick={() => setMenuOpen(!menuOpen)} className="flex items-center gap-3 text-left">
            <img src="/images/logo.jpg" alt="" className="h-10 w-10 shrink-0 rounded-full border border-edge bg-white object-cover lg:h-11 lg:w-11" onError={hideBroken} />
            <div className="hidden sm:block">
              <div className="max-w-56 truncate text-[15px] font-bold leading-tight">{me.shopName}</div>
              <div className="text-[13px] text-muted">{me.city || me.userName}</div>
            </div>
            <ChevronDown size={16} />
          </button>
          {menuOpen && (
            <div className="absolute right-0 top-full z-10 mt-2 w-44 overflow-hidden rounded-xl border border-edge bg-white shadow-lg">
              <div className="border-b border-edge px-4 py-2.5 text-[13px] sm:hidden">
                <div className="truncate font-bold">{me.shopName}</div>
                <div className="truncate text-muted">{me.userName}</div>
              </div>
              <Link to="/help" onClick={() => setMenuOpen(false)} className="flex items-center gap-2 px-4 py-3 text-[14px] font-medium hover:bg-stone-50">
                <CircleHelp size={16} /> Help
              </Link>
              <button onClick={logout} className="flex w-full items-center gap-2 px-4 py-3 text-[14px] font-medium hover:bg-stone-50">
                <LogOut size={16} /> Log out
              </button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
