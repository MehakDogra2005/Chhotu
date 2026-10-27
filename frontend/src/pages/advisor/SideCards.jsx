import { ChartNoAxesColumnIncreasing, ClipboardList, Lightbulb, MessageCircleQuestion, Mic, Scale, Star, Tag, TrendingDown, TrendingUp, TriangleAlert, Zap } from 'lucide-react'
import { Card } from '../../components/ui'
import { useAssistant } from '../../components/AssistantContext'

// Questions Saathi can answer from the database (see assistant/CommandParser.java).
const QUESTIONS = [
  'Is mahine ka profit kitna hai?',
  'Kaun sa product sabse zyada bika?',
  'Kis product pe sabse zyada margin hai?',
  'Agle hafte ke liye kya stock mangwana chahiye?',
]

export function AskSaathi() {
  const { ask } = useAssistant()
  return (
    <Card className="bg-panel/60 p-4">
      <div className="flex items-center gap-3">
        <button onClick={() => ask({ listen: true })} aria-label="Ask Saathi by voice"
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand text-white hover:bg-brand-dark"><Mic size={20} /></button>
        <h3 className="text-[16px] font-bold">Ask Saathi about your business</h3>
      </div>
      <div className="mt-3 space-y-2">
        {QUESTIONS.map((q) => (
          <button key={q} onClick={() => ask({ text: q })}
            className="flex w-full items-center gap-2 rounded-full border border-edge bg-white px-3 py-2 text-left text-[13px] hover:border-brand">
            <MessageCircleQuestion size={15} className="shrink-0 text-muted" /> "{q}"
          </button>
        ))}
      </div>
    </Card>
  )
}

// The icon for each kind of advice the backend sends.
const KINDS = {
  up: [TrendingUp, 'bg-[#e1f5e8] text-brand'],
  down: [TrendingDown, 'bg-[#fde3e1] text-red-600'],
  warn: [TriangleAlert, 'bg-[#fff1dc] text-[#d97706]'],
  good: [ChartNoAxesColumnIncreasing, 'bg-[#e1f5e8] text-brand'],
  star: [Star, 'bg-[#e6effd] text-[#1f5fbf]'],
}

export function KeyInsights({ insights, periodLabel }) {
  return (
    <Card className="bg-[#fffaf0] p-4">
      <div className="flex items-center gap-2">
        <Lightbulb size={22} className="fill-[#fbd27a] text-[#e8a33a]" />
        <h3 className="flex-1 text-[16px] font-bold">Key Insights</h3>
        <span className="rounded-lg border border-edge bg-white px-2.5 py-1 text-[12px] text-muted">{periodLabel}</span>
      </div>
      <ul className="mt-3 space-y-3">
        {insights.map((item) => {
          const [Icon, tint] = KINDS[item.kind] || KINDS.star
          return (
            <li key={item.text} className="flex gap-3 text-[13px] leading-snug">
              <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${tint}`}><Icon size={16} /></span>
              <span className="pt-1">{item.text}</span>
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

export function QuickActions({ onProfitReport, onCompare, onPrices, onPurchasePlan }) {
  const actions = [
    { label: 'Get Profit Report by Voice', icon: Mic, tint: 'bg-[#e7f6e5] text-brand', onClick: onProfitReport },
    { label: 'Compare Product Margins', icon: Scale, tint: 'bg-[#e6effd] text-[#1f5fbf]', onClick: onCompare },
    { label: 'Suggest Price Changes', icon: Tag, tint: 'bg-[#fdebd0] text-orange', onClick: onPrices },
    { label: 'Generate Purchase Plan', icon: ClipboardList, tint: 'bg-[#efeafc] text-[#6250d6]', onClick: onPurchasePlan },
  ]
  return (
    <Card className="p-4">
      <div className="flex items-center gap-2">
        <Zap size={20} className="fill-[#fbd27a] text-[#e8a33a]" />
        <h3 className="text-[16px] font-bold">Quick Actions</h3>
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">
        {actions.map(({ label, icon: Icon, tint, onClick }) => (
          <button key={label} onClick={onClick} className={`flex flex-col items-center gap-2 rounded-xl px-2 py-4 text-center text-[13px] font-medium hover:brightness-95 ${tint}`}>
            <Icon size={22} /> <span className="text-ink">{label}</span>
          </button>
        ))}
      </div>
    </Card>
  )
}
