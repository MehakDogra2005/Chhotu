import { ArrowRight, BarChart3, Bell, CalendarClock, ChartNoAxesColumnIncreasing, ClipboardList, Star, TrendingDown, TrendingUp, TriangleAlert } from 'lucide-react'
import { Card, rupees } from '../../components/ui'
import { ago } from './CustomerTable'

const RED = '#e34948'
const AMBER = '#eda100'
const BARS = 6

function Title({ icon, title, onViewAll }) {
  return (
    <div className="flex items-center gap-2">
      {icon}
      <h3 className="flex-1 text-[16px] font-bold">{title}</h3>
      {onViewAll && <button onClick={onViewAll} className="flex items-center gap-1 text-[13px] font-semibold text-brand">View all <ArrowRight size={14} /></button>}
    </div>
  )
}

/** The customers who owe the most, as bars. Red means some of it is overdue, amber that it is not due yet. */
export function OutstandingChart({ customers, today, onOpen, onViewAll }) {
  const top = customers.filter((c) => c.pendingPaise > 0).slice(0, BARS)
  const max = Math.max(...top.map((c) => c.pendingPaise), 1)
  return (
    <Card className="p-4">
      <Title icon={<BarChart3 size={20} className="text-brand" />} title="Customer-wise Outstanding" onViewAll={onViewAll} />
      <div className="mt-2 flex gap-4 text-[12px] text-muted">
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: RED }} /> Has overdue udhaar</span>
        <span className="flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm" style={{ background: AMBER }} /> Not due yet</span>
      </div>
      {top.length === 0 ? <p className="py-8 text-center text-[13px] text-muted">Nobody owes you anything. </p> : (
        <ul className="mt-3 space-y-1">
          {top.map((c) => (
            <li key={c.customerId}>
              <button onClick={() => onOpen(c)} title={`${c.name}: owes ${rupees(c.pendingPaise)}${c.overduePaise > 0 ? `, ${rupees(c.overduePaise)} overdue (${c.daysOverdue} days)` : ', not due yet'}. Last entry ${ago(c.lastTransaction, today).toLowerCase()}.`}
                className="grid w-full grid-cols-[96px_1fr_56px] items-center gap-2 rounded-lg px-1 py-1.5 text-left text-[12.5px] hover:bg-stone-50">
                <span className="truncate">{c.name}</span>
                <span className="h-3.5 rounded-r-[4px] rounded-l-sm" style={{ width: `${Math.max((c.pendingPaise / max) * 100, 3)}%`, background: c.overduePaise > 0 ? RED : AMBER }} />
                <span className="text-right font-semibold">{rupees(c.pendingPaise)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

export const TXN_KINDS = {
  CREDIT: ['Udhaar given', 'bg-[#fde3e1] text-red-600'],
  PAYMENT: ['Payment', 'bg-[#e1f5e8] text-brand'],
  WAIVED: ['Waived', 'bg-[#efeafc] text-[#6250d6]'],
  PAYMENT_REVERSED: ['Correction', 'bg-[#fff1dc] text-[#a36a00]'],
}

export function KindPill({ kind }) {
  const [label, tone] = TXN_KINDS[kind] || [kind, 'bg-stone-100']
  return <span className={`inline-block whitespace-nowrap rounded-md px-2 py-0.5 text-[11.5px] font-medium ${tone}`}>{label}</span>
}

/** A table of ledger entries: date, customer, kind, amount and note. compact puts the note under the name (for narrow cards). */
export function TransactionRows({ transactions, today, compact = false }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-[12.5px]">
        <thead>
          <tr className="bg-[#f6f1e6] text-[12px]">
            <th className="p-2">When</th><th className="p-2">Customer</th>{!compact && <th className="p-2">Type</th>}<th className="p-2 text-right">Amount</th>
            {!compact && <th className="hidden p-2 sm:table-cell">Notes</th>}
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => (
            <tr key={t.id} className="border-b border-edge/60 last:border-0">
              <td className="whitespace-nowrap p-2 text-muted">{ago(t.date, today)}</td>
              <td className="p-2">
                <div className="whitespace-nowrap">{t.customerName}</div>
                {compact && t.note && <div className="max-w-[130px] truncate text-[11px] text-muted" title={t.note}>{t.note}</div>}
              </td>
              {!compact && <td className="p-2"><KindPill kind={t.kind} /></td>}
              <td className="p-2 text-right">
                <div className="font-semibold">{rupees(t.amountPaise)}</div>
                {compact && <KindPill kind={t.kind} />}
              </td>
              {!compact && <td className="hidden max-w-[220px] truncate p-2 text-muted sm:table-cell" title={t.note}>{t.note || '—'}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function RecentTransactions({ transactions, today, onViewAll }) {
  return (
    <Card className="p-4">
      <Title icon={<ClipboardList size={20} className="text-orange" />} title="Recent Transactions" onViewAll={transactions.length > 6 ? onViewAll : null} />
      <div className="mt-3">
        {transactions.length === 0 ? <p className="py-8 text-center text-[13px] text-muted">No udhaar or payments recorded yet.</p>
          : <TransactionRows transactions={transactions.slice(0, 6)} today={today} compact />}
      </div>
    </Card>
  )
}

const TIP_ICONS = {
  warn: [Bell, 'text-red-500'],
  up: [TrendingUp, 'text-brand'],
  down: [TrendingDown, 'text-red-600'],
  time: [CalendarClock, 'text-[#c2541f]'],
  star: [Star, 'text-[#2a78d6]'],
  good: [ChartNoAxesColumnIncreasing, 'text-brand'],
}

export function RemindersInsights({ insights, onRemind }) {
  return (
    <Card className="p-4">
      <Title icon={<Bell size={20} className="fill-red-500 text-red-500" />} title="Reminders & Insights" onViewAll={onRemind} />
      {insights.length === 0 ? <p className="py-8 text-center text-[13px] text-muted">Nothing to report yet.</p> : (
        <ul className="mt-3 space-y-3">
          {insights.map((tip) => {
            const [Icon, tone] = TIP_ICONS[tip.kind] || [TriangleAlert, 'text-muted']
            return (
              <li key={tip.text} className="flex items-start gap-3 text-[13px] leading-snug">
                <Icon size={17} className={`mt-0.5 shrink-0 ${tone}`} />
                <span className="flex-1">{tip.text}</span>
                {tip.action === 'remind' && (
                  <button onClick={onRemind} className="shrink-0 rounded-md bg-[#e6effd] px-2.5 py-1.5 text-[12px] font-semibold text-[#1f5fbf] hover:bg-[#d3e2fb]">Send Reminder</button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </Card>
  )
}
