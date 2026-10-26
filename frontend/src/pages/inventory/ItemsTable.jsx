import { CircleAlert, CircleX, EllipsisVertical, Package, Plus, Search, Truck, Box } from 'lucide-react'
import { Card, StatusPill, number, relativeDay } from '../../components/ui'

// [filter sent to the backend, label, how to read its count, colours, icon]
const TABS = [
  ['all', 'All Items', (c) => c.all, 'bg-white text-ink', Box],
  ['low', 'Low Stock', (c) => c.low, 'bg-[#fde8e5] text-red-600', CircleAlert],
  ['out', 'Out of Stock', (c) => c.out, 'bg-[#fde8e5] text-red-600', CircleX],
  ['order', 'On Order', (c) => c.onOrder, 'bg-[#e3ecfb] text-[#2563c9]', Truck],
]

// [heading, extra classes]. Narrow screens (phones) drop the less important columns.
const COLUMNS = [
  ['Item Name', ''],
  ['Category', 'hidden md:table-cell'],
  ['Current Stock', ''],
  ['Reorder Level', 'hidden md:table-cell'],
  ['Status', ''],
  ['Last Updated', 'hidden 2xl:table-cell'],
  ['Actions', ''],
]
const [NAME, CATEGORY, STOCK, REORDER, STATUS, UPDATED, ACTIONS] = COLUMNS.map((c) => `p-2 sm:p-3 ${c[1]}`)

/**
 * The list of products. `selected` is the ticked product ids. onAction(kind, item) is called when a row's ⋮ is
 * tapped, onAdd for "Add Item", and onRestockSelected for the bar that shows once some rows are ticked.
 */
export default function ItemsTable({ data, filter, setFilter, search, setSearch, selected, setSelected, onAction, onAdd, onRestockSelected }) {
  const toggle = (id) => setSelected(selected.includes(id) ? selected.filter((x) => x !== id) : [...selected, id])
  const allTicked = data.items.length > 0 && data.items.every((i) => selected.includes(i.productId))
  const toggleAll = () => setSelected(allTicked ? [] : data.items.map((i) => i.productId))

  return (
    <Card className="p-4 sm:p-5">
      <div className="flex flex-wrap items-center gap-2">
        {TABS.map(([key, label, count, colours, Icon]) => (
          <button key={key} onClick={() => setFilter(key)}
            className={`flex items-center gap-1.5 rounded-lg px-3 py-2 text-[12.5px] font-semibold ${colours} ${filter === key ? 'ring-2 ring-current' : ''}`}>
            <Icon size={14} /> {label} ({count(data.counts)})
          </button>
        ))}
        <label className="flex w-full items-center gap-2 rounded-xl border border-edge bg-white px-4 py-2.5 text-[13px] sm:ml-auto sm:w-40">
          <Search size={16} />
          <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search items..." className="w-full outline-none" />
        </label>
        <button onClick={onAdd} className="flex items-center gap-2 rounded-xl bg-brand px-4 py-2.5 text-[14px] font-semibold text-white hover:bg-brand-dark">
          <Plus size={16} /> Add Item
        </button>
      </div>

      {/* Appears once some rows are ticked */}
      {selected.length > 0 && (
        <div className="mt-3 flex flex-wrap items-center gap-3 rounded-xl bg-mint px-4 py-2.5 text-[13px]">
          <b>{selected.length} selected</b>
          <button onClick={onRestockSelected} className="font-semibold text-brand hover:underline">Restocking list for these</button>
          <button onClick={() => setSelected([])} className="text-muted hover:underline">Clear</button>
        </div>
      )}

      <div className="mt-4 overflow-x-auto">
        <table className="w-full text-left text-[13px]">
          <thead>
            <tr className="bg-[#f6f1e6] text-[12.5px]">
              <th className="hidden w-10 p-3 md:table-cell"><input type="checkbox" checked={allTicked} onChange={toggleAll} aria-label="Select all" /></th>
              {COLUMNS.map(([heading, hide]) => <th key={heading} className={`p-2 font-semibold sm:p-3 ${hide}`}>{heading}</th>)}
            </tr>
          </thead>
          <tbody>
            {data.items.map((item) => (
              <tr key={item.productId} className="border-b border-edge/60 last:border-0">
                <td className="hidden p-3 md:table-cell">
                  <input type="checkbox" checked={selected.includes(item.productId)} onChange={() => toggle(item.productId)} aria-label={`Select ${item.name}`} />
                </td>
                <td className={NAME}>
                  <div className="flex items-center gap-3">
                    <span className="hidden h-10 w-10 shrink-0 place-items-center rounded-lg bg-[#f6efe0] text-[#b08a4a] sm:grid"><Package size={20} /></span>
                    {item.name}
                  </div>
                </td>
                <td className={`${CATEGORY} text-muted`}>{item.category}</td>
                <td className={STOCK}>{number(item.stock)} {item.unit}</td>
                <td className={REORDER}>{number(item.reorderLevel)}</td>
                <td className={STATUS}><StatusPill status={item.status} />{item.onOrder && <span className="ml-1.5 text-[11px] font-semibold text-[#2563c9]">On order</span>}</td>
                <td className={`${UPDATED} text-muted`}>{relativeDay(item.lastUpdated, data.today)}</td>
                <td className={ACTIONS}>
                  <button onClick={() => onAction('actions', item)} aria-label={`Actions for ${item.name}`} className="rounded-lg p-1 text-muted hover:bg-stone-100"><EllipsisVertical size={18} /></button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {data.items.length === 0 && <p className="py-8 text-center text-[13px] text-muted">No items found.</p>}
    </Card>
  )
}
