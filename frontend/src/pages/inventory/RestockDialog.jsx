import { useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Copy, ListChecks, MessageCircle } from 'lucide-react'
import { post } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch, { refreshData } from '../../useFetch'
import Modal from '../../components/Modal'
import { Empty, number, rupees } from '../../components/ui'

/**
 * "Generate Restocking List": what to order, how much, and what it costs.
 * ids is the products that were ticked in the table; without it, everything low or out of stock.
 * The list can be shared on WhatsApp or copied, and "Place order" saves it as purchase orders.
 */
export default function RestockDialog({ ids, onClose }) {
  const { token } = useAuth()
  const { me } = useOutletContext()
  const { data, error } = useFetch(ids?.length ? `/api/restock?ids=${ids.join(',')}` : '/api/restock')
  const [quantities, setQuantities] = useState({}) // what the shopkeeper typed, by product id
  const [skipped, setSkipped] = useState([]) // products un-ticked in this list
  const [result, setResult] = useState('') // shown after the order is placed
  const [problem, setProblem] = useState('')
  const [busy, setBusy] = useState(false)

  const quantityOf = (s) => quantities[s.productId] ?? s.suggestedQuantity
  const chosen = (data || []).filter((s) => !s.onOrder && !skipped.includes(s.productId))
  const total = chosen.reduce((sum, s) => sum + quantityOf(s) * s.unitCostPaise, 0)
  const toggle = (id) => setSkipped(skipped.includes(id) ? skipped.filter((x) => x !== id) : [...skipped, id])

  // The same list as plain text, for WhatsApp or the clipboard.
  const listText = `Order list from ${me.shopName}:\n` + chosen.map((s) => `- ${s.name}: ${quantityOf(s)} ${s.unit}`).join('\n')

  // The page behind is refreshed only when this closes: reloading the list earlier would drop the items just ordered.
  const close = () => {
    if (result) refreshData()
    onClose()
  }

  const placeOrder = async () => {
    setBusy(true)
    setProblem('')
    try {
      const done = await post('/api/restock/orders', { lines: chosen.map((s) => ({ productId: s.productId, quantity: Number(quantityOf(s)) })) }, token)
      setResult(done.message)
    } catch (err) {
      setProblem(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Restocking List" icon={<ListChecks size={22} className="text-[#2563c9]" />} onClose={close}>
      <div className="overflow-y-auto p-4">
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        {!data && !error && <p className="text-[13px] text-muted">Loading...</p>}
        {data?.length === 0 && !result && <Empty>Nothing needs restocking right now.</Empty>}
        {result && <p className="rounded-xl bg-mint p-4 text-[14px] font-semibold text-heading">{result}</p>}

        {data?.length > 0 && !result && (
          <>
            <p className="mb-3 text-[12.5px] text-muted">Suggested amounts bring each item up to twice its reorder level. Change a number if you like.</p>
            <ul className="divide-y divide-edge">
              {data.map((s) => (
                <li key={s.productId} className="flex items-center gap-3 py-3 text-[13.5px]">
                  <input type="checkbox" checked={!s.onOrder && !skipped.includes(s.productId)} disabled={s.onOrder} onChange={() => toggle(s.productId)} aria-label={`Order ${s.name}`} />
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-semibold">{s.name}</div>
                    <div className="text-[12px] text-muted">
                      {s.onOrder ? 'Already on order' : `${number(s.stock)} left (reorder at ${number(s.reorderLevel)})`}{s.supplierName && ` · ${s.supplierName}`}
                    </div>
                  </div>
                  <input type="number" min="1" disabled={s.onOrder} value={quantityOf(s)} onChange={(e) => setQuantities({ ...quantities, [s.productId]: e.target.value })}
                    className="h-10 w-16 rounded-lg border border-line bg-white px-2 text-center outline-none focus:border-brand disabled:bg-stone-100" aria-label={`Quantity of ${s.name}`} />
                  <span className="w-12 text-[12px] text-muted">{s.unit}</span>
                </li>
              ))}
            </ul>
          </>
        )}
        {problem && <p className="mt-3 text-[13px] text-red-600">{problem}</p>}
      </div>

      {chosen.length > 0 && (
        <div className="space-y-3 border-t border-edge p-4">
          {!result && <div className="flex justify-between text-[14px]"><span className="text-muted">{chosen.length} items, about</span><b>{rupees(total)}</b></div>}
          <div className="flex gap-2">
            <a href={`https://wa.me/?text=${encodeURIComponent(listText)}`} target="_blank" rel="noreferrer"
              className="flex h-11 flex-1 items-center justify-center gap-2 rounded-xl border border-[#25a244] text-[13.5px] font-semibold text-[#1a7f37] hover:bg-[#eefaf1]"><MessageCircle size={18} /> Share on WhatsApp</a>
            <button onClick={() => navigator.clipboard?.writeText(listText)} aria-label="Copy the list" className="grid h-11 w-11 place-items-center rounded-xl border border-line hover:bg-stone-50"><Copy size={18} /></button>
          </div>
          {!result && <button onClick={placeOrder} disabled={busy} className="h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">{busy ? 'Placing...' : 'Place order'}</button>}
          {result && <button onClick={close} className="h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark">Done</button>}
        </div>
      )}
    </Modal>
  )
}
