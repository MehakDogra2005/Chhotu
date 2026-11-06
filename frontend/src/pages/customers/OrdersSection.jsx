import { useEffect, useState } from 'react'
import { Mic, Plus, ShoppingBag, Trash2 } from 'lucide-react'
import { post } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch, { refreshData } from '../../useFetch'
import { useAssistant } from '../../components/AssistantContext'
import { Card } from '../../components/ui'

const STATUS = {
  CONFIRMED: 'bg-[#e1f5e8] text-brand',
  COMPLETED: 'bg-[#e3ecfb] text-[#2563c9]',
  CANCELLED: 'bg-[#fde3e1] text-red-600',
  PENDING_CONFIRMATION: 'bg-[#fff1dc] text-[#a36a00]',
  DRAFT: 'bg-stone-100 text-muted',
}
const label = (status) => status.replace('_', ' ').toLowerCase().replace(/^./, (c) => c.toUpperCase())
const dateText = (at) => new Date(at).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })

/**
 * The order-taking part of Customer Handling. A shopkeeper can take an order here with the form, or by voice
 * ("Ramesh ka order lena hai"): while Saathi is taking one, it shows up in the blue box. Saving an order does not
 * change stock; stock goes down when the goods are sold.
 */
export default function OrdersSection({ customers, preselect }) {
  const { token } = useAuth()
  const { ask, draft } = useAssistant()
  const orders = useFetch('/api/orders')
  const stock = useFetch('/api/inventory?filter=all&search=')
  const [customerId, setCustomerId] = useState(preselect || '')
  const [lines, setLines] = useState([{ productId: '', quantity: '1' }])
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState(null) // { ok, text }

  // Saathi worked out who the customer is: select them here too
  useEffect(() => { if (preselect) setCustomerId(preselect) }, [preselect])

  const products = stock.data?.items || []
  const setLine = (i, next) => setLines((all) => all.map((l, at) => (at === i ? { ...l, ...next } : l)))
  const valid = customerId && lines.every((l) => l.productId && Number(l.quantity) > 0)

  const save = async (event) => {
    event.preventDefault()
    if (!valid || saving) return
    setSaving(true)
    setMessage(null)
    try {
      const saved = await post('/api/orders', { customerId, lines: lines.map((l) => ({ productId: l.productId, quantity: Number(l.quantity) })) }, token)
      setMessage({ ok: true, text: `Order saved for ${saved.customerName}: ${saved.summary.join(', ')}.${saved.shortages.length ? ` Note: ${saved.shortages.join('; ')}.` : ''}` })
      setLines([{ productId: '', quantity: '1' }])
      refreshData()
    } catch (err) {
      setMessage({ ok: false, text: err.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <div className="grid grid-cols-1 gap-4 xl:grid-cols-[1fr_380px]">
      <div className="min-w-0 space-y-4">
        {draft && (
          <Card className="border-[#cfe0f7] !bg-[#eef4fd] p-4">
            <div className="flex items-center gap-2 text-[14px] font-bold text-[#1f5fbf]"><Mic size={16} /> Saathi is taking an order</div>
            <div className="mt-1 text-[14px]">{draft.customerName ? `For ${draft.customerName}` : 'Customer not chosen yet'}</div>
            {draft.lines.length > 0 ? (
              <ul className="mt-1 list-disc pl-5 text-[14px]">{draft.lines.map((l, i) => <li key={i}>{l}</li>)}</ul>
            ) : (
              <div className="mt-1 text-[13px] text-muted">Waiting for the items. Say, for example, "do Maggi aur ek kilo cheeni".</div>
            )}
            <div className="mt-1 text-[12.5px] text-muted">Say "haan" to save it, or "nahi" to change it.</div>
          </Card>
        )}

        <Card className="p-4">
          <div className="flex flex-wrap items-center gap-2">
            <ShoppingBag size={20} className="text-brand" />
            <h2 className="flex-1 text-[17px] font-bold">Take an order</h2>
            <button type="button" onClick={() => ask({ greet: true })} className="flex items-center gap-2 rounded-full border border-[#cfe9d8] bg-panel px-3 py-1.5 text-[13px] font-semibold hover:border-brand">
              <Mic size={15} className="text-brand" /> Say it instead
            </button>
          </div>

          <form onSubmit={save} className="mt-3 space-y-3">
            <label className="block text-[13px] font-semibold">
              Customer
              <select value={customerId} onChange={(e) => setCustomerId(e.target.value)} className="mt-1 h-11 w-full rounded-xl border border-line bg-white px-3 text-[14px] font-normal outline-none focus:border-brand">
                <option value="">Choose a customer</option>
                {customers.map((c) => <option key={c.customerId} value={c.customerId}>{c.name} ({c.phone})</option>)}
              </select>
            </label>

            <div className="space-y-2">
              <div className="text-[13px] font-semibold">Items</div>
              {lines.map((line, i) => (
                <div key={i} className="flex items-center gap-2">
                  <select value={line.productId} onChange={(e) => setLine(i, { productId: e.target.value })} aria-label="Product"
                    className="h-11 min-w-0 flex-1 rounded-xl border border-line bg-white px-3 text-[14px] outline-none focus:border-brand">
                    <option value="">Choose a product</option>
                    {products.map((p) => <option key={p.productId} value={p.productId}>{p.name} ({p.unit})</option>)}
                  </select>
                  <input type="number" min="0" step="any" value={line.quantity} onChange={(e) => setLine(i, { quantity: e.target.value })} aria-label="Quantity"
                    className="h-11 w-20 rounded-xl border border-line bg-white px-3 text-[14px] outline-none focus:border-brand" />
                  {lines.length > 1 && (
                    <button type="button" onClick={() => setLines((all) => all.filter((_, at) => at !== i))} aria-label="Remove item" className="grid h-11 w-11 place-items-center rounded-xl hover:bg-stone-100"><Trash2 size={16} /></button>
                  )}
                </div>
              ))}
              <button type="button" onClick={() => setLines((all) => [...all, { productId: '', quantity: '1' }])} className="flex items-center gap-1 text-[13px] font-semibold text-brand"><Plus size={14} /> Add another item</button>
            </div>

            {message && <p role="status" className={`text-[13px] ${message.ok ? 'text-brand' : 'text-red-600'}`}>{message.text}</p>}
            <button disabled={!valid || saving} className="h-11 rounded-xl bg-brand px-5 text-[14px] font-semibold text-white disabled:opacity-50">{saving ? 'Saving...' : 'Save order'}</button>
            <p className="text-[12px] text-muted">Saving an order does not reduce stock. Stock goes down when the goods are sold.</p>
          </form>
        </Card>
      </div>

      <Card className="h-fit p-4">
        <h2 className="text-[17px] font-bold">Recent orders</h2>
        {orders.error && <p className="mt-2 text-[13px] text-red-600">{orders.error}</p>}
        {!orders.data && !orders.error && <p className="mt-2 text-[13px] text-muted">Loading orders...</p>}
        {orders.data?.length === 0 && <p className="mt-2 text-[13px] text-muted">No orders yet.</p>}
        <ul className="mt-2 divide-y divide-edge">
          {orders.data?.map((o) => (
            <li key={o.orderId} className="py-2.5">
              <div className="flex items-center gap-2">
                <span className="min-w-0 flex-1 truncate text-[14px] font-semibold">{o.customerName || 'Walk-in'}</span>
                <span className={`rounded-md px-2 py-0.5 text-[11.5px] font-semibold ${STATUS[o.status] || STATUS.DRAFT}`}>{label(o.status)}</span>
              </div>
              <div className="text-[12.5px] text-muted">{dateText(o.createdAt)} · {o.orderId}{o.source === 'VOICE' ? ' · by voice' : ''}</div>
              <div className="mt-0.5 text-[13px]">{o.lines.map((l) => `${l.quantity} ${l.unit} ${l.name}`).join(', ') || 'No items'}</div>
            </li>
          ))}
        </ul>
      </Card>
    </div>
  )
}
