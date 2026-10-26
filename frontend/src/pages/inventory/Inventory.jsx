import { useState } from 'react'
import { Box, ChartNoAxesCombined, Truck, TriangleAlert } from 'lucide-react'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import ActionNeeded from '../../components/ActionNeeded'
import { StatCard, rupees } from '../../components/ui'
import ItemsTable from './ItemsTable'
import { QuickActions, TalkToSaathi } from './SideCards'

export default function Inventory() {
  const [filter, setFilter] = useState('all') // all | low | out | order
  const [search, setSearch] = useState('')
  const { data, error } = useFetch(`/api/inventory?filter=${filter}&search=${encodeURIComponent(search)}`)

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading your stock...</p>

  const { counts } = data
  const needRestock = counts.low + counts.out

  return (
    <>
      <PageHeader icon={<Box size={48} strokeWidth={1.4} className="fill-[#fbe3b4] text-[#e8a33a]" />}
        title="Inventory Management" subtitle="Track your stock, update inventory and get smart recommendations." />

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
            <StatCard icon={<Box size={24} />} tint="bg-[#e1f5e8] text-brand" value={counts.all} label="Total Items"
              footer={<div className="text-[13px] text-brand">In your catalogue</div>} />
            <StatCard icon={<TriangleAlert size={24} />} tint="bg-[#fde6e4] text-red-500" value={needRestock} label="Low Stock Items"
              footer={needRestock > 0 ? <div className="text-[13px] text-red-500">Need restock</div> : <div className="text-[13px] text-brand">All stocked up</div>} />
            <StatCard icon={<Truck size={24} />} tint="bg-[#e3ecfb] text-[#2563c9]" value={counts.onOrder} label="Items on Order"
              footer={<div className="text-[13px] text-[#2563c9]">Pending delivery</div>} />
            <StatCard icon={<ChartNoAxesCombined size={24} />} tint="bg-[#ece6fb] text-[#6d4bc8]" value={rupees(counts.valuePaise)} label="Inventory Value"
              footer={<div className="text-[13px] text-muted">at purchase price</div>} />
          </div>

          <ItemsTable data={data} filter={filter} setFilter={setFilter} search={search} setSearch={setSearch} />
        </div>

        <div className="space-y-4">
          <TalkToSaathi names={data.alerts.map((a) => a.name)} />
          <ActionNeeded title="Low Stock Alert" tone="red" viewAll items={data.alerts} total={needRestock} />
          <QuickActions />
        </div>
      </div>
    </>
  )
}
