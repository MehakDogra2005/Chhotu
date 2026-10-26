import { Box, ChartNoAxesColumnIncreasing, Lightbulb, Mic, Users } from 'lucide-react'
import { useAssistant } from '../../components/AssistantContext'

// Bar heights (px) for the sound waves either side of the mic. The waves are left out on a narrow phone screen.
const LEFT = [8, 16, 28, 44, 30, 52, 38, 60, 44, 56, 34, 46, 24, 32, 18]
const RIGHT = [14, 26, 20, 40, 54, 38, 62, 46, 56, 36, 48, 28, 36, 20, 12]

const Wave = ({ heights }) => (
  <div className="hidden items-center gap-1.25 sm:flex">
    {heights.map((h, i) => <span key={i} style={{ height: h }} className="w-0.75 rounded-full bg-[#7fd0a0]" />)}
  </div>
)

// The big mic starts listening; each sentence below is sent to Saathi when tapped.
export default function VoicePanel({ firstProduct, firstCustomer }) {
  const { ask } = useAssistant()
  const examples = [
    [Box, `${firstProduct || 'Chocolate'} ka stock kitna bacha hai?`],
    [ChartNoAxesColumnIncreasing, 'Aaj ki total sale batao'],
    [Users, `${firstCustomer || 'Ramesh'} ka udhaar kitna hai?`],
    [Lightbulb, 'Restocking list bana do'],
  ]

  return (
    <div className="rounded-2xl border border-[#d5ecdd] bg-linear-to-b from-[#eaf8ef] to-[#f3fbf5] px-4 pb-5 pt-6 text-center sm:px-6">
      <h2 className="text-[18px] font-bold sm:text-[22px]">“Boliye, aapke liye kya karna hai?”</h2>
      <p className="mt-1 text-[16px] text-muted">Tap the mic and speak</p>

      <div className="my-6 flex items-center justify-center gap-6">
        <Wave heights={LEFT} />
        <div className="grid h-36 w-36 place-items-center rounded-full bg-brand/10 sm:h-44 sm:w-44">
          <div className="grid h-28 w-28 place-items-center rounded-full bg-brand/15 sm:h-32 sm:w-32">
            <button onClick={() => ask({ listen: true })} aria-label="Speak to Saathi" className="grid h-20 w-20 place-items-center rounded-full bg-brand text-white shadow-lg hover:bg-brand-dark sm:h-24 sm:w-24"><Mic size={36} /></button>
          </div>
        </div>
        <Wave heights={RIGHT} />
      </div>

      <div className="grid grid-cols-1 gap-3 min-[480px]:grid-cols-2 2xl:grid-cols-4">
        {examples.map(([Icon, text]) => (
          <button key={text} onClick={() => ask({ text })} className="flex items-center gap-3 rounded-full bg-[#dff3e5] px-4 py-3 text-left text-[13px] leading-snug hover:bg-[#cdebd6]">
            <Icon size={22} className="shrink-0 text-brand" /> {text}
          </button>
        ))}
      </div>
    </div>
  )
}
