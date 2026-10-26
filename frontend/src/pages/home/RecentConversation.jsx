import { Mic } from 'lucide-react'
import { Card, Empty, ViewAll, shortDate } from '../../components/ui'

export default function RecentConversation({ messages }) {
  return (
    <Card className="p-5">
      <div className="flex items-center justify-between">
        <h3 className="text-[16px] font-bold">Recent Conversation</h3>
        <ViewAll />
      </div>
      {messages.length === 0 && <Empty>No conversations yet.</Empty>}
      <ul className="mt-3 space-y-4">
        {messages.map((m, i) => (
          <li key={i} className="flex items-start gap-3 text-[13px]">
            <span className="grid h-8 w-8 shrink-0 place-items-center rounded-full bg-mint text-brand"><Mic size={16} /></span>
            <span className="flex-1 pt-1">{m.text}</span>
            <span className="shrink-0 pt-1 text-[11px] text-muted">{shortDate(m.date)}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}
