import { useState } from 'react'
import { CircleAlert, CircleCheck, EllipsisVertical, Plus, Search, Users } from 'lucide-react'
import { Card, rupees, shortDate } from '../../components/ui'

// [key, label, which rows, colours, icon]
const TABS = [
  ['all', 'All Customers', () => true, 'bg-white text-ink', Users],
  ['pending', 'Pending Payments', (c) => c.pendingPaise > 0, 'bg-[#fde8e5] text-red-600', CircleAlert],
  ['paid', 'Recently Paid', (c) => c.paidInPeriod, 'bg-[#e3ecfb] text-[#2563c9]', CircleCheck],
]
export const tabFilter = (key) => TABS.find((t) => t[0] === key)[2]

const SHOWN = 8

const AVATAR_TINTS = ['bg-[#e6effd] text-[#1f5fbf]', 'bg-[#fdebd0] text-[#b4501f]', 'bg-[#e1f5e8] text-brand', 'bg-[#efeafc] text-[#6250d6]',
  'bg-[#fde7ef] text-[#b83d70]', 'bg-[#fff1dc] text-[#8a5a00]']

/** A round badge with the customer's initials; the colour stays the same for the same name. */
export function Avatar({ name, size = 'h-9 w-9 text-[13px]' }) {
  const initials = name.split(' ').map((w) => w[0]).slice(0, 2).join('').toUpperCase()
  const hash = [...name].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) % 997, 7)
  return <span className={`grid shrink-0 place-items-center rounded-full font-bold ${size} ${AVATAR_TINTS[hash % AVATAR_TINTS.length]}`}>{initials}</span>
}

/** "Today", "2 days ago", "3 weeks ago"... dates after today (the demo has a few) are shown as dates. */
export function ago(iso, today) {
  if (!iso) return '—'
  const days = Math.round((new Date(today) - new Date(iso)) / 86400000)
  if (days < 0) return shortDate(iso)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  if (days < 7) return `${days} days ago`
  if (days < 14) return '1 week ago'
  if (days < 60) return `${Math.floor(days / 7)} weeks ago`
  return `${Math.floor(days / 30)} months ago`
}

/** Pending amount: red when some of it is overdue, amber when not due yet, green when nothing is owed. */
export function PendingPill({ customer }) {
  const c = customer
  const tone = c.pendingPaise <= 0 ? 'bg-[#e1f5e8] text-brand' : c.overduePaise > 0 ? 'bg-[#fde3e1] text-red-600' : 'bg-[#fff1dc] text-[#a36a00]'
  return <span className={`inline-block whitespace-nowrap rounded-md px-2.5 py-1 text-[12.5px] font-semibold ${tone}`}>{rupees(Math.max(c.pendingPaise, 0))}</span>
}

const COLUMNS = [
  ['#', 'hidden w-10 sm:table-cell'],
  ['Customer Name', ''],
  ['Phone', 'hidden lg:table-cell'],
  ['Total Udhaar', 'hidden md:table-cell'],
  ['Paid', 'hidden md:table-cell'],
  ['Pending', ''],
  ['Last Transaction', 'hidden 2xl:table-cell'],
  ['Actions', ''],
]
const cell = (i) => `p-2 sm:p-3 ${COLUMNS[i][1]}`

/** onPay / onView / onMenu get the customer; onAdd is the "Add Customer" button. */
export default function CustomerTable({ customers, today, tab, setTab, search, setSearch, onAdd, onPay, onView, onMenu }) {
  const [showAll, setShowAll] = useState(false)
  const words = search.trim().toLowerCase()
  const rows = customers.filter(tabFilter(tab)).filter((c) => !words || c.name.toLowerCase().includes(words) || (c.phone || '').includes(words))
  const shown = showAll ? rows : rows.slice(0, SHOWN)

  return (
    <Card className="p-4 sm:p-5" id="customer-list">
      <div className="flex flex-wrap items-center gap-2">
        {TABS.map(([key, label, test, colours, Icon]) => (
          <button key={key} onClick={() => setTab(key)}
            className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-[12.5px] font-semibold ${colours} ${tab === key ? 'ring-2 ring-current' : ''}`}>
            <Icon size={14} /> {label} ({customers.filter(test).length})
          </button>
        ))}
        <label className="flex w-full items-center gap-2 rounded-xl border border-edge bg-white px-3 py-2.5 text-[13px] sm:ml-auto sm:w-40">
          <Search size={16} />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search customers..." aria-label="Search customers" className="w-full outline-none" />
        </label>
        <button onClick={onAdd} className="flex items-center gap-2 whitespace-nowrap rounded-xl bg-brand px-4 py-2.5 text-[14px] font-semibold text-white hover:bg-brand-dark">
          <Plus size={16} /> Add Customer
        </button>
      </div>
      {tab === 'paid' && <p className="mt-2 text-[12px] text-muted">Customers who paid something in the chosen period.</p>}

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-left text-[13px]">
          <thead>
            <tr className="bg-[#f6f1e6] text-[12.5px]">
              {COLUMNS.map(([heading, hide]) => <th key={heading} className={`p-2 font-semibold sm:p-3 ${hide}`}>{heading}</th>)}
            </tr>
          </thead>
          <tbody>
            {shown.map((c, i) => (
              <tr key={c.customerId} className="border-b border-edge/60 last:border-0">
                <td className={`${cell(0)} text-muted`}>{i + 1}</td>
                <td className={cell(1)}>
                  <button onClick={() => onView(c)} className="flex items-center gap-3 text-left hover:text-brand">
                    <Avatar name={c.name} />
                    <span>
                      <span className="block whitespace-nowrap font-medium">{c.name}</span>
                      <span className="block text-[11.5px] text-muted lg:hidden">{c.phone || c.type}</span>
                    </span>
                  </button>
                </td>
                <td className={`${cell(2)} text-muted`}>{c.phone || '—'}</td>
                <td className={cell(3)}>{rupees(c.totalCreditPaise)}</td>
                <td className={`${cell(4)} text-brand`}>{rupees(c.paidPaise)}</td>
                <td className={cell(5)}>
                  <PendingPill customer={c} />
                  {c.daysOverdue > 0 && <div className="mt-1 whitespace-nowrap text-[11px] text-red-600">{c.daysOverdue} days late</div>}
                </td>
                <td className={`${cell(6)} text-muted`}>{ago(c.lastTransaction, today)}</td>
                <td className={cell(7)}>
                  <div className="flex items-center gap-1">
                    {c.pendingPaise > 0
                      ? <button onClick={() => onPay(c)} className="whitespace-nowrap rounded-md bg-[#e6effd] px-2.5 py-1.5 text-[12px] font-medium text-[#1f5fbf] hover:bg-[#d3e2fb]">Record Payment</button>
                      : <button onClick={() => onView(c)} className="rounded-md bg-[#e6effd] px-2.5 py-1.5 text-[12px] font-medium text-[#1f5fbf] hover:bg-[#d3e2fb]">View</button>}
                    <button onClick={() => onMenu(c)} aria-label={`More for ${c.name}`} className="rounded-lg p-1 text-muted hover:bg-stone-100"><EllipsisVertical size={18} /></button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {rows.length === 0 && <p className="py-8 text-center text-[13px] text-muted">{search ? 'No customer matches your search.' : 'No customers here yet.'}</p>}
      {rows.length > SHOWN && (
        <button onClick={() => setShowAll(!showAll)} className="mt-3 w-full rounded-xl border border-edge py-2 text-[13px] font-semibold text-brand hover:bg-stone-50">
          {showAll ? 'Show less' : `Show all (${rows.length})`}
        </button>
      )}
    </Card>
  )
}
