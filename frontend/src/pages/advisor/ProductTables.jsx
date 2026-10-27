import { useState } from 'react'
import { CircleAlert, Trophy } from 'lucide-react'
import { Card, Empty, number, rupees } from '../../components/ui'

// Below this margin a product counts as low (the backend uses the same number for its advice).
export const TARGET_MARGIN = 15
const SHOW = 5

/** "22%" in green, amber or red, so the weak ones stand out. The number is always written, so colour is never the only sign. */
export function MarginPill({ pct }) {
  if (pct === null || pct === undefined) return <span className="text-muted">—</span>
  const tone = pct < TARGET_MARGIN ? 'bg-[#fde3e1] text-red-600' : pct < 25 ? 'bg-[#fff1dc] text-[#b45309]' : 'bg-[#e1f5e8] text-brand'
  return <span className={`inline-block min-w-12 rounded-md px-2 py-0.5 text-center text-[12px] font-semibold ${tone}`}>{Math.round(pct)}%</span>
}

function TableCard({ icon, title, rows, total, open, setOpen, children }) {
  return (
    <Card className="flex flex-col p-4">
      <div className="flex items-center gap-2">
        {icon}
        <h3 className="flex-1 text-[16px] font-bold">{title}</h3>
        {total > SHOW && (
          <button onClick={() => setOpen(!open)} className="text-[13px] font-semibold text-brand">{open ? 'Show less' : `View all (${total})`}</button>
        )}
      </div>
      {rows === 0 ? <Empty>Nothing was sold in this period.</Empty> : <div className="mt-2 overflow-x-auto">{children}</div>}
    </Card>
  )
}

const th = 'px-2 py-2 text-left text-[12px] font-semibold text-muted'
const td = 'px-2 py-2'

export function TopSellers({ products }) {
  const [open, setOpen] = useState(false)
  const sold = products.filter((p) => p.quantity > 0) // already most-sold first
  const rows = open ? sold : sold.slice(0, SHOW)
  return (
    <TableCard icon={<Trophy size={20} className="text-[#e8a33a]" />} title="Top Selling Products" rows={sold.length} total={sold.length} open={open} setOpen={setOpen}>
      <table className="w-full text-[13px]">
        <thead className="border-b border-edge bg-[#faf6ec]">
          <tr><th className={th}>#</th><th className={th}>Product</th><th className={`${th} text-right`}>Items Sold</th><th className={`${th} text-right`}>Revenue</th><th className={`${th} text-right`}>Margin</th></tr>
        </thead>
        <tbody>
          {rows.map((p, i) => (
            <tr key={p.productId} className="border-b border-edge/60 last:border-0">
              <td className={`${td} text-muted`}>{i + 1}</td>
              <td className={`${td} font-medium`}>{p.name}</td>
              <td className={`${td} text-right`}>{number(p.quantity)}</td>
              <td className={`${td} text-right`}>{rupees(p.salesPaise)}</td>
              <td className={`${td} text-right`}><MarginPill pct={p.marginPct} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableCard>
  )
}

export function LowestMargins({ products }) {
  const [open, setOpen] = useState(false)
  const sold = products.filter((p) => p.salesPaise > 0).sort((a, b) => a.marginPct - b.marginPct)
  const rows = open ? sold : sold.slice(0, SHOW)
  return (
    <TableCard icon={<CircleAlert size={20} className="fill-red-500 text-white" />} title="Lowest Profit Margin Products" rows={sold.length} total={sold.length} open={open} setOpen={setOpen}>
      <table className="w-full text-[13px]">
        <thead className="border-b border-edge bg-[#faf6ec]">
          <tr><th className={th}>Product</th><th className={`${th} text-right`}>Revenue</th><th className={`${th} text-right`}>Profit</th><th className={`${th} text-right`}>Margin</th></tr>
        </thead>
        <tbody>
          {rows.map((p) => (
            <tr key={p.productId} className="border-b border-edge/60 last:border-0">
              <td className={`${td} font-medium`}>{p.name}</td>
              <td className={`${td} text-right`}>{rupees(p.salesPaise)}</td>
              <td className={`${td} text-right ${p.profitPaise < 0 ? 'text-red-600' : ''}`}>{rupees(p.profitPaise)}</td>
              <td className={`${td} text-right`}><MarginPill pct={p.marginPct} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableCard>
  )
}
