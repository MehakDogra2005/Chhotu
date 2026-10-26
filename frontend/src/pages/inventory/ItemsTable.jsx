import { CircleAlert, CircleX, EllipsisVertical, Package, Plus, Search, Truck, Box } from 'lucide-react'
import { Card, StatusPill, number, relativeDay } from '../../components/ui'

// [filter sent to the backend, label, how to read its count, colours, icon]
const TABS = [
  ['all', 'All Items', (c) => c.all, 'bg-white text-ink', Box],
  ['low', 'Low Stock', (c) => c.low, 'bg-[#fde8e5] text-red-600', CircleAlert],
  ['out', 'Out of Stock', (c) => c.out, 'bg-[#fde8e5] text-red-600', CircleX],
  ['order', 'On Order', (c) => c.onOrder, 'bg-[#e3ecfb] text-[#2563c9]', Truck],
]
const HEADINGS = ['Item Name', 'Category', 'Current Stock', 'Reorder Level', 'Status', 'Last Updated', 'Actions']

export default function ItemsTable({ data, filter, setFilter, search, setSearch }) {
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-center gap-2">
        {TABS.map(([key, label, count, colours, Icon]) => (
          <button key={key} onClick={() => setFilter(key)}
            className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-[12.5px] font-semibold ${colours} ${filter === key ? 'ring-2 ring-current' : ''}`}>
            <Icon size={14} /> {label} ({count(data.counts)})
          </button>
        ))}
        <label className="ml-auto flex w-40 items-center gap-2 rounded-xl border border-edge bg-white px-4 py-2.5 text-[13px]">
          <Search size={16} />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search items..." className="w-full outline-none" />
        </label>
        {/* Adding products is not built yet */}
        <button className="flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-[14px] font-semibold text-white">
          <Plus size={16} /> Add Item
        </button>
      </div>

      <table className="mt-4 w-full text-left text-[13px]">
        <thead>
          <tr className="bg-[#f6f1e6] text-[12.5px]">
            <th className="w-10 p-3"><input type="checkbox" /></th>
            {HEADINGS.map((h) => <th key={h} className="p-3 font-semibold">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {data.items.map((item) => (
            <tr key={item.name} className="border-b border-edge/60 last:border-0">
              <td className="p-3"><input type="checkbox" /></td>
              <td className="p-3">
                <div className="flex items-center gap-3">
                  <span className="grid h-10 w-10 shrink-0 place-items-center rounded-lg bg-[#f6efe0] text-[#b08a4a]"><Package size={20} /></span>
                  {item.name}
                </div>
              </td>
              <td className="p-3 text-muted">{item.category}</td>
              <td className="p-3">{number(item.stock)} {item.unit}</td>
              <td className="p-3">{number(item.reorderLevel)}</td>
              <td className="p-3"><StatusPill status={item.status} /></td>
              <td className="p-3 text-muted">{relativeDay(item.lastUpdated, data.today)}</td>
              <td className="p-3"><button className="text-muted"><EllipsisVertical size={18} /></button></td>
            </tr>
          ))}
        </tbody>
      </table>
      {data.items.length === 0 && <p className="py-8 text-center text-[13px] text-muted">No items found.</p>}
    </Card>
  )
}
