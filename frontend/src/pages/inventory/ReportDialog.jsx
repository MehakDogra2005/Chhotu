import { ChartNoAxesColumnIncreasing, Download } from 'lucide-react'
import useFetch from '../../useFetch'
import Modal from '../../components/Modal'
import { number, rupees } from '../../components/ui'

/** Saves every product as a spreadsheet file (CSV) that opens in Excel or Google Sheets. */
function downloadCsv(products) {
  const rows = [['Item', 'Category', 'Stock', 'Unit', 'Reorder level', 'Status', 'Value (Rs)']]
  products.forEach((p) => rows.push([p.name, p.category, p.stock, p.unit, p.reorderLevel, p.status, p.valuePaise / 100]))
  const csv = rows.map((row) => row.map((cell) => `"${String(cell).replaceAll('"', '""')}"`).join(',')).join('\n')

  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  link.download = 'stock-report.csv'
  link.click()
}

/** "View Stock Report": what your stock is worth, by category. */
export default function ReportDialog({ onClose }) {
  const { data, error } = useFetch('/api/inventory/report')

  return (
    <Modal title="Stock Report" icon={<ChartNoAxesColumnIncreasing size={22} className="text-[#6d4bc8]" />} onClose={onClose}>
      <div className="overflow-y-auto p-5">
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        {!data && !error && <p className="text-[13px] text-muted">Loading...</p>}
        {data && (
          <>
            <div className="rounded-xl bg-[#ece6fb] p-4">
              <div className="text-[13px] text-muted">Your stock is worth (at buying price)</div>
              <div className="text-[26px] font-extrabold">{rupees(data.totalValuePaise)}</div>
            </div>
            <table className="mt-4 w-full text-left text-[13px]">
              <thead>
                <tr className="bg-[#f6f1e6] text-[12px]"><th className="p-2">Category</th><th>Items</th><th>Need restock</th><th className="text-right">Value</th></tr>
              </thead>
              <tbody>
                {data.categories.map((c) => (
                  <tr key={c.name} className="border-b border-edge/60 last:border-0">
                    <td className="p-2 py-2.5">{c.name}</td>
                    <td>{number(c.items)}</td>
                    <td className={c.needRestock > 0 ? 'font-semibold text-red-600' : ''}>{c.needRestock}</td>
                    <td className="text-right">{rupees(c.valuePaise)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </>
        )}
      </div>
      {data && (
        <div className="border-t border-edge p-4">
          <button onClick={() => downloadCsv(data.products)} className="flex h-12 w-full items-center justify-center gap-2 rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark">
            <Download size={18} /> Download all items (spreadsheet)
          </button>
        </div>
      )}
    </Modal>
  )
}
