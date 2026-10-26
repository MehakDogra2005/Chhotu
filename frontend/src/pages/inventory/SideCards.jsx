import { ChartNoAxesColumnIncreasing, CirclePlus, ListChecks, Mic } from 'lucide-react'
import { Card } from '../../components/ui'

// Voice is not connected yet, so these are examples of what the shopkeeper will be able to say.
export function TalkToSaathi({ names }) {
  const first = names[0] || 'Maggi'
  const second = names[1] || first
  const examples = [
    `${first} ka stock kitna hai?`,
    `10 packet ${second} add karo`,
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
          <li key={text} className="flex items-center gap-2 rounded-full border border-[#d5ecdd] bg-white/80 px-3 py-2 text-[13px]">
            <Mic size={13} className="shrink-0 text-muted" /> “{text}”
          </li>
        ))}
      </ul>
    </div>
  )
}

const ACTIONS = [
  [Mic, 'Update Stock by Voice', 'bg-[#e3f5e8] text-brand'],
  [ListChecks, 'Generate Restocking List', 'bg-[#e3ecfb] text-[#2563c9]'],
  [CirclePlus, 'Add New Item', 'bg-[#fde8e5] text-red-500'],
  [ChartNoAxesColumnIncreasing, 'View Stock Report', 'bg-[#ece6fb] text-[#6d4bc8]'],
]

// None of these are built yet.
export function QuickActions() {
  return (
    <Card className="p-5">
      <h3 className="text-[16px] font-bold">Quick Actions</h3>
      <div className="mt-3 grid grid-cols-2 gap-3">
        {ACTIONS.map(([Icon, label, colours]) => (
          <button key={label} className={`flex h-24 flex-col items-center justify-center gap-2 rounded-xl px-2 text-center text-[13px] ${colours}`}>
            <Icon size={26} />
            <span className="text-[#1c2b24]">{label}</span>
          </button>
        ))}
      </div>
    </Card>
  )
}
