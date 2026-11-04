import { useEffect, useRef, useState } from 'react'
import { get } from '../api'
import { microphoneBlocked, openMic } from './mic'

// Speech has two engines. With a Gnani key on the server (see README) the microphone and Saathi's voice use Gnani:
// good with Hindi and Hinglish. Without a key the browser's own speech is used (Chrome and Android Chrome; free, no setup).
// Either way the browser never sees the key: it only talks to this app's /api/voice.
const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition

const engine = { token: '', gnani: false }

/** Asks the server whether Gnani is set up, and remembers it for speak(). Returns true when it is. */
export async function loadVoiceEngine(token) {
  engine.token = token
  engine.gnani = await get('/api/voice/status', token).then((r) => !!r.gnani).catch(() => false)
  return engine.gnani
}

const BLOCKED = 'The microphone is blocked. Allow it in your browser settings, or type instead.'
const NOT_HEARD = 'I did not hear anything. Tap the mic and try again.'
const MIC_PROBLEMS = {
  'not-allowed': BLOCKED,
  'service-not-allowed': BLOCKED,
  'no-speech': NOT_HEARD,
}

/** One recorded sentence as text. "" when the service heard nothing. */
async function transcribe(blob) {
  const form = new FormData()
  form.append('audio', blob, 'speech.wav')
  const res = await fetch('/api/voice/transcribe', { method: 'POST', headers: { Authorization: `Bearer ${engine.token}` }, body: form })
    .catch(() => { throw new Error('Cannot reach Dukaan Saathi. Please check your internet connection.') })
  const data = await res.json().catch(() => ({}))
  if (!res.ok) throw new Error(data.message || 'The voice service did not work. You can type instead.')
  return (data.text || '').trim()
}

/** Listening with the microphone. start() listens for one sentence and gives the words to onText. */
export function useListening(onText) {
  const recogniser = useRef(null)
  const mic = useRef(null)
  const onTextRef = useRef(onText)
  onTextRef.current = onText
  const attempt = useRef(0) // a new number for every start; closing or stopping makes the old attempt out of date
  const [listening, setListening] = useState(false)
  const [working, setWorking] = useState(false) // the sentence has been heard and is being turned into words
  const [problem, setProblem] = useState('')

  // Closing the window (or leaving the page) must switch the microphone off straight away, even if it is still starting
  // up, and must stop a sentence that is still being turned into words from being acted on.
  useEffect(() => () => {
    attempt.current++
    recogniser.current?.abort()
    mic.current?.close()
    mic.current = null
  }, [])

  const startGnani = async () => {
    const mine = ++attempt.current
    const current = () => mine === attempt.current
    setListening(true)
    try {
      const opened = await openMic({
        once: true,
        onNoSpeech: () => {
          if (!current()) return
          mic.current = null
          setListening(false)
          setProblem(NOT_HEARD)
        },
        onSentence: async (blob) => {
          if (!current()) return
          mic.current = null
          setListening(false)
          setWorking(true)
          try {
            const words = await transcribe(blob)
            if (!current()) return
            if (words) onTextRef.current(words)
            else setProblem(NOT_HEARD)
          } catch (err) {
            if (current()) setProblem(err.message)
          } finally {
            if (current()) setWorking(false)
          }
        },
      })
      if (current()) mic.current = opened
      else opened.close() // stopped or closed while the microphone was still starting
    } catch (err) {
      if (!current()) return
      setListening(false)
      setProblem(microphoneBlocked(err) ? BLOCKED : 'The microphone did not work. You can type instead.')
    }
  }

  const startBrowser = () => {
    const r = new Recognition()
    r.lang = 'en-IN'
    r.interimResults = false
    r.onresult = (event) => onTextRef.current(event.results[0][0].transcript)
    r.onerror = (event) => setProblem(MIC_PROBLEMS[event.error] || 'The microphone did not work. You can type instead.')
    r.onend = () => setListening(false)
    recogniser.current = r
    r.start()
    setListening(true)
  }

  const start = () => {
    if (!engine.gnani && !Recognition) return
    setProblem('')
    if (engine.gnani) startGnani()
    else startBrowser()
  }

  const stop = () => {
    if (mic.current) mic.current.finish() // ends the sentence being spoken, which is then sent
    else if (recogniser.current) recogniser.current.stop()
    else {
      attempt.current++ // the microphone is still starting: cancel that
      setListening(false)
    }
  }

  return { supported: engine.gnani || !!Recognition, engine: engine.gnani ? 'gnani' : 'voice', listening, working, problem, start, stop }
}

/**
 * Keep listening: the microphone stays on, hears each sentence on its own, and hands it to onText.
 * `enabled` keeps the microphone open; `active` is whether it is taking in sound right now (it is switched off while Saathi
 * talks, so Saathi does not hear itself). `gnani` says which engine to use.
 */
export function useKeepListening(onText, { enabled, active, gnani }) {
  const onTextRef = useRef(onText)
  onTextRef.current = onText
  const browser = useBrowserKeepListening(onTextRef, active && !gnani)
  const server = useGnaniKeepListening(onTextRef, enabled && gnani, active)
  return gnani ? server : browser
}

function useGnaniKeepListening(onTextRef, enabled, active) {
  const mic = useRef(null)
  const activeRef = useRef(active)
  activeRef.current = active
  const [open, setOpen] = useState(false)
  const [problem, setProblem] = useState('')

  useEffect(() => {
    if (!enabled) return undefined
    let cancelled = false
    setProblem('')
    openMic({
      onSentence: async (blob) => {
        try {
          const words = await transcribe(blob)
          if (!cancelled && words.length > 1) onTextRef.current(words) // not after listening was switched off
        } catch (err) {
          if (!cancelled) setProblem(err.message)
        }
      },
    }).then((m) => {
      if (cancelled) return m.close()
      mic.current = m
      m.setCapturing(activeRef.current)
      setOpen(true)
    }).catch((err) => {
      if (!cancelled) setProblem(microphoneBlocked(err) ? BLOCKED : 'The microphone did not work. You can type instead.')
    })
    return () => {
      cancelled = true
      mic.current?.close()
      mic.current = null
      setOpen(false)
    }
  }, [enabled])

  useEffect(() => { mic.current?.setCapturing(active) }, [active])

  return { listening: enabled && open && active, problem }
}

function useBrowserKeepListening(onTextRef, active) {
  const [listening, setListening] = useState(false)
  const [problem, setProblem] = useState('')
  const recogniser = useRef(null)
  const wanted = useRef(false)
  const restarts = useRef([]) // when the browser last ended listening on its own

  const open = () => {
    if (!Recognition) return setProblem('This browser cannot listen. Use Chrome, or type instead.')
    const r = new Recognition()
    r.lang = 'en-IN'
    r.continuous = true
    r.interimResults = false
    r.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) onTextRef.current(event.results[i][0].transcript.trim())
      }
    }
    r.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        wanted.current = false
        setProblem(MIC_PROBLEMS[event.error])
      } else if (event.error === 'audio-capture') {
        wanted.current = false
        setProblem('No microphone was found. Connect one, or type instead.')
      }
    }
    r.onend = () => {
      if (recogniser.current !== r) return undefined
      recogniser.current = null
      if (!wanted.current) return setListening(false)
      // The browser ends listening after a pause, which is normal. Ending again and again at once is not: give up.
      const now = Date.now()
      restarts.current = [...restarts.current.filter((t) => now - t < 3000), now]
      if (restarts.current.length > 5) {
        wanted.current = false
        setListening(false)
        setProblem('The microphone keeps stopping. Tap Stop and try again, or type instead.')
        return undefined
      }
      return open()
    }
    recogniser.current = r
    r.start()
    setListening(true)
  }

  useEffect(() => {
    wanted.current = active
    if (!active) {
      const r = recogniser.current
      recogniser.current = null
      r?.abort()
      setListening(false)
    } else if (!recogniser.current) {
      setProblem('')
      restarts.current = []
      open()
    }
  }, [active])

  useEffect(() => () => {
    wanted.current = false
    const r = recogniser.current
    recogniser.current = null
    r?.abort()
  }, [])

  return { listening, problem }
}

let player = null // the audio being played now
let speakingNow = 0 // a new number for every call, so an old call can tell it was replaced

/** Stops whatever Saathi is saying. */
export function stopSpeaking() {
  speakingNow++
  window.speechSynthesis?.cancel()
  player?.pause()
  player = null
}

/** Reads text aloud. The promise comes back when Saathi has finished speaking. */
export async function speak(text) {
  stopSpeaking()
  const mine = speakingNow
  if (engine.gnani) {
    try {
      const res = await fetch('/api/voice/speak', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${engine.token}` },
        body: JSON.stringify({ text }),
      })
      if (!res.ok) throw new Error('no voice')
      const url = URL.createObjectURL(await res.blob())
      if (mine !== speakingNow) return URL.revokeObjectURL(url) // something newer was said or Saathi was stopped
      await new Promise((done, fail) => {
        const audio = new Audio(url)
        player = audio
        audio.onended = done
        audio.onpause = done
        audio.onerror = fail
        audio.play().catch(fail)
      }).finally(() => URL.revokeObjectURL(url))
      return undefined
    } catch {
      if (mine !== speakingNow) return undefined
      // the Gnani voice failed: use the browser's voice so the reply is still heard
    }
  }
  return new Promise((done) => {
    if (!('speechSynthesis' in window)) return done()
    const voice = new SpeechSynthesisUtterance(text.replace(/[•\n]+/g, '. '))
    voice.lang = 'en-IN'
    voice.onend = done
    voice.onerror = done
    window.speechSynthesis.speak(voice)
    return undefined
  })
}
