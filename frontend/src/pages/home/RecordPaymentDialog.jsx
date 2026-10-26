import { useState } from 'react'
import { Banknote } from 'lucide-react'
import { post } from '../../api'
import { useAuth } from '../../AuthContext'
import { refreshData } from '../../useFetch'
import Modal from '../../components/Modal'
import { inputStyle, rupees } from '../../components/ui'

/** "Record Payment": the customer paid some of what they owe. customer comes from the backend (id, name, pending). */
export default function RecordPaymentDialog({ customer, onClose }) {
  const { token } = useAuth()
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const save = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await post(`/api/customers/${customer.customerId}/payments`, { amountPaise: Math.round(Number(amount) * 100) }, token)
      refreshData()
      onClose()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return (
    <Modal title="Record Payment" icon={<Banknote size={22} className="text-brand" />} onClose={onClose}>
      <form onSubmit={save} className="space-y-4 p-5">
        <div className="rounded-xl bg-mint p-4">
          <div className="text-[15px] font-bold">{customer.name}</div>
          <div className="text-[13px] text-muted">Pending udhaar: <b className="text-orange">{rupees(customer.pendingPaise)}</b></div>
        </div>
        <label className="block">
          <span className="mb-1.5 block text-[13px] font-semibold">Amount received (₹)</span>
          <input type="number" min="1" step="any" inputMode="decimal" autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 500" className={inputStyle} />
        </label>
        <button type="button" onClick={() => setAmount(String(customer.pendingPaise / 100))} className="text-[13px] font-semibold text-brand">Customer paid everything ({rupees(customer.pendingPaise)})</button>
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        <button disabled={busy || !amount} className="h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">
          {busy ? 'Saving...' : 'Save payment'}
        </button>
      </form>
    </Modal>
  )
}
