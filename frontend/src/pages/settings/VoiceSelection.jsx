import { useEffect, useRef, useState } from 'react'
import { AudioLines, CircleCheck, Loader2, Play, Square } from 'lucide-react'
import { put } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import { Card, CardHeader } from '../../components/ui'
import { stopSpeaking } from '../../components/speech'

/**
 * Choose the voice Munim Ji speaks with. The voices are Gnani's Timbre v3.0 voices (the list comes from the server). Picking a card
 * saves it at once on your profile, and the server uses it for every spoken reply in every part of the app, so there is nothing
 * to press "Save" for. Play Sample reads one fixed sentence in that voice without choosing it.
 */
export default function VoiceSelection() {
  const { token } = useAuth()
  const { data, error } = useFetch('/api/voice/voices')
  const [selected, setSelected] = useState(null)
  const [playing, setPlaying] = useState(null)   // the voice whose sample is playing or loading
  const [loading, setLoading] = useState(false)
  const [status, setStatus] = useState(null)     // { ok, text }
  const audio = useRef(null)
  const request = useRef(0)                      // a newer Play press makes an older sample out of date

  useEffect(() => { if (data) setSelected(data.selected) }, [data])
  useEffect(() => () => stopSample(), []) // leaving the page stops the sample

  const stopSample = () => {
    request.current++
    audio.current?.pause()
    audio.current = null
    setPlaying(null)
    setLoading(false)
  }

  const choose = async (name) => {
    if (name === selected) return
    const before = selected
    setSelected(name) // shown at once; put back if saving fails
    setStatus(null)
    try {
      const saved = await put('/api/voice/voice', { voice: name }, token)
      setSelected(saved.selected)
      setStatus({ ok: true, text: `${saved.selected} is now Munim Ji's voice. It will be used for every reply, in every section.` })
    } catch (err) {
      setSelected(before)
      setStatus({ ok: false, text: err.message })
    }
  }

  const play = async (name) => {
    if (playing === name) return stopSample()
    stopSample()
    stopSpeaking() // Munim Ji's own reply must not talk over the sample
    const mine = request.current
    setPlaying(name)
    setLoading(true)
    setStatus(null)
    try {
      const res = await fetch('/api/voice/sample', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ voice: name }),
      }).catch(() => { throw new Error('Cannot reach Galla. Please check your internet connection.') })
      if (!res.ok) throw new Error((await res.json().catch(() => ({}))).message || 'The sample could not be played.')
      const url = URL.createObjectURL(await res.blob())
      if (mine !== request.current) return URL.revokeObjectURL(url)
      const sound = new Audio(url)
      audio.current = sound
      sound.onended = () => { URL.revokeObjectURL(url); if (mine === request.current) stopSample() }
      sound.onerror = () => { if (mine === request.current) { stopSample(); setStatus({ ok: false, text: 'The sample could not be played.' }) } }
      setLoading(false)
      await sound.play()
    } catch (err) {
      if (mine === request.current) {
        stopSample()
        setStatus({ ok: false, text: err.message })
      }
    }
    return undefined
  }

  return (
    <Card className="p-5">
      <CardHeader icon={<AudioLines size={22} />} tint="bg-[#efeafc] text-[#6250d6]"
        title="Voice Selection" subtitle="Choose the voice of Munim Ji. It speaks every reply in the voice you pick." />

      {error && <p className="mt-4 text-[13px] text-red-600">{error}</p>}
      {!data && !error && <p className="mt-4 text-[13px] text-muted">Loading voices...</p>}
      {data && !data.gnani && (
        <p className="mt-4 rounded-xl bg-warm px-3 py-2 text-[13px] text-[#8a5a00]">
          Gnani speech is not set up on this server, so Munim Ji is using your browser's own voice for now. You can still choose a voice: it is saved and
          used as soon as Gnani is switched on. Samples need Gnani.
        </p>
      )}

      {data && (
        <div role="radiogroup" aria-label="Munim Ji's voice" className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
          {data.voices.map((v) => {
            const on = v.name === selected
            const thisOne = playing === v.name
            return (
              <div key={v.name} className={`flex items-stretch overflow-hidden rounded-2xl border ${on ? 'border-brand bg-mint' : 'border-edge bg-white hover:border-brand'}`}>
                <button type="button" role="radio" aria-checked={on} onClick={() => choose(v.name)} className="flex min-w-0 flex-1 items-center gap-3 px-3 py-3 text-left">
                  <span className={`grid h-6 w-6 shrink-0 place-items-center rounded-full border-2 ${on ? 'border-brand bg-brand text-white' : 'border-[#cfc6b3]'}`}>
                    {on && <CircleCheck size={16} strokeWidth={3} />}
                  </span>
                  <span className="min-w-0">
                    <span className="flex items-center gap-2 text-[15px] font-bold">
                      {v.name}
                      {on && <span className="rounded-md bg-brand px-2 py-0.5 text-[11px] font-semibold text-white">Selected</span>}
                    </span>
                    <span className="block text-[12.5px] leading-snug text-muted">{v.gender} · {v.persona}</span>
                  </span>
                </button>
                <button type="button" onClick={() => play(v.name)} disabled={!data.gnani} aria-label={`${thisOne ? 'Stop' : 'Play sample of'} ${v.name}`}
                  className="flex shrink-0 items-center gap-1.5 border-l border-edge px-3 text-[13px] font-semibold text-brand hover:bg-white disabled:text-muted disabled:opacity-60">
                  {thisOne && loading ? <Loader2 size={15} className="animate-spin" /> : thisOne ? <Square size={14} /> : <Play size={15} />}
                  <span className="hidden sm:inline">{thisOne && !loading ? 'Stop' : 'Play Sample'}</span>
                </button>
              </div>
            )
          })}
        </div>
      )}

      <p role="status" className={`mt-3 min-h-5 text-[13px] ${status?.ok ? 'text-brand' : 'text-red-600'}`}>{status?.text}</p>
    </Card>
  )
}
