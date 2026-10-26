import { useState } from 'react'
import { AudioLines, Bell, ChevronDown, LogOut, Mic } from 'lucide-react'
import { hideBroken } from './Logo'
import { useAuth } from '../AuthContext'

export default function TopBar({ me }) {
  const { logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <header className="flex items-center justify-between border-b border-edge bg-card px-6 py-3">
      <div className="flex items-center gap-3">
        <img src="/images/logo.jpg" alt="" className="h-12 mix-blend-multiply" onError={hideBroken} />
        <div>
          <div className="text-[21px] font-extrabold leading-tight tracking-tight">Dukaan Saathi</div>
          <div className="text-[12.5px] text-muted">Aapki dukaan ka AI Saathi</div>
        </div>
      </div>

      {/* The voice assistant itself is not built yet; this is the place it will live. */}
      <div className="hidden text-center lg:block">
        <div className="flex items-center gap-3 rounded-full border border-[#cfe9d8] bg-panel py-1.5 pl-1.5 pr-5 text-[14px] font-semibold">
          <span className="grid h-10 w-10 place-items-center rounded-full bg-brand text-white"><Mic size={20} /></span>
          Mic Locked <span className="text-muted">•</span> <span className="font-medium text-brand">Always Listening</span>
          <AudioLines size={20} className="text-brand" />
        </div>
        <div className="mt-1 text-[12px] text-muted">Speak naturally anytime...</div>
      </div>

      <div className="flex items-center gap-5">
        <label className="relative hidden items-center rounded-xl border border-edge bg-white px-4 py-2 text-[14px] font-medium shadow-sm sm:flex">
          <select className="appearance-none bg-transparent pr-6 outline-none">
            <option>Hindi</option>
            <option>English</option>
            <option>Hinglish</option>
          </select>
          <ChevronDown size={15} className="pointer-events-none absolute right-3" />
        </label>

        <div className="relative">
          <Bell size={26} />
          {me.alerts > 0 && (
            <span className="absolute -right-1.5 -top-1.5 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">{me.alerts}</span>
          )}
        </div>

        <div className="relative">
          <button onClick={() => setMenuOpen(!menuOpen)} className="flex items-center gap-3 text-left">
            <img src="/images/logo.jpg" alt="" className="h-11 w-11 rounded-full border border-edge bg-white object-cover" onError={hideBroken} />
            <div className="hidden sm:block">
              <div className="max-w-56 truncate text-[15px] font-bold leading-tight">{me.shopName}</div>
              <div className="text-[13px] text-muted">{me.city || me.userName}</div>
            </div>
            <ChevronDown size={16} />
          </button>
          {menuOpen && (
            <button onClick={logout} className="absolute right-0 top-full z-10 mt-2 flex items-center gap-2 whitespace-nowrap rounded-xl border border-edge bg-white px-4 py-2.5 text-[14px] font-medium shadow-lg hover:bg-stone-50">
              <LogOut size={16} /> Log out
            </button>
          )}
        </div>
      </div>
    </header>
  )
}
