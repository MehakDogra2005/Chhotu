import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Bell, CircleDollarSign, PackageX } from 'lucide-react'
import useFetch from '../useFetch'

const ICONS = { LOW_STOCK: [PackageX, 'bg-[#fde8e5] text-red-500'], OVERDUE: [CircleDollarSign, 'bg-[#fdebd0] text-orange'] }

/** The bell. Opens a list of what needs attention; tapping an item goes to the page about it. */
export default function NotificationsMenu({ count }) {
  const [open, setOpen] = useState(false)

  return (
    <div className="relative">
      <button onClick={() => setOpen(!open)} aria-label="Notifications" className="relative block">
        <Bell size={26} />
        {count > 0 && (
          <span className="absolute -right-1.5 -top-1.5 grid h-4.5 min-w-4.5 place-items-center rounded-full bg-red-500 px-1 text-[10px] font-bold text-white">{count > 9 ? '9+' : count}</span>
        )}
      </button>
      {open && <List onPick={() => setOpen(false)} />}
    </div>
  )
}

function List({ onPick }) {
  const { data, error } = useFetch('/api/notifications')
  const navigate = useNavigate()

  return (
    <div className="absolute -right-12 top-full z-20 mt-3 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-edge bg-white shadow-xl sm:right-0">
      <div className="border-b border-edge px-4 py-3 text-[15px] font-bold">Notifications</div>
      {error && <p className="p-4 text-[13px] text-red-600">{error}</p>}
      {!data && !error && <p className="p-4 text-[13px] text-muted">Loading...</p>}
      {data && data.items.length === 0 && <p className="p-6 text-center text-[13px] text-muted">You are all caught up.</p>}
      <ul className="max-h-96 divide-y divide-edge overflow-y-auto">
        {data?.items.map((n, i) => {
          const [Icon, colours] = ICONS[n.type]
          return (
            <li key={i}>
              <button onClick={() => { onPick(); navigate(n.link) }} className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-stone-50">
                <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-full ${colours}`}><Icon size={18} /></span>
                <span className="min-w-0">
                  <span className="block truncate text-[14px] font-semibold">{n.title}</span>
                  <span className="block text-[12.5px] text-muted">{n.detail}</span>
                </span>
              </button>
            </li>
          )
        })}
      </ul>
      {data && data.total > data.items.length && <p className="border-t border-edge px-4 py-2.5 text-center text-[12.5px] text-muted">and {data.total - data.items.length} more</p>}
    </div>
  )
}
