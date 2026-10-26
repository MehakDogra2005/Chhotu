import { NavLink } from 'react-router-dom'
import { Box, ChartNoAxesColumnIncreasing, CircleHelp, House, Settings, Users } from 'lucide-react'
import { hideBroken } from './Logo'

// [address, icon, title, second line]
const PAGES = [
  ['/', House, 'Home', null],
  ['/inventory', Box, 'Inventory', 'Management'],
  ['/advisor', ChartNoAxesColumnIncreasing, 'Business Advisor', '(Profit & Loss)'],
  ['/customers', Users, 'Customer Handling', '(Udhaar Management)'],
]
const OTHER = [
  ['/settings', Settings, 'Settings'],
  ['/help', CircleHelp, 'Help'],
]

// The page you are on gets a green box around it.
const linkStyle = (padding) => ({ isActive }) =>
  `flex w-full items-center gap-4 rounded-xl border px-4 text-left text-[16px] ${padding} ${
    isActive ? 'border-brand bg-mint font-semibold text-heading' : 'border-transparent hover:bg-stone-100/70'
  }`

export default function Sidebar() {
  return (
    <aside className="hidden w-70 shrink-0 flex-col overflow-y-auto border-r border-edge bg-card/60 p-3 lg:flex">
      <nav className="space-y-1">
        {PAGES.map(([to, Icon, title, subtitle]) => (
          <NavLink key={to} to={to} end={to === '/'} className={linkStyle('py-4')}>
            {({ isActive }) => (
              <>
                <Icon size={26} strokeWidth={1.8} className={isActive ? 'text-brand' : 'text-[#2b4a3d]'} />
                <span className="leading-snug">{title}{subtitle && <><br />{subtitle}</>}</span>
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* Settings and Help sit at the bottom, just above the shop card */}
      <nav className="mt-auto space-y-1 pb-4">
        {OTHER.map(([to, Icon, title]) => (
          <NavLink key={to} to={to} className={linkStyle('py-3.5')}>
            {({ isActive }) => (
              <>
                <Icon size={26} strokeWidth={1.8} className={isActive ? 'text-brand' : 'text-[#2b4a3d]'} /> {title}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      <div className="overflow-hidden rounded-2xl border border-edge bg-warm/60 pt-2">
        <img src="/images/illustration.jpg" alt="" className="mx-auto h-28 mix-blend-multiply" onError={hideBroken} />
        <div className="mx-2 mb-2 rounded-xl bg-white/90 p-4 text-[14px]">
          <div className="font-bold">Boliye, hum sun rahe hain!</div>
          <div className="mt-2 text-muted">Dukaan Saathi is always listening to help you.</div>
        </div>
      </div>
    </aside>
  )
}
