import { useEffect, useRef, useState } from 'react'
import { ChevronDown, ChevronUp, Ear, Mic, Pause, Play, RotateCw, Send, SkipForward, Sparkles, Square, X } from 'lucide-react'
import CapabilitySuggestions from './CapabilitySuggestions'
import VoiceStateIndicator from './VoiceStateIndicator'
import VoiceWaveform from './VoiceWaveform'
import { S } from './voiceStates'

/**
 * The small conversation window. It sits in a corner (above the bottom menu on a phone) so the page Saathi opens stays
 * visible behind it, and it stays open while Saathi moves between pages. `voice` is what useVoiceConversation returns.
 */
export default function ConversationPanel({ voice, keepListening, onKeepListening, onClose }) {
  const { state, chat, error, showSuggestions, listening, working, micSupported, speaking } = voice
  const [input, setInput] = useState('')
  const [minimized, setMinimized] = useState(false)
  const bottom = useRef(null)
  const busy = state === S.PROCESSING || state === S.NAVIGATING

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [chat, showSuggestions, minimized])
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const submit = (event) => {
    event.preventDefault()
    if (!input.trim() || busy) return
    voice.send(input)
    setInput('')
  }

  const mode = listening ? 'listening' : speaking ? 'speaking' : 'idle'
  const isListening = state === S.LISTENING || (state === S.AWAITING_CONFIRMATION && listening)

  return (
    <section aria-label="Saathi conversation" data-state={state}
      className="fixed inset-x-3 bottom-20 z-50 flex max-h-[min(72dvh,600px)] flex-col overflow-hidden rounded-3xl border border-edge bg-card shadow-2xl lg:inset-x-auto lg:bottom-4 lg:right-4 lg:w-[400px]">
      <header className="flex items-center gap-3 border-b border-edge px-4 py-3">
        <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full bg-brand text-white"><Sparkles size={18} /></span>
        <div className="min-w-0 flex-1">
          <h2 className="text-[16px] font-bold leading-tight">Saathi</h2>
          <VoiceStateIndicator state={state} />
        </div>
        {state === S.PAUSED ? (
          <button onClick={voice.resume} aria-label="Resume" title="Resume" className="grid h-9 w-9 place-items-center rounded-full hover:bg-stone-100"><Play size={18} /></button>
        ) : (
          <button onClick={voice.pause} aria-label="Pause" title="Pause voice" className="grid h-9 w-9 place-items-center rounded-full hover:bg-stone-100"><Pause size={18} /></button>
        )}
        <button onClick={() => setMinimized(!minimized)} aria-label={minimized ? 'Show conversation' : 'Minimise'} title={minimized ? 'Show' : 'Minimise'}
          className="grid h-9 w-9 place-items-center rounded-full hover:bg-stone-100">
          {minimized ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
        </button>
        <button onClick={onClose} aria-label="End conversation" title="End" className="grid h-9 w-9 place-items-center rounded-full hover:bg-stone-100"><X size={20} /></button>
      </header>

      {/* What the microphone is doing, always visible, even when the conversation is minimised */}
      {(isListening || speaking || working) && (
        <div className={`flex items-center gap-3 px-4 py-2 ${isListening ? 'bg-red-50' : 'bg-panel'}`}>
          <VoiceWaveform mode={mode} />
          <div className="min-w-0 flex-1 text-[13px] font-semibold">
            {working ? 'Samajh raha hoon...' : isListening ? 'Ji, boliye, main sun raha hoon...' : 'Saathi bol raha hai...'}
          </div>
          {isListening && (
            <button onClick={voice.pause} className="flex shrink-0 items-center gap-1 rounded-lg border border-red-200 bg-white px-2.5 py-1.5 text-[12.5px] font-semibold text-red-600 hover:bg-red-50">
              <Square size={12} /> Stop
            </button>
          )}
          {speaking && (
            <button onClick={voice.micToggle} className="flex shrink-0 items-center gap-1 rounded-lg border border-edge bg-white px-2.5 py-1.5 text-[12.5px] font-semibold hover:bg-stone-50">
              <SkipForward size={12} /> Skip
            </button>
          )}
        </div>
      )}

      {!minimized && (
        <>
          <div className="flex-1 space-y-3 overflow-y-auto px-4 py-3" aria-live="polite">
            {chat.map((m) => (
              <div key={m.id} className={m.from === 'me' ? 'flex justify-end' : 'flex'}>
                <div className={`max-w-[90%] whitespace-pre-line rounded-2xl px-3.5 py-2.5 text-[14px] leading-relaxed ${m.from === 'me' ? 'bg-brand text-white' : 'bg-white shadow-sm'}`}>
                  {m.text}
                  {m.options?.length > 0 && (
                    <div className="mt-3 space-y-2">
                      {m.options.map((label, choice) => (
                        <button key={choice} disabled={busy} onClick={() => voice.confirm(m.utteranceId, choice)}
                          className="block w-full rounded-xl border border-brand bg-mint px-3 py-2 text-left text-[13px] font-semibold text-heading hover:bg-[#d6efdc] disabled:opacity-60">
                          {m.options.length === 1 ? 'Haan, kar do: ' : ''}{label}
                        </button>
                      ))}
                      <button disabled={busy} onClick={() => voice.cancelPending(m.utteranceId)} className="w-full rounded-xl py-1.5 text-[13px] font-medium text-muted hover:bg-stone-100">Nahi, rehne do</button>
                    </div>
                  )}
                </div>
              </div>
            ))}
            {showSuggestions && <CapabilitySuggestions onPick={voice.send} disabled={busy} />}
            {state === S.PROCESSING && <div className="text-[13px] text-muted">Saathi soch raha hai...</div>}
            {error && (
              <div role="alert" className="rounded-xl border border-red-200 bg-red-50 px-3 py-2 text-[13px] text-red-700">
                {error}
                <button onClick={voice.retry} className="ml-2 inline-flex items-center gap-1 font-semibold underline"><RotateCw size={12} /> Try again</button>
              </div>
            )}
            <div ref={bottom} />
          </div>

          <button onClick={() => onKeepListening(!keepListening)} className="mx-4 mb-1 flex items-center gap-2 self-start rounded-full border border-edge px-3 py-1 text-[12px] font-semibold hover:bg-stone-50">
            <Ear size={13} className={keepListening ? 'text-brand' : 'text-muted'} /> Keep listening: {keepListening ? 'on' : 'off'}
          </button>

          <form onSubmit={submit} className="flex items-center gap-2 border-t border-edge p-3">
            {micSupported && (
              <button type="button" onClick={voice.micToggle} aria-label={listening ? 'Done speaking' : 'Speak'}
                className={`grid h-12 w-12 shrink-0 place-items-center rounded-full text-white ${listening ? 'animate-pulse bg-red-500' : 'bg-brand'}`}>
                <Mic size={22} />
              </button>
            )}
            <input value={input} onChange={(e) => setInput(e.target.value)} aria-label="Type to Saathi"
              placeholder={listening ? 'Sun raha hoon...' : 'Ya yahan type kijiye...'}
              className="h-12 min-w-0 flex-1 rounded-full border border-line bg-white px-4 text-[14px] outline-none focus:border-brand" />
            <button disabled={busy || !input.trim()} aria-label="Send" className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-brand text-white disabled:opacity-50"><Send size={20} /></button>
          </form>
        </>
      )}
    </section>
  )
}
