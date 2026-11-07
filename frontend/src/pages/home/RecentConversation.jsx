import { Mic } from 'lucide-react'
import { Card, Empty, ViewAll, shortDate } from '../../components/ui'

export default function RecentConversation({ messages }) {
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-[16px] font-bold">Recent Conversation</h3>
        <ViewAll to="/conversations" />
      </div>
      {messages.length === 0 && <Empty>No conversations yet.</Empty>}
      <ul className="mt-2 space-y-2.5">
        {messages.slice(0, 3).map((m, i) => (
          <li key={i} className="flex items-start gap-3 text-[13px]">
            <span className="grid h-7 w-7 shrink-0 place-items-center rounded-full bg-mint text-brand"><Mic size={14} /></span>
            <span className="line-clamp-2 flex-1 pt-0.5">{m.text}</span>
            <span className="shrink-0 pt-0.5 text-[11px] text-muted">{shortDate(m.date)}</span>
          </li>
        ))}
      </ul>
    </Card>
  )
}
