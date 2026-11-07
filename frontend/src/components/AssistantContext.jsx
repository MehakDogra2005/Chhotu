import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../api'
import { useAuth } from '../AuthContext'
import { refreshData } from '../useFetch'
import AssistantDialog from './AssistantDialog'
import { getVoiceLanguage, loadVoiceEngine, speak, stopSpeaking, useKeepListening } from './speech'
import { Mic, Square } from 'lucide-react'

const AssistantContext = createContext(null)
const KEY = 'saathi-keep-listening'

/**
 * Anything on any page can call ask() to open Munim Ji: ask({ greet: true }) (the mic button: greets, explains, then listens),
 * ask({ text }), ask({ listen: true, hint }), ask({ reply }). A plain ask({ listen: true }) is the same as greet.
 * `draft` is the order Munim Ji is taking by voice right now, for the Orders screen to show.
 */
export const useAssistant = () => useContext(AssistantContext)

function readKeepListening() {
  try {
    return localStorage.getItem(KEY) === 'on'
  } catch {
    return false
  }
}

export function AssistantProvider({ speakReplies, userName, children }) {
  const { token } = useAuth()
  const navigate = useNavigate()
  const [start, setStart] = useState(null) // null = closed
  const [request, setRequest] = useState(null) // something asked while the chat is already open: it goes into the same chat
  const [keepListening, setKeepListeningState] = useState(readKeepListening)
  const [busy, setBusy] = useState(false)
  const [engine, setEngine] = useState(null) // null until the server says whether Gnani speech is set up, then 'gnani' or 'browser'
  const working = useRef(false)
  const keepRef = useRef(false) // is Keep listening on right now (also read after waits, so Stop is honoured at once)
  const dialogRef = useRef(false) // is the Munim Ji window open
  const queueRef = useRef([]) // sentences heard while another was being answered
  const heardRef = useRef(null)
  const [last, setLast] = useState(null) // { heard, reply } of the last sentence while listening
  const [draft, setDraft] = useState(null) // { customerName, lines } of the order being taken by voice
  useEffect(() => {
    loadVoiceEngine(token).then((gnani) => setEngine(gnani ? 'gnani' : 'browser'))
    return stopSpeaking
  }, [token])

  const ask = useCallback((options = {}) => {
    const greet = options.greet || options.listen // every mic button on every page opens the same Munim Ji as the home page: greeting, then listening
    if (dialogRef.current) setRequest({ ...options, greet, id: Date.now() }) // one chat box: never a second one, never a fresh one
    else {
      setRequest(null)
      setStart({ ...options, greet, id: Date.now() })
    }
  }, [])
  const close = useCallback(() => { setStart(null); setRequest(null); setDraft(null) }, [])

  const setKeepListening = (on) => {
    keepRef.current = on
    if (!on) {
      queueRef.current = [] // Stop means stop: nothing waiting is acted on and Munim Ji stops talking
      stopSpeaking()
    }
    setKeepListeningState(on)
    try {
      localStorage.setItem(KEY, on ? 'on' : 'off')
    } catch {
      // the switch still works for this visit
    }
  }

  // One sentence heard while listening: do it, say the answer, or open Munim Ji to confirm a change.
  const heard = async (words) => {
    if (!words || !keepRef.current || dialogRef.current) return // listening was switched off, or the Munim Ji window is open
    if (working.current) { // a sentence is being answered: keep this one for afterwards instead of losing it
      if (queueRef.current.length < 3) queueRef.current.push(words)
      return
    }
    working.current = true
    setBusy(true)
    try {
      const r = await post('/api/assistant/interpret', { text: words, via: engine === 'gnani' ? 'gnani' : 'voice', language: getVoiceLanguage() || undefined }, token)
      if (!keepRef.current) return // Stop was pressed while this was being answered: do not act on it
      if (r.navigate) navigate(r.navigate)
      if (r.options?.length) {
        setLast({ heard: words, reply: 'Please confirm in Munim Ji.' })
        ask({ reply: { ...r, heard: words } })
        if (speakReplies) await speak(r.message, r.language)
      } else {
        setLast({ heard: words, reply: r.message })
        if (speakReplies) await speak(r.message, r.language)
      }
      refreshData()
    } catch (err) {
      setLast({ heard: words, reply: err.message })
      if (speakReplies) await speak(err.message)
    } finally {
      working.current = false
      setBusy(false)
      const next = queueRef.current.shift()
      if (next && keepRef.current) setTimeout(() => heardRef.current(next), 0)
    }
  }
  heardRef.current = heard
  keepRef.current = keepListening
  dialogRef.current = !!start

  // Listening runs only when nothing else is going on: not while a sentence is being answered, and not while
  // the Munim Ji window is open (so "yes" for a proposal is not taken as a new sentence).
  const listening = useKeepListening(heard, {
    enabled: keepListening && engine !== null,
    active: keepListening && engine !== null && !start && !busy,
    gnani: engine === 'gnani',
  })

  return (
    <AssistantContext.Provider value={{ ask, keepListening, setKeepListening, draft }}>
      {children}
      {/* a new id makes a fresh conversation each time it is opened, and keeps this one while it stays open */}
      {start && (
        <AssistantDialog key={start.id} start={start} request={request} ready={engine !== null} speakReplies={speakReplies} userName={userName}
          keepListening={keepListening} onKeepListening={setKeepListening} onDraft={setDraft} onClose={close} />
      )}
      {keepListening && !start && (
        <div className="fixed bottom-24 left-1/2 z-40 w-[min(92vw,460px)] -translate-x-1/2 rounded-2xl border border-brand bg-white px-4 py-3 text-[13px] shadow-lg md:bottom-5">
          <div className="flex items-center gap-3">
            <span className={`grid h-9 w-9 shrink-0 place-items-center rounded-full text-white ${listening.listening ? 'animate-pulse bg-red-500' : 'bg-muted'}`}><Mic size={18} /></span>
            <div className="min-w-0 flex-1">
              <div className="font-bold text-heading">{busy ? 'Munim Ji is working...' : listening.listening ? 'Listening, always on' : 'Not listening'}</div>
              <div className="truncate text-muted">
                {listening.problem || (last ? `You: "${last.heard}". ${last.reply}` : 'Say a command, for example "Inventory kholo".')}
              </div>
            </div>
            <button onClick={() => setKeepListening(false)} className="flex shrink-0 items-center gap-1 rounded-lg border border-edge px-2.5 py-1.5 font-semibold hover:bg-stone-50">
              <Square size={12} /> Stop
            </button>
          </div>
        </div>
      )}
    </AssistantContext.Provider>
  )
}
