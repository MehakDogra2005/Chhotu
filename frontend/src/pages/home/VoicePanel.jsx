import { Box, ChartNoAxesColumnIncreasing, Lightbulb, Mic, Users } from 'lucide-react'

// Bar heights (px) for the sound waves either side of the mic.
const LEFT = [8, 16, 28, 44, 30, 52, 38, 60, 44, 56, 34, 46, 24, 32, 18]
const RIGHT = [14, 26, 20, 40, 54, 38, 62, 46, 56, 36, 48, 28, 36, 20, 12]

const Wave = ({ heights }) => (
  <div className="flex items-center gap-1.25">
    {heights.map((h, i) => <span key={i} style={{ height: h }} className="w-0.75 rounded-full bg-[#7fd0a0]" />)}
  </div>
)

// The voice assistant is not connected yet, so this panel is the design only.
export default function VoicePanel({ firstProduct }) {
  const examples = [
    [Box, `${firstProduct || 'Maggi'} ka stock kitna bacha hai?`],
    [ChartNoAxesColumnIncreasing, 'Aaj ki total sale batao'],
    [Users, 'Ramesh ka udhaar update karo'],
    [Lightbulb, 'Restocking list bana do'],
  ]

  return (
    <div className="rounded-2xl border border-[#d5ecdd] bg-linear-to-b from-[#eaf8ef] to-[#f3fbf5] px-6 pb-5 pt-6 text-center">
      <h2 className="text-[22px] font-bold">“Boliye, aapke liye kya karna hai?”</h2>
      <p className="mt-1 text-[16px] text-muted">I'm listening...</p>

      <div className="my-6 flex items-center justify-center gap-6">
        <Wave heights={LEFT} />
        <div className="grid h-44 w-44 place-items-center rounded-full bg-brand/10">
          <div className="grid h-32 w-32 place-items-center rounded-full bg-brand/15">
            <div className="grid h-24 w-24 place-items-center rounded-full bg-brand text-white shadow-lg"><Mic size={40} /></div>
          </div>
        </div>
        <Wave heights={RIGHT} />
      </div>

      <div className="grid grid-cols-2 gap-3 2xl:grid-cols-4">
        {examples.map(([Icon, text]) => (
          <div key={text} className="flex items-center gap-3 rounded-full bg-[#dff3e5] px-4 py-3 text-left text-[13px] leading-snug">
            <Icon size={22} className="shrink-0 text-brand" /> {text}
          </div>
        ))}
      </div>
    </div>
  )
}
