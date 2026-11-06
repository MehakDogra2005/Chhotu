import { STATE_INFO } from './voiceStates'

/** The one-line status under the title: a coloured dot and what Saathi is doing. */
export default function VoiceStateIndicator({ state }) {
  const info = STATE_INFO[state]
  return (
    <div className="flex items-center gap-1.5 text-[12px] text-muted" role="status" aria-live="polite">
      <span className={`h-2 w-2 rounded-full ${info.tone}`} />
      {info.label}
    </div>
  )
}
