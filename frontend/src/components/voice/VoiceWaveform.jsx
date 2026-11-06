const HEIGHTS = [10, 18, 26, 14, 30, 20, 12, 24, 16]

/** A few bars that rise and fall. `mode` is 'listening' (quicker, red), 'speaking' (slower, green) or anything else (still). */
export default function VoiceWaveform({ mode }) {
  const live = mode === 'listening' || mode === 'speaking'
  const colour = mode === 'listening' ? 'bg-red-500' : 'bg-brand'
  return (
    <div className="flex h-8 items-center justify-center gap-[3px]" aria-hidden="true">
      {HEIGHTS.map((h, i) => (
        <span key={i} style={{ height: h, animationDelay: `${i * 90}ms` }}
          className={`w-[3px] rounded-full ${live ? `saathi-bar ${mode === 'listening' ? 'listening' : ''} ${colour}` : 'bg-stone-300'}`} />
      ))}
    </div>
  )
}
