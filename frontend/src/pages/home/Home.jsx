import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Calendar, ChevronDown } from 'lucide-react'
import useFetch from '../../useFetch'
import Summary from './Summary'
import VoicePanel from './VoicePanel'
import RecentConversation from './RecentConversation'
import ActionNeeded from '../../components/ActionNeeded'
import InventoryCard from './InventoryCard'
import AdvisorCard from './AdvisorCard'
import CustomerCard from './CustomerCard'

// What each period is called on the page. The keys match the backend's `period` parameter.
const PERIODS = {
  today: { label: 'Today', sales: "Today's Sales", vs: 'vs yesterday' },
  week: { label: 'Last 7 days', sales: 'Sales (7 days)', vs: 'vs last week' },
  month: { label: 'Last 30 days', sales: 'Sales (30 days)', vs: 'vs last month' },
}

export default function Home() {
  const { me } = useOutletContext() // who is logged in, from the shared layout
  const [period, setPeriod] = useState('month')
  const { data, error } = useFetch(`/api/dashboard?period=${period}`)

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading your shop...</p>

  const periodInfo = PERIODS[period]
  const firstName = (me.userName || 'there').split(' ')[0]
  const today = new Date(data.period.to + 'T00:00:00').toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h1 className="text-[24px] font-extrabold tracking-tight sm:text-[30px]">Namaste, {firstName} ji! 👋</h1>
          <p className="mt-1 text-[16px] text-muted sm:text-[18px]">Your shop at a glance today.</p>
        </div>
        <div className="flex items-center gap-4">
          <label className="relative flex items-center gap-2 rounded-xl border border-edge bg-white px-4 py-2.5 text-[15px] font-medium shadow-sm">
            <Calendar size={18} />
            <select value={period} onChange={(e) => setPeriod(e.target.value)} className="appearance-none bg-transparent pr-6 outline-none">
              {Object.entries(PERIODS).map(([key, p]) => <option key={key} value={key}>{p.label}</option>)}
            </select>
            <ChevronDown size={16} className="pointer-events-none absolute right-3" />
          </label>
          <span className="hidden text-[16px] text-muted md:inline">{today}</span>
        </div>
      </div>

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="space-y-4">
          <Summary summary={data.summary} periodInfo={periodInfo} />
          <VoicePanel firstProduct={data.inventory[0]?.name} firstCustomer={data.customers[0]?.name} />
        </div>
        <div className="space-y-4">
          <RecentConversation messages={data.conversations} />
          <ActionNeeded items={data.actionNeeded} total={data.summary.lowStockItems} />
        </div>
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 xl:grid-cols-3">
        <InventoryCard items={data.inventory} />
        <AdvisorCard advisor={data.advisor} periodInfo={periodInfo} />
        <CustomerCard debtors={data.customers} voiceNotes={data.voiceNotes} callRecordings={data.callRecordings} />
      </div>
    </>
  )
}
