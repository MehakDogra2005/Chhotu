import { MessageSquare, Mic } from 'lucide-react'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import { Card, Empty } from '../../components/ui'

// What the shop owner sees for the status the backend saved with each sentence.
const STATUS = {
  CONFIRMED: ['Done', 'bg-[#e1f5e8] text-brand'],
  PENDING: ['Waiting for you', 'bg-[#fff1dc] text-[#d97706]'],
  REJECTED: ['Cancelled', 'bg-stone-100 text-muted'],
}

/** Everything the shopkeeper has said to Saathi, newest first. */
export default function Conversations() {
  const { data, error } = useFetch('/api/assistant/history?limit=100')

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!data) return <p className="p-10 text-center text-muted">Loading...</p>

  return (
    <>
      <PageHeader icon={<MessageSquare size={44} strokeWidth={1.4} className="text-brand" />}
        title="Conversations" subtitle="Everything you have asked or told Saathi." />
      <Card className="mt-5 p-2 sm:p-4">
        {data.length === 0 && <Empty>Nothing yet. Tap the mic and say something.</Empty>}
        <ul className="divide-y divide-edge">
          {data.map((m, i) => {
            const status = STATUS[m.status]
            return (
              <li key={i} className="flex items-center gap-3 px-2 py-3 text-[14px]">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-mint text-brand"><Mic size={16} /></span>
                <span className="min-w-0 flex-1 break-words">{m.text}</span>
                {status && <span className={`shrink-0 rounded-md px-2.5 py-1 text-[12px] ${status[1]}`}>{status[0]}</span>}
                <span className="hidden w-16 shrink-0 text-right text-[12px] text-muted sm:block">{new Date(m.date + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</span>
              </li>
            )
          })}
        </ul>
      </Card>
    </>
  )
}
