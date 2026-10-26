import { NavLink } from 'react-router-dom'
import { hideBroken } from './Logo'
import { HELP, PAGES, SETTINGS } from './navItems'

// The page you are on gets a green box around it.
const linkStyle = (padding) => ({ isActive }) =>
  `flex w-full items-center gap-4 rounded-xl border px-4 text-left text-[16px] ${padding} ${
    isActive ? 'border-brand bg-mint font-semibold text-heading' : 'border-transparent hover:bg-stone-100/70'
  }`

/** The menu on a laptop. On a phone the bottom bar is used instead (see BottomNav). */
export default function Sidebar() {
  return (
    // The scrollbar is hidden: on a short window you can still scroll the menu, but only the page shows a bar.
    <aside className="hidden w-70 shrink-0 flex-col overflow-y-auto border-r border-edge bg-card/60 p-3 [scrollbar-width:none] lg:flex [&::-webkit-scrollbar]:hidden">
      <nav className="space-y-1">
        {PAGES.map(({ to, icon: Icon, title, subtitle }) => (
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
        {[SETTINGS, HELP].map(({ to, icon: Icon, title }) => (
          <NavLink key={to} to={to} className={linkStyle('py-3.5')}>
            {({ isActive }) => (
              <>
                <Icon size={26} strokeWidth={1.8} className={isActive ? 'text-brand' : 'text-[#2b4a3d]'} /> {title}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* On a short window there is no room for the card, so it is left out */}
      <div className="overflow-hidden rounded-2xl border border-edge bg-warm/60 pt-2 [@media(max-height:780px)]:hidden">
        <img src="/images/illustration.jpg" alt="" className="mx-auto h-28 mix-blend-multiply" onError={hideBroken} />
        <div className="mx-2 mb-2 rounded-xl bg-white/90 p-4 text-[14px]">
          <div className="font-bold">Boliye, hum sun rahe hain!</div>
          <div className="mt-2 text-muted">Dukaan Saathi is always listening to help you.</div>
        </div>
      </div>
    </aside>
  )
}
