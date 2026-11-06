import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { Clock, HandCoins, ShoppingBag, Users, Wallet } from 'lucide-react'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import PeriodSelect, { PERIODS } from '../../components/PeriodSelect'
import { Change, StatCard, rupees } from '../../components/ui'
import RecordPaymentDialog from '../home/RecordPaymentDialog'
import CustomerTable from './CustomerTable'
import OrdersSection from './OrdersSection'
import { OutstandingChart, RecentTransactions, RemindersInsights } from './BottomCards'
import { AddByVoice, AskSaathi } from './SideCards'
import { AddCustomerDialog, CustomerDetailDialog, CustomerMenu, GiveCreditDialog, RecordingDialog, RemindersDialog, TransactionsDialog } from './CustomerDialogs'

const NEW_WORD = { week: 'this week', month: 'this month', 'last-month': 'last month', quarter: 'in 3 months', year: 'this year' }

export default function Customers() {
  const [period, setPeriod] = useState('month')
  const [tab, setTab] = useState('all')
  const [search, setSearch] = useState('')
  // { kind, id } — the customer is looked up again in fresh data, so a dialog shows new numbers after a change
  const [dialog, setDialog] = useState(null)
  const { data, error } = useFetch(`/api/customers?period=${period}`)

  // Munim Ji can open this page on a customer (?search=Ramesh&open=CUS-1) or on the order-taking part (?view=orders&customer=CUS-1)
  const [params, setParams] = useSearchParams()
  const { key } = useLocation()
  const view = params.get('view') === 'orders' ? 'orders' : 'udhaar'
  const wanted = params.get('search') || ''
  const openId = params.get('open')
  useEffect(() => {
    setSearch(wanted)
    setTab('all')
  }, [wanted, key])
  useEffect(() => {
    if (openId && data?.customers.some((c) => c.customerId === openId)) setDialog({ kind: 'view', id: openId })
  }, [openId, key, !!data])
  const showView = (next) => setParams(next === 'orders' ? { view: 'orders' } : {}, { replace: true })

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading your customers...</p>

  const info = PERIODS[period]
  const s = data.stats
  const open = (kind, customer) => setDialog({ kind, id: customer?.customerId, recording: customer?.recordingId ? customer : null })
  const close = () => setDialog(null)
  const customer = dialog?.id && data.customers.find((c) => c.customerId === dialog.id)
  const overdue = data.customers.filter((c) => c.pendingPaise > 0 && c.overduePaise > 0).sort((a, b) => b.overduePaise - a.overduePaise)
  const showPending = () => {
    setTab('pending')
    document.getElementById('customer-list')?.scrollIntoView({ behavior: 'smooth' })
  }

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <PageHeader icon={<Users size={48} strokeWidth={2.4} className="text-brand" />}
          title="Customer Handling (Udhaar Management)" subtitle="Manage customer credit, record payments, and get reminders." />
        {view === 'udhaar' && <PeriodSelect value={period} onChange={setPeriod} range={data.period} />}
      </div>

      <div className="mt-4 inline-flex rounded-xl border border-edge bg-white p-1 text-[14px] font-semibold" role="tablist" aria-label="Customer Handling sections">
        {[['udhaar', 'Udhaar', Users], ['orders', 'Orders', ShoppingBag]].map(([key, text, Icon]) => (
          <button key={key} role="tab" aria-selected={view === key} onClick={() => showView(key)}
            className={`flex items-center gap-2 rounded-lg px-4 py-2 ${view === key ? 'bg-brand text-white' : 'hover:bg-stone-100'}`}>
            <Icon size={16} /> {text}
          </button>
        ))}
      </div>

      {view === 'orders' ? (
        <div className="mt-4"><OrdersSection customers={data.customers} preselect={params.get('customer')} /></div>
      ) : (
      <>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="min-w-0 space-y-4">
          <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
            <StatCard icon={<Users size={24} />} tint="bg-[#e1f5e8] text-brand" value={rupees(s.outstandingPaise)} label="Total Outstanding"
              onClick={showPending} footer={<Change pct={s.outstandingChangePct} label={info.vs} good="down" />} />
            <StatCard icon={<Wallet size={24} />} tint="bg-[#dff1fb] text-[#1d8fc9]" value={s.customersOnUdhaar} label="Customers on Udhaar"
              onClick={showPending} footer={<div className="text-[12px] text-[#2563c9]">+{s.newOnUdhaar} new {NEW_WORD[period]}</div>} />
            <StatCard icon={<HandCoins size={24} />} tint="bg-[#fdebd0] text-orange" value={rupees(s.paymentsPaise)} label="Payments Received"
              onClick={() => setTab('paid')} footer={<Change pct={s.paymentsChangePct} label={info.vs} />} />
            <StatCard icon={<Clock size={24} />} tint="bg-[#fde3e1] text-red-600" value={s.overdueCustomers} label="Payments Overdue"
              onClick={() => open('remind-all')}
              footer={s.overdueCustomers > 0
                ? <div className="text-[12px] text-red-600">{rupees(s.overduePaise)} · need follow up</div>
                : <div className="text-[12px] text-brand">All on time</div>} />
          </div>

          <CustomerTable customers={data.customers} today={data.today} tab={tab} setTab={setTab} search={search} setSearch={setSearch}
            onAdd={() => open('add')} onPay={(c) => open('pay', c)} onView={(c) => open('view', c)} onMenu={(c) => open('menu', c)} />
        </div>

        <div className="space-y-4">
          <AskSaathi customers={data.customers} />
          <AddByVoice recordings={data.recordings} onOpen={(r) => open('recording', r)} />
        </div>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-2 2xl:grid-cols-3">
        <OutstandingChart customers={data.customers} today={data.today} onOpen={(c) => open('view', c)} onViewAll={showPending} />
        <RecentTransactions transactions={data.transactions} today={data.today} onViewAll={() => open('transactions')} />
        <RemindersInsights insights={data.insights} onRemind={() => open('remind-all')} />
      </div>
      </>
      )}

      {dialog?.kind === 'add' && <AddCustomerDialog onClose={close} />}
      {dialog?.kind === 'transactions' && <TransactionsDialog transactions={data.transactions} today={data.today} onClose={close} />}
      {dialog?.kind === 'remind-all' && <RemindersDialog customers={overdue} shopName={data.shopName} today={data.today} onClose={close} />}
      {dialog?.kind === 'recording' && <RecordingDialog recording={dialog.recording} onClose={close} />}
      {customer && dialog.kind === 'menu' && <CustomerMenu customer={customer} onPick={(kind) => open(kind, customer)} onClose={close} />}
      {customer && dialog.kind === 'view' && <CustomerDetailDialog customer={customer} today={data.today} onPick={(kind) => open(kind, customer)} onClose={close} />}
      {customer && dialog.kind === 'pay' && <RecordPaymentDialog customer={customer} onClose={close} />}
      {customer && dialog.kind === 'credit' && <GiveCreditDialog customer={customer} onClose={close} />}
      {customer && dialog.kind === 'remind' && <RemindersDialog customers={[customer]} shopName={data.shopName} today={data.today} onClose={close} />}
    </>
  )
}
