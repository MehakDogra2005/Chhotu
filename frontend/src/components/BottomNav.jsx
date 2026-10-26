import { NavLink } from 'react-router-dom'
import { PAGES, SETTINGS } from './navItems'

/** The menu on a phone: five big buttons along the bottom, with a green box around the page you are on. */
export default function BottomNav() {
  return (
    <nav className="flex border-t border-edge bg-card p-1 lg:hidden">
      {[...PAGES, SETTINGS].map(({ to, icon: Icon, short }) => (
        <NavLink key={to} to={to} end={to === '/'}
          className={({ isActive }) =>
            `flex flex-1 flex-col items-center gap-1 rounded-xl border py-2 text-[11px] ${
              isActive ? 'border-brand bg-mint font-semibold text-heading' : 'border-transparent text-muted'
            }`}>
          {({ isActive }) => (
            <>
              <Icon size={22} strokeWidth={1.8} className={isActive ? 'text-brand' : ''} />
              {short}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )
}
