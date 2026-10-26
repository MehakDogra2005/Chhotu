import { ArrowDown, ArrowRight, ArrowUp, CircleCheck, CircleX, TriangleAlert } from 'lucide-react'

// The backend sends money in paise (1 rupee = 100 paise).
export const rupees = (paise) => '₹' + Math.round(paise / 100).toLocaleString('en-IN')
export const number = (n) => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 1 })
export const shortDate = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })

export function Card({ className = '', children }) {
  return <div className={`rounded-2xl border border-edge bg-white/80 shadow-[0_1px_4px_rgba(0,0,0,0.04)] ${className}`}>{children}</div>
}

/** A number with an icon, a label and a small line underneath (used at the top of Home and Inventory). */
export function StatCard({ icon, tint, value, label, footer }) {
  return (
    <Card className="flex items-start gap-3 p-4">
      <span className={`grid h-12 w-12 shrink-0 place-items-center rounded-xl ${tint}`}>{icon}</span>
      <div className="min-w-0">
        <div className="text-[22px] font-extrabold leading-tight">{value}</div>
        <div className="text-[15px] text-[#2f3a44]">{label}</div>
        <div className="mt-1">{footer}</div>
      </div>
    </Card>
  )
}

const STATUS = {
  OUT: ['bg-[#fde3e1] text-red-600', 'Out of Stock', CircleX],
  LOW: ['bg-[#fff1dc] text-[#d97706]', 'Low Stock', TriangleAlert],
  OK: ['bg-[#e1f5e8] text-brand', 'In Stock', CircleCheck],
}

/** "Low Stock" / "Out of Stock" / "In Stock" badge. status comes from the backend: LOW, OUT or OK. */
export function StatusPill({ status }) {
  const [style, label, Icon] = STATUS[status]
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap rounded-md px-2.5 py-1 text-[12px] ${style}`}>
      <Icon size={12} /> {label}
    </span>
  )
}

/** "Today", "Yesterday", or a date like "16 Oct 2026". */
export function relativeDay(iso, today) {
  if (!iso) return '—'
  const days = Math.round((new Date(today) - new Date(iso)) / 86400000)
  if (days === 0) return 'Today'
  if (days === 1) return 'Yesterday'
  return new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function ViewAll() {
  return <button className="flex items-center gap-1 text-[13px] font-semibold text-brand">View all <ArrowRight size={14} /></button>
}

/** "↑ 12% vs last month". Shows nothing useful when there is no earlier period to compare with. */
export function Change({ pct, label = '' }) {
  if (pct === null || pct === undefined) return <div className="text-[13px] text-muted">No earlier data</div>
  const up = pct >= 0
  const Arrow = up ? ArrowUp : ArrowDown
  return (
    <div className={`flex items-center gap-1 text-[12px] ${up ? 'text-brand' : 'text-red-600'}`}>
      <Arrow size={13} /> {Math.abs(pct)}% {label}
    </div>
  )
}

/** Header used by the three bottom cards: icon tile, title, subtitle and a "View all" link. */
export function CardHeader({ icon, tint, title, note, subtitle, viewAll = true }) {
  return (
    <div className="flex items-start gap-3">
      <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-xl ${tint}`}>{icon}</span>
      <div className="min-w-0 flex-1">
        <div className="text-[16px] font-bold">{title} {note && <span className="text-[12px] font-semibold">{note}</span>}</div>
        <div className="mt-0.5 text-[12.5px] text-muted">{subtitle}</div>
      </div>
      {viewAll && <ViewAll />}
    </div>
  )
}

export const Empty = ({ children }) => <p className="py-6 text-center text-[13px] text-muted">{children}</p>

export const inputStyle = 'h-12 w-full rounded-xl border border-line bg-white px-4 text-[15px] outline-none focus:border-brand'

/** A label above an input or select. */
export function Field({ label, children }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-[13px] font-semibold text-[#2f3a44]">{label}</span>
      {children}
    </label>
  )
}

/** An on/off switch with a title and a line of explanation. */
export function Toggle({ checked, onChange, label, description }) {
  return (
    <label className="flex cursor-pointer items-center gap-4 py-3">
      <span className="flex-1">
        <span className="block text-[15px] font-semibold">{label}</span>
        <span className="block text-[13px] text-muted">{description}</span>
      </span>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} className="peer sr-only" />
      <span className="relative h-6 w-11 shrink-0 rounded-full bg-[#d3d7da] transition after:absolute after:left-0.5 after:top-0.5 after:h-5 after:w-5 after:rounded-full after:bg-white after:shadow after:transition peer-checked:bg-brand peer-checked:after:translate-x-5 peer-focus-visible:ring-2 peer-focus-visible:ring-brand/40" />
    </label>
  )
}
