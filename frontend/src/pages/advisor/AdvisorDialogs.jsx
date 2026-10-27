import { useState } from 'react'
import { Check, ClipboardList, Scale, Tag } from 'lucide-react'
import Modal from '../../components/Modal'
import { get, put } from '../../api'
import { useAuth } from '../../AuthContext'
import { refreshData } from '../../useFetch'
import { Empty, rupees, shortDate } from '../../components/ui'
import { MarginPill, TARGET_MARGIN } from './ProductTables'

/** Every product sold in the period, best margin first, with a bar for its margin. */
export function MarginsDialog({ products, onClose }) {
  const sold = products.filter((p) => p.salesPaise > 0).sort((a, b) => b.marginPct - a.marginPct)
  const top = Math.max(TARGET_MARGIN, ...sold.map((p) => p.marginPct))
  return (
    <Modal title="Compare Product Margins" icon={<Scale size={22} className="text-[#1f5fbf]" />} onClose={onClose}>
      <div className="overflow-y-auto p-5">
        <p className="text-[13px] text-muted">Margin = profit ÷ sales for each product in this period. Below {TARGET_MARGIN}% is low.</p>
        {sold.length === 0 ? <Empty>Nothing was sold in this period.</Empty> : (
          <ul className="mt-4 space-y-3">
            {sold.map((p) => (
              <li key={p.productId}>
                <div className="flex items-center gap-2 text-[13px]">
                  <span className="flex-1 font-medium">{p.name}</span>
                  <span className="text-muted">{rupees(p.profitPaise)} of {rupees(p.salesPaise)}</span>
                  <MarginPill pct={p.marginPct} />
                </div>
                <div className="mt-1 h-2 rounded-full bg-stone-100">
                  <div className="h-2 rounded-full bg-[#2a78d6]" style={{ width: `${Math.max(0, (p.marginPct / top) * 100)}%` }} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </Modal>
  )
}

/** Products priced too close to their cost, and the price that would give the target margin. One tap sets it. */
export function PricesDialog({ suggestions, onClose }) {
  const { token } = useAuth()
  const [state, setState] = useState({}) // productId -> 'saving' | 'done' | error message

  const apply = async (s) => {
    setState((now) => ({ ...now, [s.productId]: 'saving' }))
    try {
      // The edit form sends the whole product, so do the same with only the price changed (it is checked and audited there).
      const product = await get(`/api/products/${s.productId}`, token)
      await put(`/api/products/${s.productId}`, { ...product, sellingPricePaise: s.suggestedPricePaise }, token)
      setState((now) => ({ ...now, [s.productId]: 'done' }))
      refreshData()
    } catch (err) {
      setState((now) => ({ ...now, [s.productId]: err.message }))
    }
  }

  return (
    <Modal title="Suggest Price Changes" icon={<Tag size={22} className="text-orange" />} onClose={onClose}>
      <div className="overflow-y-auto p-5">
        <p className="text-[13px] text-muted">
          These products earn less than {TARGET_MARGIN}% on their current price. The suggested price gives about {TARGET_MARGIN}%.
          Check what nearby shops charge before changing.
        </p>
        {suggestions.length === 0 ? <Empty>All your prices give at least {TARGET_MARGIN}% margin. Nothing to change.</Empty> : (
          <ul className="mt-4 divide-y divide-edge">
            {suggestions.map((s) => {
              const status = state[s.productId]
              return (
                <li key={s.productId} className="py-3">
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
                    <div className="min-w-0 flex-1">
                      <div className="text-[14px] font-semibold">{s.name}</div>
                      <div className="text-[12px] text-muted">
                        Cost {rupees(s.purchasePricePaise)} · now {rupees(s.sellingPricePaise)} (<MarginText pct={s.marginPct} />)
                        {' → '}<b className="text-ink">{rupees(s.suggestedPricePaise)}</b> ({Math.round(s.suggestedMarginPct)}%)
                      </div>
                    </div>
                    {status === 'done' ? (
                      <span className="flex items-center gap-1 text-[13px] font-semibold text-brand"><Check size={16} /> Price set</span>
                    ) : (
                      <button onClick={() => apply(s)} disabled={status === 'saving'}
                        className="rounded-lg bg-brand px-3 py-2 text-[13px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">
                        {status === 'saving' ? 'Saving...' : `Set ${rupees(s.suggestedPricePaise)}`}
                      </button>
                    )}
                  </div>
                  {status && !['saving', 'done'].includes(status) && <p className="mt-1 text-[12px] text-red-600">{status}</p>}
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </Modal>
  )
}

const MarginText = ({ pct }) => <span className={pct < 0 ? 'text-red-600' : ''}>{pct === null ? '—' : `${Math.round(pct)}%`}</span>

/** The stock orders placed in the period. */
export function PurchasesDialog({ purchases, onClose }) {
  return (
    <Modal title="Purchases in this period" icon={<ClipboardList size={22} className="text-[#6250d6]" />} onClose={onClose}>
      <div className="overflow-y-auto p-5">
        {purchases.length === 0 ? <Empty>No stock was ordered in this period.</Empty> : (
          <div className="overflow-x-auto">
            <table className="w-full text-[13px]">
              <thead className="border-b border-edge text-left text-[12px] text-muted">
                <tr><th className="py-2 pr-2">Date</th><th className="py-2 pr-2">Supplier</th><th className="py-2 pr-2">Status</th><th className="py-2 pr-2 text-right">Total</th><th className="py-2 text-right">Still to pay</th></tr>
              </thead>
              <tbody>
                {purchases.map((p) => (
                  <tr key={p.purchaseId} className="border-b border-edge/60 last:border-0">
                    <td className="whitespace-nowrap py-2 pr-2">{shortDate(p.date)}</td>
                    <td className="py-2 pr-2">{p.supplier || '—'}</td>
                    <td className="py-2 pr-2">
                      <span className={`rounded-md px-2 py-0.5 text-[12px] ${p.status === 'RECEIVED' ? 'bg-[#e1f5e8] text-brand' : 'bg-[#e6effd] text-[#1f5fbf]'}`}>
                        {p.status === 'RECEIVED' ? 'Received' : 'On order'}
                      </span>
                    </td>
                    <td className="py-2 pr-2 text-right">{rupees(p.totalPaise)}</td>
                    <td className={`py-2 text-right ${p.duePaise > 0 ? 'text-orange' : 'text-muted'}`}>{rupees(p.duePaise)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </Modal>
  )
}
