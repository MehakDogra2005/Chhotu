import { useState } from 'react'
import { ArrowRight, Mic, Phone, Search, Users } from 'lucide-react'
import { Card, CardHeader, Empty, rupees, shortDate } from '../../components/ui'

const TABS = [
  ['customers', 'Customers', Users],
  ['voice', 'Voice Notes', Mic],
  ['calls', 'Call Recordings', Phone],
]

export default function CustomerCard({ debtors, voiceNotes, callRecordings }) {
  const [tab, setTab] = useState('customers')
  const [search, setSearch] = useState('')
  const customers = debtors.filter((d) => d.name.toLowerCase().includes(search.toLowerCase()))
  const recordings = tab === 'voice' ? voiceNotes : callRecordings

  return (
    <Card className="flex flex-col p-5">
      <CardHeader icon={<Users size={22} />} tint="bg-[#fdebd0] text-orange" title="Customer Handling" note="(Udhaar Management)"
        subtitle="Manage customer credit using voice notes or call recordings." />

      <div className="mt-4 flex gap-5 border-b border-edge text-[12.5px]">
        {TABS.map(([key, label, Icon]) => (
          <button key={key} onClick={() => setTab(key)}
            className={`-mb-px flex items-center gap-1.5 border-b-2 pb-2 ${tab === key ? 'border-brand font-bold text-brand' : 'border-transparent text-muted'}`}>
            <Icon size={14} /> {label}
          </button>
        ))}
      </div>

      {tab === 'customers' ? (
        <>
          <label className="mt-3 flex items-center gap-2 rounded-lg border border-edge bg-white px-3 py-2 text-[12.5px]">
            <Search size={14} />
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search customer..." className="w-full outline-none" />
          </label>
          {customers.length === 0 ? <Empty>No customers with pending udhaar.</Empty> : (
            <table className="mt-3 w-full text-left text-[12.5px]">
              <thead>
                <tr className="bg-[#f6f1e6] text-[12px]"><th className="p-2">Name</th><th>Total Udhaar</th><th>Pending</th><th>Actions</th></tr>
              </thead>
              <tbody>
                {customers.map((c) => (
                  <tr key={c.name} className="border-b border-edge/60 last:border-0">
                    <td className="p-2 py-2.5">{c.name}</td>
                    <td>{rupees(c.totalCreditPaise)}</td>
                    <td className="font-semibold text-orange">{rupees(c.pendingPaise)}</td>
                    <td><button className="whitespace-nowrap rounded bg-[#e6effd] px-2 py-1 text-[11px] font-medium text-[#1f5fbf]">Record Payment</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      ) : recordings.length === 0 ? <Empty>No recordings uploaded yet.</Empty> : (
        <table className="mt-3 w-full text-left text-[12.5px]">
          <thead>
            <tr className="bg-[#f6f1e6] text-[12px]"><th className="p-2">File</th><th>Status</th><th>Uploaded</th></tr>
          </thead>
          <tbody>
            {recordings.map((r) => (
              <tr key={r.fileName} className="border-b border-edge/60 last:border-0">
                <td className="p-2 py-2.5">{r.fileName}</td>
                <td>{r.status.replace('_', ' ').toLowerCase()}</td>
                <td>{shortDate(r.uploadedOn)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* Voice payments are not built yet */}
      <button className="mt-auto flex h-13 w-full items-center gap-3 rounded-xl bg-[#e1f3e6] px-5 pt-0 text-[15px] font-semibold text-brand">
        <Mic size={20} /> <span className="flex-1 text-center">Add Payment by Voice</span> <ArrowRight size={18} />
      </button>
    </Card>
  )
}
