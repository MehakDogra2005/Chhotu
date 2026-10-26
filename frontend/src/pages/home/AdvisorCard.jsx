import { ArrowRight, ChartNoAxesColumnIncreasing, Mic } from 'lucide-react'
import { Card, CardHeader, Change, rupees, shortDate } from '../../components/ui'

/** Rounds the biggest day up to a tidy number (e.g. 8,300 -> 9,000) so the axis labels are simple. */
function niceMax(rupeeValue) {
  if (rupeeValue <= 0) return 100
  const step = 10 ** Math.floor(Math.log10(rupeeValue))
  return Math.ceil(rupeeValue / step) * step
}
const axisLabel = (v) => (v >= 1000 ? `${v / 1000}K` : v)

function Figure({ value, label, change, className = '' }) {
  return (
    <div className="rounded-xl border border-edge bg-white/70 p-3">
      <div className={`text-[19px] font-extrabold ${className}`}>{value}</div>
      <div className="text-[12px] text-muted">{label}</div>
      {change}
    </div>
  )
}

export default function AdvisorCard({ advisor, periodInfo }) {
  const max = niceMax(Math.max(...advisor.trend.map((d) => d.salesPaise / 100)))

  return (
    <Card className="flex flex-col p-5">
      <CardHeader icon={<ChartNoAxesColumnIncreasing size={22} strokeWidth={3} />} tint="bg-[#fdebd0] text-orange"
        title="Business Advisor" note="(Profit & Loss)" subtitle="Understand your business and increase profits." />

      <div className="mt-4 grid grid-cols-3 gap-2">
        <Figure value={rupees(advisor.salesPaise)} label="Total Sales" change={<Change pct={advisor.salesChangePct} />} />
        <Figure value={rupees(advisor.profitPaise)} label="Gross Profit" change={<Change pct={advisor.profitChangePct} />} />
        <Figure value={advisor.marginPct === null ? '—' : `${advisor.marginPct}%`} label="Margin" className="text-brand"
          change={<Change pct={advisor.marginChangePoints} />} />
      </div>
      <p className="mt-1 text-[11px] text-muted">Compared with the previous {periodInfo.label.toLowerCase()}. Profit is before shop expenses.</p>

      <h4 className="mt-4 text-[13px] font-bold">Sales Trend (Last 7 Days)</h4>
      <div className="mt-2 flex gap-3">
        <div className="flex h-24 flex-col justify-between text-right text-[11px] text-muted">
          <span>{axisLabel(max)}</span><span>{axisLabel(max / 2)}</span><span>0</span>
        </div>
        <div className="flex-1">
          <div className="flex h-24 items-end gap-2 border-b border-edge">
            {advisor.trend.map((d) => (
              <div key={d.date} className="flex h-full flex-1 items-end" title={`${shortDate(d.date)}: ${rupees(d.salesPaise)}`}>
                <div className="w-full rounded-t bg-[#2bb673]" style={{ height: `${(d.salesPaise / 100 / max) * 100}%` }} />
              </div>
            ))}
          </div>
          <div className="mt-1 flex gap-2 text-[10px] text-muted">
            {advisor.trend.map((d) => <span key={d.date} className="flex-1 text-center">{shortDate(d.date)}</span>)}
          </div>
        </div>
      </div>

      {/* Business insights are not built yet */}
      <button className="mt-auto flex h-13 w-full items-center gap-3 rounded-xl bg-[#e6effd] px-5 text-[15px] font-semibold text-[#1f5fbf]">
        <Mic size={20} /> <span className="flex-1 text-center">Ask for Business Insights</span> <ArrowRight size={18} />
      </button>
    </Card>
  )
}
