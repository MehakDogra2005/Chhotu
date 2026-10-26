import { Bell, ChevronRight } from 'lucide-react'
import { Empty, ViewAll, number } from './ui'

const TONES = {
  orange: { box: 'border-[#f3dfb8] from-[#fff4df] to-[#fffaf0]', accent: 'text-orange', bell: 'fill-[#f5a524] text-[#f5a524]', line: 'divide-[#f3e6c9]' },
  red: { box: 'border-[#f6d5d1] from-[#fff0ee] to-[#fff8f7]', accent: 'text-red-500', bell: 'fill-red-500 text-red-500', line: 'divide-[#f6e0dd]' },
}

/** A list of products that need restocking. `items` and `total` come from the backend. */
export default function ActionNeeded({ items, total, title = 'Action Needed', tone = 'orange', viewAll = false }) {
  const style = TONES[tone]

  return (
    <div className={`rounded-2xl border bg-linear-to-b p-5 ${style.box}`}>
      <div className="flex items-center gap-3">
        <Bell size={22} className={style.bell} />
        <h3 className="flex-1 text-[16px] font-bold">{title}</h3>
        <div className="text-right">
          <div className={`text-[12px] ${style.accent}`}>{total} items</div>
          {viewAll && <ViewAll />}
        </div>
      </div>
      {items.length === 0 && <Empty>Nothing needs your attention.</Empty>}
      <ul className={`mt-3 divide-y ${style.line}`}>
        {items.map((item) => {
          const urgent = item.stock <= item.reorderLevel / 2  // out of stock, or under half the reorder level
          return (
            <li key={item.name} className="flex items-center gap-3 py-3 text-[14px]">
              <span className={`h-3 w-3 rounded-full ${urgent ? 'bg-red-500' : 'bg-[#f5b324]'}`} />
              <span className="flex-1">{item.name} – {item.status === 'OUT' ? 'out of stock' : `${number(item.stock)} ${item.unit} left`}</span>
              <ChevronRight size={18} />
            </li>
          )
        })}
      </ul>
    </div>
  )
}
