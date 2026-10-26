import { ChevronDown, CircleHelp, Mic } from 'lucide-react'
import PageHeader from '../../components/PageHeader'
import { Card } from '../../components/ui'
import { FAQ, STEPS, VOICE_GROUPS } from './helpContent'

export default function Help() {
  return (
    <>
      <PageHeader icon={<CircleHelp size={46} strokeWidth={1.4} className="text-[#2563c9]" />}
        title="Help" subtitle="Quick answers, and what you can ask Saathi." />

      <h2 className="mb-3 mt-6 text-[18px] font-bold">Getting started</h2>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 2xl:grid-cols-4">
        {STEPS.map(([title, text], i) => (
          <Card key={title} className="p-5">
            <span className="grid h-9 w-9 place-items-center rounded-full bg-brand text-[15px] font-bold text-white">{i + 1}</span>
            <h3 className="mt-3 text-[15px] font-bold">{title}</h3>
            <p className="mt-1 text-[13px] leading-relaxed text-muted">{text}</p>
          </Card>
        ))}
      </div>

      <div className="mt-6 grid grid-cols-1 gap-4 xl:grid-cols-[400px_1fr]">
        <div className="self-start rounded-2xl border border-[#d5ecdd] bg-linear-to-b from-[#eaf8ef] to-[#f4fbf6] p-5">
          <div className="flex items-center gap-3">
            <span className="grid h-10 w-10 place-items-center rounded-full bg-white text-brand shadow-sm"><Mic size={20} /></span>
            <h2 className="flex-1 text-[16px] font-bold">What you can say to Saathi</h2>
            <span className="rounded-full bg-white px-3 py-1 text-[11px] font-semibold text-orange">Coming soon</span>
          </div>
          <div className="mt-4 space-y-4">
            {VOICE_GROUPS.map(([group, examples]) => (
              <div key={group}>
                <h3 className="mb-2 text-[13px] font-bold text-brand">{group}</h3>
                <ul className="space-y-2">
                  {examples.map((text) => (
                    <li key={text} className="rounded-full border border-[#d5ecdd] bg-white/80 px-4 py-2 text-[13px]">“{text}”</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </div>

        <div>
          <h2 className="mb-3 text-[18px] font-bold">Common questions</h2>
          <div className="space-y-3">
            {FAQ.map(([question, answer]) => (
              <details key={question} className="group rounded-2xl border border-edge bg-white/80 shadow-[0_1px_4px_rgba(0,0,0,0.04)]">
                <summary className="flex cursor-pointer list-none items-center gap-3 p-4 text-[15px] font-semibold">
                  <span className="flex-1">{question}</span>
                  <ChevronDown size={18} className="shrink-0 text-muted transition group-open:rotate-180" />
                </summary>
                <p className="px-4 pb-4 text-[14px] leading-relaxed text-muted">{answer}</p>
              </details>
            ))}
          </div>
        </div>
      </div>
    </>
  )
}
