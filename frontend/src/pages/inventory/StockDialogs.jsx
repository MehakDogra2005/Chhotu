import { useState } from 'react'
import { History, PackagePlus } from 'lucide-react'
import { post } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import Modal from '../../components/Modal'
import useSubmit from '../../components/useSubmit'
import { inputStyle, number, shortDate } from '../../components/ui'

/** Stock came in: type how much. */
export function AddStockDialog({ item, onClose }) {
  const { token } = useAuth()
  const [quantity, setQuantity] = useState('')
  const { busy, error, run } = useSubmit(onClose)

  return (
    <Modal title={`Add stock: ${item.name}`} icon={<PackagePlus size={22} className="text-brand" />} onClose={onClose}>
      <form onSubmit={run(() => post(`/api/products/${item.productId}/stock`, { quantity: Number(quantity) }, token))} className="space-y-4 p-5">
        <p className="rounded-xl bg-mint p-4 text-[14px]">You have <b>{number(item.stock)} {item.unit}</b> now.</p>
        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold">How many {item.unit} came in?</span>
          <input type="number" min="1" step="any" inputMode="decimal" autoFocus value={quantity} onChange={(e) => setQuantity(e.target.value)} placeholder="e.g. 10" className={inputStyle} />
        </label>
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        <button disabled={busy || !quantity} className="h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">
          {busy ? 'Saving...' : 'Add stock'}
        </button>
      </form>
    </Modal>
  )
}

// How each kind of stock movement is shown.
const KINDS = {
  PURCHASE_IN: 'Order received', MANUAL_IN: 'Stock added', SALE_OUT: 'Sold', RETURN_IN: 'Customer return',
  ADJUSTMENT: 'Stock correction', WASTAGE: 'Wasted or damaged',
}

/** The latest stock changes of one item, newest first. */
export function HistoryDialog({ item, onClose }) {
  const { data, error } = useFetch(`/api/products/${item.productId}/movements`)

  return (
    <Modal title={`History: ${item.name}`} icon={<History size={22} className="text-brand" />} onClose={onClose}>
      <div className="overflow-y-auto p-2 sm:p-4">
        {error && <p className="p-3 text-[13px] text-red-600">{error}</p>}
        {!data && !error && <p className="p-3 text-[13px] text-muted">Loading...</p>}
        {data?.length === 0 && <p className="p-6 text-center text-[13px] text-muted">No stock changes yet.</p>}
        <ul className="divide-y divide-edge">
          {data?.map((m, i) => (
            <li key={i} className="flex items-center gap-3 px-2 py-3 text-[13.5px]">
              <span className="w-14 shrink-0 text-[12px] text-muted">{shortDate(m.date)}</span>
              <span className="min-w-0 flex-1">{KINDS[m.type] || m.type}</span>
              <span className={`w-16 text-right font-semibold ${m.quantityChange >= 0 ? 'text-brand' : 'text-red-600'}`}>{m.quantityChange >= 0 ? '+' : ''}{number(m.quantityChange)}</span>
              <span className="w-20 text-right text-[12px] text-muted">{number(m.balanceAfter)} {m.unit}</span>
            </li>
          ))}
        </ul>
      </div>
    </Modal>
  )
}
