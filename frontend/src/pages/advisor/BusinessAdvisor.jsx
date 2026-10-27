import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, ChartNoAxesColumnIncreasing, CircleDollarSign, IndianRupee, Percent, PieChart, ShoppingCart, TrendingUp, Wallet } from 'lucide-react'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import PeriodSelect, { PERIODS } from '../../components/PeriodSelect'
import { useAssistant } from '../../components/AssistantContext'
import { Card, Change, StatCard, rupees, shortDate } from '../../components/ui'
import { Donut, Legend, SERIES, TrendChart } from './Charts'
import { LowestMargins, TopSellers } from './ProductTables'
import { AskSaathi, KeyInsights, QuickActions } from './SideCards'
import { MarginsDialog, PricesDialog, PurchasesDialog } from './AdvisorDialogs'

const monthName = (iso) => new Date(iso + 'T00:00:00').toLocaleDateString('en-IN', { month: 'short' })
/** "-₹1,200" rather than "₹-1,200". */
const signedRupees = (paise) => (paise < 0 ? '-' : '') + rupees(Math.abs(paise))

/** How one bar group of the trend is named: on the axis (short) and in the tooltip (full). */
function bucketLabel(bucket) {
  return (b, short) => {
    if (bucket === 'month') return short ? monthName(b.from) : new Date(b.from + 'T00:00:00').toLocaleDateString('en-IN', { month: 'long', year: 'numeric' })
    if (bucket === 'week' && !short) return `${shortDate(b.from)} – ${shortDate(b.to)}`
    return shortDate(b.from)
  }
}

function Tile({ icon, tint, label, value, footer, className = '' }) {
  return (
    <div className={`flex items-start gap-3 rounded-xl p-3 ${className}`}>
      <span className={`grid h-10 w-10 shrink-0 place-items-center rounded-lg ${tint}`}>{icon}</span>
      <div className="min-w-0">
        <div className="text-[12.5px] text-muted">{label}</div>
        <div className="text-[20px] font-extrabold leading-tight">{value}</div>
        {footer}
      </div>
    </div>
  )
}

function CardTitle({ icon, title, right }) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      {icon}
      <h3 className="flex-1 text-[16px] font-bold">{title}</h3>
      {right}
    </div>
  )
}

export default function BusinessAdvisor() {
  const [period, setPeriod] = useState('month')
  const [dialog, setDialog] = useState(null) // 'margins' | 'prices' | 'purchases'
  const { data, error } = useFetch(`/api/advisor?period=${period}`)
  const { ask } = useAssistant()
  const navigate = useNavigate()

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading your business numbers...</p>

  const info = PERIODS[period]
  const { totals: t, spending: s } = data
  const net = s.netProfitPaise
  const netChange = s.previousNetProfitPaise > 0 ? Math.round(((net - s.previousNetProfitPaise) / s.previousNetProfitPaise) * 1000) / 10 : null

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHeader icon={<ChartNoAxesColumnIncreasing size={48} strokeWidth={2.4} className="text-brand" />}
          title="Business Advisor (Profit & Loss)" subtitle="Understand your business, track profits and get smart recommendations." />
        <PeriodSelect value={period} onChange={setPeriod} range={data.period} />
      </div>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="min-w-0 space-y-4">
          <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
            <StatCard icon={<IndianRupee size={24} />} tint="bg-[#e1f5e8] text-brand" value={rupees(t.salesPaise)} label="Total Sales"
              footer={<Change pct={t.salesChangePct} label={info.vs} />} />
            <StatCard icon={<ShoppingCart size={24} />} tint="bg-[#dff1fb] text-[#1d8fc9]" value={rupees(t.costPaise)} label="Total Purchase Cost"
              footer={<Change pct={t.costChangePct} label={info.vs} good="none" />} />
            <StatCard icon={<CircleDollarSign size={24} />} tint="bg-[#fdebd0] text-orange" value={rupees(t.profitPaise)} label="Estimated Profit"
              footer={<Change pct={t.profitChangePct} label={info.vs} />} />
            <StatCard icon={<Percent size={24} />} tint="bg-[#efeafc] text-[#6250d6]" value={t.marginPct === null ? '—' : `${t.marginPct}%`} label="Profit Margin"
              footer={<Change pct={t.marginChangePoints} unit=" pts" label={info.vs} />} />
          </div>
          <p className="-mt-2 text-[12px] text-muted">Purchase cost is what the items you sold cost you. Profit here is before shop expenses (see Net Profit below).</p>

          <div className="grid grid-cols-1 gap-4 2xl:grid-cols-[1.35fr_1fr]">
            <Card className="min-w-0 p-4">
              <CardTitle icon={<TrendingUp size={20} className="text-brand" />} title="Sales vs Profit Trend" />
              <div className="mt-1"><Legend items={[SERIES.sales, SERIES.cost, { ...SERIES.profit, line: true }]} /></div>
              <div className="mt-3"><TrendChart trend={data.trend} label={bucketLabel(data.period.bucket)} /></div>
            </Card>
            <Card className="p-4">
              <CardTitle icon={<PieChart size={20} className="text-[#2a78d6]" />} title="Sales by Category" />
              <div className="mt-3"><Donut slices={data.categories} totalLabel="Total Sales" emptyText="No sales in this period yet." /></div>
            </Card>
          </div>

          <div className="grid grid-cols-1 gap-4 2xl:grid-cols-2">
            <TopSellers products={data.products} />
            <LowestMargins products={data.products} />
          </div>

          <div className="grid grid-cols-1 gap-4 2xl:grid-cols-2">
            <Card className="p-4">
              <CardTitle icon={<ArrowRight size={20} className="-rotate-45 text-red-500" />} title="Purchase vs Sales Summary"
                right={<button onClick={() => setDialog('purchases')} className="flex items-center gap-1 text-[13px] font-semibold text-brand">View details <ArrowRight size={14} /></button>} />
              <div className="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
                <Tile icon={<ShoppingCart size={20} />} tint="bg-[#dff1fb] text-[#1d8fc9]" className="bg-[#f3f8fd]" label="Stock Bought (received)"
                  value={rupees(s.stockBoughtPaise)} footer={<Change pct={s.stockBoughtChangePct} label={info.vs} good="none" />} />
                <Tile icon={<IndianRupee size={20} />} tint="bg-[#e1f5e8] text-brand" className="bg-[#f3faf5]" label="Total Sales"
                  value={rupees(t.salesPaise)} footer={<Change pct={t.salesChangePct} label={info.vs} />} />
              </div>
              <Tile icon={<Wallet size={20} />} tint={net < 0 ? 'bg-[#fde3e1] text-red-600' : 'bg-[#e1f5e8] text-brand'}
                className={`mt-2 ${net < 0 ? 'bg-[#fdf1f0]' : 'bg-panel'}`} label="Net Profit (Estimated)"
                value={<span className={net < 0 ? 'text-red-600' : ''}>{signedRupees(net)}</span>}
                footer={<>
                  <div className="text-[12px] text-muted">Profit {rupees(t.profitPaise)} − shop expenses {rupees(s.expensesPaise)}</div>
                  {netChange !== null && <Change pct={netChange} label={info.vs} />}
                </>} />
            </Card>
            <Card className="p-4">
              <CardTitle icon={<Wallet size={20} className="text-orange" />} title="Expense Breakdown" />
              <p className="mt-0.5 text-[12px] text-muted">Where the money went: the cost of the goods you sold, and your shop expenses.</p>
              <div className="mt-3"><Donut slices={data.moneyOut} totalLabel="Money spent" emptyText="No costs or expenses in this period." /></div>
            </Card>
          </div>
        </div>

        <div className="space-y-4">
          <AskSaathi />
          <KeyInsights insights={data.insights} periodLabel={info.label} />
          <QuickActions
            onProfitReport={() => ask({ text: 'Is mahine ka profit batao' })}
            onCompare={() => setDialog('margins')}
            onPrices={() => setDialog('prices')}
            onPurchasePlan={() => navigate('/inventory?restock=1')} />
        </div>
      </div>

      {dialog === 'margins' && <MarginsDialog products={data.products} onClose={() => setDialog(null)} />}
      {dialog === 'prices' && <PricesDialog suggestions={data.priceSuggestions} onClose={() => setDialog(null)} />}
      {dialog === 'purchases' && <PurchasesDialog purchases={data.purchases} onClose={() => setDialog(null)} />}
    </>
  )
}
