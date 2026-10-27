import { Calendar, ChevronDown } from 'lucide-react'

// The periods a page can show. The keys match the backend's `period` parameter; `vs` is what it is compared with.
export const PERIODS = {
  week: { label: 'Last 7 days', vs: 'vs last week' },
  month: { label: 'This Month', vs: 'vs last month' },
  'last-month': { label: 'Last Month', vs: 'vs month before' },
  quarter: { label: 'Last 3 months', vs: 'vs 3 months earlier' },
  year: { label: 'This Year', vs: 'vs last year' },
}

export const longDate = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/** The period dropdown and, on wider screens, its dates. range is the backend's period ({ from, to }). */
export default function PeriodSelect({ value, onChange, range }) {
  return (
    <div className="flex items-center gap-4">
      <label className="relative flex items-center gap-2 rounded-xl border border-edge bg-white px-4 py-2.5 text-[15px] font-medium shadow-sm">
        <Calendar size={18} />
        <select value={value} onChange={(e) => onChange(e.target.value)} className="appearance-none bg-transparent pr-6 outline-none" aria-label="Period">
          {Object.entries(PERIODS).map(([key, p]) => <option key={key} value={key}>{p.label}</option>)}
        </select>
        <ChevronDown size={16} className="pointer-events-none absolute right-3" />
      </label>
      {range && <span className="hidden text-[15px] text-muted md:inline">{longDate(range.from)} – {longDate(range.to)}</span>}
    </div>
  )
}
