import { ChartNoAxesColumnIncreasing, CirclePlus, ListChecks, Mic } from 'lucide-react'
import { useAssistant } from '../../components/AssistantContext'
import { Card } from '../../components/ui'

/** Sentences to try. Tapping one sends it to Saathi. names are products of this shop. */
export function TalkToSaathi({ names }) {
  const { ask } = useAssistant()
  const first = names[0] || 'Chocolate'
  const second = names[1] || first
  const examples = [
    `${first} ka stock kitna hai?`,
    `10 ${second} add karo`,
    'Kaun sa samaan khatam hone wala hai?',
    'Restocking list bana do',
  ]

  return (
    <div className="rounded-2xl border border-[#d5ecdd] bg-linear-to-b from-[#eaf8ef] to-[#f4fbf6] p-5">
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 place-items-center rounded-full bg-white text-brand shadow-sm"><Mic size={20} /></span>
        <h3 className="text-[16px] font-bold leading-snug">Talk to Saathi about Inventory</h3>
      </div>
      <ul className="mt-4 space-y-2">
        {examples.map((text) => (
          <li key={text}>
            <button onClick={() => ask({ text })} className="flex w-full items-center gap-2 rounded-full border border-[#d5ecdd] bg-white/80 px-3 py-2 text-left text-[13px] hover:border-brand">
              <Mic size={13} className="shrink-0 text-muted" /> “{text}”
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** The four big buttons. Each one is handled by the Inventory page. */
export function QuickActions({ onVoice, onRestock, onAdd, onReport }) {
  const actions = [
    [Mic, 'Update Stock by Voice', 'bg-[#e3f5e8] text-brand', onVoice],
    [ListChecks, 'Generate Restocking List', 'bg-[#e3ecfb] text-[#2563c9]', onRestock],
    [CirclePlus, 'Add New Item', 'bg-[#fde8e5] text-red-500', onAdd],
    [ChartNoAxesColumnIncreasing, 'View Stock Report', 'bg-[#ece6fb] text-[#6d4bc8]', onReport],
  ]

  return (
    <Card className="p-5">
      <h3 className="text-[16px] font-bold">Quick Actions</h3>
      <div className="mt-3 grid grid-cols-2 gap-3">
        {actions.map(([Icon, label, colours, onClick]) => (
          <button key={label} onClick={onClick} className={`flex h-24 flex-col items-center justify-center gap-2 rounded-xl px-2 text-center text-[13px] hover:opacity-80 ${colours}`}>
            <Icon size={26} />
            <span className="text-[#1c2b24]">{label}</span>
          </button>
        ))}
      </div>
    </Card>
  )
}
