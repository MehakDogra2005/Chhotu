import { IndianRupee, ShoppingBag, Siren, Users } from 'lucide-react'
import { Change, StatCard, rupees } from '../../components/ui'

export default function Summary({ summary, periodInfo }) {
  const cards = [
    {
      icon: <IndianRupee size={24} />, tint: 'bg-[#e1f5e8] text-brand',
      value: rupees(summary.salesPaise), label: periodInfo.sales,
      footer: <Change pct={summary.salesChangePct} label={periodInfo.vs} />,
    },
    {
      icon: <ShoppingBag size={24} />, tint: 'bg-[#dff1fb] text-[#1d8fc9]',
      value: summary.itemsSold, label: 'Items Sold',
      footer: <Change pct={summary.itemsSoldChangePct} label={periodInfo.vs} />,
    },
    {
      icon: <Users size={24} />, tint: 'bg-[#fdebe0] text-orange',
      value: rupees(summary.pendingUdhaarPaise), label: 'Pending Udhaar',
      footer: <div className="text-[13px] text-orange">{summary.pendingCustomers} customers</div>,
    },
    {
      icon: <Siren size={24} />, tint: 'bg-[#fde6e4] text-red-500',
      value: summary.lowStockItems, label: 'Low Stock Items',
      footer: summary.lowStockItems > 0
        ? <div className="text-[13px] text-red-500">Need restock</div>
        : <div className="text-[13px] text-brand">All stocked up</div>,
    },
  ]

  return (
    <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
      {cards.map((c) => <StatCard key={c.label} {...c} />)}
    </div>
  )
}
