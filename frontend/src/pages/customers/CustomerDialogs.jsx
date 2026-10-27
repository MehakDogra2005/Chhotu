import { useEffect, useState } from 'react'
import { Banknote, Bell, ClipboardList, Copy, FileAudio, HandCoins, History, MessageCircle, MessageSquare, Mic, Send, UserPlus } from 'lucide-react'
import { post, put } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch, { refreshData } from '../../useFetch'
import Modal from '../../components/Modal'
import useSubmit from '../../components/useSubmit'
import { Field, inputStyle, rupees, shortDate, toPaise } from '../../components/ui'
import { useAssistant } from '../../components/AssistantContext'
import { Avatar, PendingPill, ago } from './CustomerTable'
import { KindPill, TransactionRows } from './BottomCards'
import { seconds } from './SideCards'

const saveButton = 'h-12 w-full rounded-xl bg-brand text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60'

export function AddCustomerDialog({ onClose }) {
  const { token } = useAuth()
  const { busy, error, run } = useSubmit(onClose)
  const [form, setForm] = useState({ name: '', phone: '', type: 'Regular', opening: '' })
  const field = (key) => ({ value: form[key], onChange: (e) => setForm({ ...form, [key]: e.target.value }), className: inputStyle })
  const submit = run(() => post('/api/customers', { name: form.name, phone: form.phone, type: form.type, openingPaise: toPaise(form.opening) }, token))

  return (
    <Modal title="Add Customer" icon={<UserPlus size={22} className="text-brand" />} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4 overflow-y-auto p-5">
        <Field label="Name"><input {...field('name')} placeholder="e.g. Ramesh Kumar" autoFocus /></Field>
        <Field label="Mobile number (optional)"><input {...field('phone')} type="tel" inputMode="numeric" placeholder="10 digits" /></Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Type">
            <select {...field('type')}>{['Regular', 'Retail', 'Wholesale'].map((t) => <option key={t}>{t}</option>)}</select>
          </Field>
          <Field label="Already owes you (₹, optional)"><input {...field('opening')} type="number" min="0" step="any" inputMode="decimal" placeholder="0" /></Field>
        </div>
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        <button disabled={busy || !form.name.trim()} className={saveButton}>{busy ? 'Saving...' : 'Add customer'}</button>
      </form>
    </Modal>
  )
}

/** "Give Udhaar": the customer took goods on credit. Due in 30 days. */
export function GiveCreditDialog({ customer, onClose }) {
  const { token } = useAuth()
  const { busy, error, run } = useSubmit(onClose)
  const [amount, setAmount] = useState('')
  const [note, setNote] = useState('')
  const submit = run(() => post(`/api/customers/${customer.customerId}/credit`, { amountPaise: toPaise(amount), note }, token))

  return (
    <Modal title="Give Udhaar" icon={<HandCoins size={22} className="text-orange" />} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4 p-5">
        <div className="rounded-xl bg-mint p-4">
          <div className="text-[15px] font-bold">{customer.name}</div>
          <div className="text-[13px] text-muted">Owes now: <b className="text-orange">{rupees(Math.max(customer.pendingPaise, 0))}</b></div>
        </div>
        <Field label="Amount (₹)"><input type="number" min="1" step="any" inputMode="decimal" autoFocus value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="e.g. 300" className={inputStyle} /></Field>
        <Field label="What did they take? (optional)"><input value={note} onChange={(e) => setNote(e.target.value)} maxLength={200} placeholder="e.g. Atta, oil" className={inputStyle} /></Field>
        <p className="text-[12.5px] text-muted">To be paid back within 30 days.</p>
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        <button disabled={busy || !amount} className={saveButton}>{busy ? 'Saving...' : 'Save udhaar'}</button>
      </form>
    </Modal>
  )
}

/** The ⋮ menu of a customer row. onPick gets the name of the action. */
export function CustomerMenu({ customer, onPick, onClose }) {
  const owes = customer.pendingPaise > 0
  const actions = [
    ['view', History, 'View history', ''],
    ...(owes ? [['pay', Banknote, 'Record payment', 'text-brand']] : []),
    ['credit', HandCoins, 'Give udhaar', 'text-orange'],
    ...(owes ? [['remind', Bell, 'Send payment reminder', 'text-[#1f5fbf]']] : []),
  ]
  return (
    <Modal title={customer.name} onClose={onClose}>
      <ul className="divide-y divide-edge">
        {actions.map(([name, Icon, label, colour]) => (
          <li key={name}>
            <button onClick={() => onPick(name)} className={`flex w-full items-center gap-3 px-5 py-4 text-left text-[15px] font-medium hover:bg-stone-50 ${colour}`}>
              <Icon size={20} /> {label}
            </button>
          </li>
        ))}
      </ul>
    </Modal>
  )
}

/** One customer: what they owe, and every entry with the balance after it. */
export function CustomerDetailDialog({ customer, today, onPick, onClose }) {
  const { data, error } = useFetch(`/api/customers/${customer.customerId}/history`)
  const c = customer
  return (
    <Modal title="Customer details" icon={<Avatar name={c.name} />} onClose={onClose}>
      <div className="space-y-4 overflow-y-auto p-5">
        <div>
          <div className="text-[18px] font-bold">{c.name}</div>
          <div className="text-[13px] text-muted">{[c.phone, c.type].filter(Boolean).join(' · ')}</div>
        </div>
        <div className="grid grid-cols-3 gap-2 text-center">
          {[['Total udhaar', rupees(c.totalCreditPaise), ''], ['Paid', rupees(c.paidPaise), 'text-brand'], ['Pending', <PendingPill key="p" customer={c} />, '']].map(([label, value, tone]) => (
            <div key={label} className="rounded-xl bg-white p-3">
              <div className="text-[11.5px] text-muted">{label}</div>
              <div className={`mt-1 text-[16px] font-bold ${tone}`}>{value}</div>
            </div>
          ))}
        </div>
        {(c.overduePaise > 0 || c.waivedPaise > 0 || c.lastReminded) && (
          <ul className="space-y-1 text-[12.5px]">
            {c.overduePaise > 0 && <li className="text-red-600">{rupees(c.overduePaise)} is overdue ({c.daysOverdue} days late).</li>}
            {c.waivedPaise > 0 && <li className="text-muted">{rupees(c.waivedPaise)} was waived (round-offs, discounts).</li>}
            {c.lastReminded && <li className="text-muted">Last reminder sent {ago(c.lastReminded, today).toLowerCase()}.</li>}
          </ul>
        )}
        <div className="grid grid-cols-3 gap-2">
          <button disabled={c.pendingPaise <= 0} onClick={() => onPick('pay')} className="rounded-lg bg-[#e1f5e8] py-2.5 text-[13px] font-semibold text-brand disabled:opacity-40">Record payment</button>
          <button onClick={() => onPick('credit')} className="rounded-lg bg-[#fdebd0] py-2.5 text-[13px] font-semibold text-[#b4501f]">Give udhaar</button>
          <button disabled={c.pendingPaise <= 0} onClick={() => onPick('remind')} className="rounded-lg bg-[#e6effd] py-2.5 text-[13px] font-semibold text-[#1f5fbf] disabled:opacity-40">Remind</button>
        </div>
        <div>
          <h3 className="mb-2 text-[14px] font-bold">History</h3>
          {error && <p className="text-[13px] text-red-600">{error}</p>}
          {!data && !error && <p className="text-[13px] text-muted">Loading...</p>}
          {data && data.lines.length === 0 && <p className="text-[13px] text-muted">No entries yet.</p>}
          {data && data.lines.length > 0 && (
            <ul className="divide-y divide-edge/60 rounded-xl bg-white">
              {data.lines.map((l) => (
                <li key={l.id} className="flex items-start gap-3 p-3 text-[12.5px]">
                  <div className="w-16 shrink-0 text-muted">{shortDate(l.date)}</div>
                  <div className="min-w-0 flex-1">
                    <KindPill kind={l.kind} />
                    {l.note && <div className="mt-1 truncate text-muted" title={l.note}>{l.note}</div>}
                    {l.dueDate && <div className="text-[11px] text-muted">Due {shortDate(l.dueDate)}</div>}
                  </div>
                  <div className="text-right">
                    <div className="font-semibold">{l.kind === 'CREDIT' || l.kind === 'PAYMENT_REVERSED' ? '+' : '−'}{rupees(l.amountPaise)}</div>
                    <div className="text-[11px] text-muted">owes {rupees(l.balanceAfterPaise)}</div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </Modal>
  )
}

const reminderText = (c, shopName) => `Namaste ${c.name}, aapke ${rupees(c.pendingPaise)} baaki hain. Kripya jaldi bhugtan karein. - ${shopName}`
/** Only a full 10-digit number can be messaged directly; masked sample numbers open WhatsApp to pick the contact. */
const fullNumber = (phone) => (/^[6-9]\d{9}$/.test(phone || '') ? phone : null)

function ReminderRow({ customer, shopName, today }) {
  const { token } = useAuth()
  const [text, setText] = useState(reminderText(customer, shopName))
  const [state, setState] = useState('') // '' | 'sent' | 'copied' | error message
  const number = fullNumber(customer.phone)

  const send = async (channel) => {
    const url = channel === 'WHATSAPP'
      ? `https://wa.me/${number ? '91' + number : ''}?text=${encodeURIComponent(text)}`
      : `sms:${number || ''}?body=${encodeURIComponent(text)}`
    window.open(url, '_blank', 'noopener')
    try {
      await post(`/api/customers/${customer.customerId}/reminders`, { channel, message: text }, token)
      setState('sent')
      refreshData()
    } catch (err) {
      setState(err.message)
    }
  }
  const copy = async () => {
    await navigator.clipboard.writeText(text).catch(() => {})
    setState('copied')
  }

  return (
    <li className="space-y-2 rounded-xl bg-white p-3">
      <div className="flex items-center gap-3">
        <Avatar name={customer.name} size="h-8 w-8 text-[12px]" />
        <div className="min-w-0 flex-1">
          <div className="text-[14px] font-semibold">{customer.name}</div>
          <div className="text-[12px] text-muted">
            Owes {rupees(customer.pendingPaise)}{customer.overduePaise > 0 && <span className="text-red-600"> · {rupees(customer.overduePaise)} overdue ({customer.daysOverdue} days)</span>}
            {customer.lastReminded && ` · reminded ${ago(customer.lastReminded, today).toLowerCase()}`}
          </div>
        </div>
      </div>
      <textarea value={text} onChange={(e) => setText(e.target.value)} rows={2} maxLength={1000} aria-label={`Reminder for ${customer.name}`}
        className="w-full rounded-lg border border-line px-3 py-2 text-[12.5px] outline-none focus:border-brand" />
      <div className="flex flex-wrap items-center gap-2">
        <button onClick={() => send('WHATSAPP')} className="flex items-center gap-1.5 rounded-lg bg-[#25a35a] px-3 py-1.5 text-[12.5px] font-semibold text-white hover:brightness-95"><MessageCircle size={14} /> WhatsApp</button>
        <button onClick={() => send('SMS')} className="flex items-center gap-1.5 rounded-lg bg-[#e6effd] px-3 py-1.5 text-[12.5px] font-semibold text-[#1f5fbf]"><MessageSquare size={14} /> SMS</button>
        <button onClick={copy} className="flex items-center gap-1.5 rounded-lg border border-edge px-3 py-1.5 text-[12.5px] font-semibold"><Copy size={14} /> Copy</button>
        {state === 'sent' && <span className="text-[12px] font-semibold text-brand">Noted as sent</span>}
        {state === 'copied' && <span className="text-[12px] text-muted">Copied</span>}
        {state && state !== 'sent' && state !== 'copied' && <span className="text-[12px] text-red-600">{state}</span>}
      </div>
      {!number && <p className="text-[11px] text-muted">The full mobile number is not saved, so WhatsApp will ask you to pick the contact.</p>}
    </li>
  )
}

/** Reminder messages for everyone overdue, or for one customer. Sending opens WhatsApp / SMS on this device. */
export function RemindersDialog({ customers, shopName, today, onClose }) {
  const [showAll, setShowAll] = useState(false)
  const list = showAll ? customers : customers.slice(0, 5)
  return (
    <Modal title="Send payment reminders" icon={<Send size={22} className="text-[#1f5fbf]" />} onClose={onClose}>
      <div className="space-y-3 overflow-y-auto p-5">
        {customers.length === 0 ? <p className="text-center text-[13px] text-muted">Nobody's payment is overdue.</p> : (
          <>
            <p className="text-[13px] text-muted">{customers.length === 1 ? 'Edit the message if you like, then send it.' : `${customers.length} customers are overdue, biggest amount first. Edit any message, then send it.`}</p>
            <ul className="space-y-3">{list.map((c) => <ReminderRow key={c.customerId} customer={c} shopName={shopName} today={today} />)}</ul>
            {customers.length > 5 && (
              <button onClick={() => setShowAll(!showAll)} className="w-full rounded-xl border border-edge py-2 text-[13px] font-semibold text-brand">
                {showAll ? 'Show fewer' : `Show all ${customers.length}`}
              </button>
            )}
          </>
        )}
      </div>
    </Modal>
  )
}

export function TransactionsDialog({ transactions, today, onClose }) {
  return (
    <Modal title="Recent transactions" icon={<ClipboardList size={22} className="text-orange" />} onClose={onClose}>
      <div className="overflow-y-auto p-4"><TransactionRows transactions={transactions} today={today} /></div>
    </Modal>
  )
}

/** A recording: listen to it (when the audio is on this server), read or type what was said, and send it to Saathi. */
export function RecordingDialog({ recording, onClose }) {
  const { token } = useAuth()
  const { ask } = useAssistant()
  const [audioUrl, setAudioUrl] = useState(null)
  const [audioError, setAudioError] = useState('')
  const [text, setText] = useState(recording.transcript || '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!recording.hasAudio) return undefined
    let url
    fetch(`/api/recordings/${recording.recordingId}/audio`, { headers: { Authorization: `Bearer ${token}` } })
      .then((res) => (res.ok ? res.blob() : Promise.reject(new Error('Could not load the audio'))))
      .then((blob) => { url = URL.createObjectURL(blob); setAudioUrl(url) })
      .catch((err) => setAudioError(err.message))
    return () => url && URL.revokeObjectURL(url)
  }, [recording.recordingId, recording.hasAudio, token])

  const save = async () => {
    setSaving(true)
    setError('')
    try {
      await put(`/api/recordings/${recording.recordingId}/transcript`, { text }, token)
      refreshData()
      setSaving(false)
      return true
    } catch (err) {
      setError(err.message)
      setSaving(false)
      return false
    }
  }
  const sendToSaathi = async () => {
    if (text.trim() !== (recording.transcript || '') && !(await save())) return
    onClose()
    ask({ text: text.trim() })
  }

  return (
    <Modal title={recording.fileName} icon={<FileAudio size={22} className="text-brand" />} onClose={onClose}>
      <div className="space-y-4 overflow-y-auto p-5 text-[13px]">
        <p className="text-muted">
          {recording.type === 'CALL_RECORDING' ? 'Call recording' : 'Voice note'} · {shortDate(recording.uploadedAt.slice(0, 10))}
          {recording.durationSeconds != null && ` · ${seconds(recording.durationSeconds)}`}
        </p>
        {recording.hasAudio
          ? (audioUrl ? <audio controls src={audioUrl} className="w-full" /> : <p className="text-muted">{audioError || 'Loading audio...'}</p>)
          : <p className="rounded-lg bg-white p-3 text-muted">This is a sample recording; its audio is not stored on this server.</p>}
        <Field label="What was said">
          <textarea value={text} onChange={(e) => setText(e.target.value)} rows={4} maxLength={1000}
            placeholder='Type it, e.g. "Ramesh ne 500 rupaye de diye"' className="w-full rounded-xl border border-line bg-white px-4 py-3 text-[14px] outline-none focus:border-brand" />
        </Field>
        <p className="text-[12px] text-muted">Saathi reads one change at a time: for example "Ramesh ne 500 de diye". A promise to pay later is not a payment, so do not record it as one.</p>
        {error && <p className="text-red-600">{error}</p>}
        <div className="grid grid-cols-2 gap-2">
          <button onClick={save} disabled={saving || !text.trim()} className="h-11 rounded-xl border border-edge font-semibold disabled:opacity-50">{saving ? 'Saving...' : 'Save text'}</button>
          <button onClick={sendToSaathi} disabled={saving || !text.trim()} className="flex h-11 items-center justify-center gap-2 rounded-xl bg-brand font-semibold text-white disabled:opacity-50"><Mic size={16} /> Send to Saathi</button>
        </div>
      </div>
    </Modal>
  )
}
