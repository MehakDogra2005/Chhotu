import { useEffect, useRef, useState } from 'react'
import { Mic, Send, Sparkles } from 'lucide-react'
import { post } from '../api'
import { useAuth } from '../AuthContext'
import { refreshData } from '../useFetch'
import Modal from './Modal'
import { speak, useListening } from './speech'

const GREETING = 'Boliye, aapke liye kya karna hai? Say it, or type it. For example: "Chocolate ka stock kitna hai?"'

/**
 * Talk to Saathi. You say or type something; Saathi answers, or shows a change for you to confirm.
 * `start` says how it was opened: { text } sends that sentence now, { listen: true } starts the microphone,
 * and { hint } replaces the first line.
 */
export default function AssistantDialog({ start, speakReplies, onClose }) {
  const { token } = useAuth()
  const [chat, setChat] = useState([{ from: 'saathi', text: start.hint || GREETING }])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const bottom = useRef(null)

  const add = (message) => setChat((c) => [...c, message])
  const reply = (text) => { add({ from: 'saathi', text }); if (speakReplies) speak(text) }
  // Remember that a proposal has been dealt with, so its buttons go away.
  const settle = (id) => setChat((c) => c.map((m) => (m.utteranceId === id ? { ...m, options: [] } : m)))

  const send = async (text, via = 'typed') => {
    if (!text.trim() || busy) return
    add({ from: 'me', text })
    setInput('')
    setBusy(true)
    try {
      const r = await post('/api/assistant/interpret', { text, via }, token)
      add({ from: 'saathi', text: r.message, options: r.options, utteranceId: r.utteranceId })
      if (speakReplies) speak(r.message)
    } catch (err) {
      reply(err.message)
    } finally {
      setBusy(false)
    }
  }

  const confirm = async (message, choice) => {
    setBusy(true)
    try {
      const done = await post('/api/assistant/execute', { utteranceId: message.utteranceId, choice }, token)
      settle(message.utteranceId)
      reply(done.message)
      refreshData()
    } catch (err) {
      settle(message.utteranceId)
      reply(err.message)
    } finally {
      setBusy(false)
    }
  }

  const cancel = async (message) => {
    settle(message.utteranceId)
    await post('/api/assistant/cancel', { utteranceId: message.utteranceId }, token).catch(() => {})
    reply('Okay, cancelled. Nothing was changed.')
  }

  const mic = useListening((words) => send(words, 'voice'))

  // When the window opens: send the sentence it was opened with, or start listening.
  useEffect(() => {
    if (start.text) send(start.text)
    else if (start.listen && mic.supported) mic.start()
  }, [])

  useEffect(() => { bottom.current?.scrollIntoView({ behavior: 'smooth' }) }, [chat])

  // Closing the window also stops Saathi talking.
  useEffect(() => () => window.speechSynthesis?.cancel(), [])

  return (
    <Modal title="Saathi" icon={<span className="grid h-9 w-9 place-items-center rounded-full bg-brand text-white"><Sparkles size={18} /></span>} onClose={onClose}>
      <div className="flex-1 space-y-3 overflow-y-auto px-5 py-4">
        {chat.map((m, i) => (
          <div key={i} className={m.from === 'me' ? 'flex justify-end' : 'flex'}>
            <div className={`max-w-[88%] whitespace-pre-line rounded-2xl px-4 py-2.5 text-[14px] leading-relaxed ${m.from === 'me' ? 'bg-brand text-white' : 'bg-white shadow-sm'}`}>
              {m.text}
              {m.options?.length > 0 && (
                <div className="mt-3 space-y-2">
                  {m.options.map((label, choice) => (
                    <button key={choice} disabled={busy} onClick={() => confirm(m, choice)}
                      className="block w-full rounded-xl border border-brand bg-mint px-3 py-2 text-left text-[13px] font-semibold text-heading hover:bg-[#d6efdc] disabled:opacity-60">
                      {m.options.length === 1 ? 'Yes, do it: ' : ''}{label}
                    </button>
                  ))}
                  <button disabled={busy} onClick={() => cancel(m)} className="w-full rounded-xl py-1.5 text-[13px] font-medium text-muted hover:bg-stone-100">No, cancel</button>
                </div>
              )}
            </div>
          </div>
        ))}
        {busy && <div className="text-[13px] text-muted">Saathi is thinking...</div>}
        <div ref={bottom} />
      </div>

      {mic.listening && <p className="px-5 pb-2 text-[13px] font-semibold text-red-500">● Listening... tap the red mic when you are done</p>}
      {mic.problem && <p className="px-5 pb-2 text-[13px] text-red-600">{mic.problem}</p>}
      <form onSubmit={(e) => { e.preventDefault(); send(input) }} className="flex items-center gap-2 border-t border-edge p-3">
        {mic.supported && (
          <button type="button" onClick={mic.listening ? mic.stop : mic.start} aria-label={mic.listening ? 'Stop listening' : 'Speak'}
            className={`grid h-12 w-12 shrink-0 place-items-center rounded-full text-white ${mic.listening ? 'animate-pulse bg-red-500' : 'bg-brand'}`}>
            <Mic size={22} />
          </button>
        )}
        <input value={input} onChange={(e) => setInput(e.target.value)} autoFocus={!start.listen}
          placeholder={mic.listening ? 'Listening...' : 'Type here, for example: Ramesh ne 500 de diye'}
          className="h-12 min-w-0 flex-1 rounded-full border border-line bg-white px-4 text-[14px] outline-none focus:border-brand" />
        <button disabled={busy || !input.trim()} aria-label="Send" className="grid h-12 w-12 shrink-0 place-items-center rounded-full bg-brand text-white disabled:opacity-50"><Send size={20} /></button>
      </form>
    </Modal>
  )
}
