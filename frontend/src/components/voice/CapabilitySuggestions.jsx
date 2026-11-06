import { Box, ChartNoAxesColumnIncreasing, Users } from 'lucide-react'

// The three things Saathi helps with. `say` is the example sentence that is sent when the card is tapped.
const CAPABILITIES = [
  { icon: Box, title: 'Inventory', text: 'Stock, low items, restock list', say: 'Mujhe aaj ki inventory check karni hai' },
  { icon: ChartNoAxesColumnIncreasing, title: 'Business Advisor', text: 'Sales, profit, expenses', say: 'Kal ki kamai batao' },
  { icon: Users, title: 'Customers', text: 'Orders, udhaar, payments', say: 'Humein order lene hain' },
]

/** Three small cards under the introduction. Tapping one asks Saathi that example. */
export default function CapabilitySuggestions({ onPick, disabled }) {
  return (
    <div className="grid grid-cols-3 gap-2">
      {CAPABILITIES.map(({ icon: Icon, title, text, say }) => (
        <button key={title} type="button" disabled={disabled} onClick={() => onPick(say)} title={`Say: "${say}"`}
          className="flex flex-col items-start gap-1 rounded-xl border border-edge bg-white px-2.5 py-2 text-left hover:border-brand disabled:opacity-60">
          <Icon size={18} className="text-brand" />
          <span className="text-[12px] font-bold leading-tight text-heading">{title}</span>
          <span className="text-[11px] leading-snug text-muted">{text}</span>
        </button>
      ))}
    </div>
  )
}
