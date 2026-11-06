import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { post } from '../../api'
import { useAuth } from '../../AuthContext'
import { refreshData } from '../../useFetch'
import { prefetchSpeech, speak, stopSpeaking, useListening } from '../speech'
import { S, SPEAKING } from './voiceStates'

const INTRO = 'Ji, aap mujhse apni dukaan ki inventory ke baare mein pooch sakte hain, aaj ya kal ki kamai ka hisaab jaan sakte hain, '
  + 'ya customer ke orders aur udhaar ka record rakh sakte hain.'
const NOT_HEARD = 'Mujhe kuch sunai nahi diya. Kripya dobara boliye.'
const GAVE_UP = 'Mujhe kuch sunai nahi diya. Jab bolna ho, mic dabaiye.'
const MAX_QUIET_ROUNDS = 2 // after this many silent rounds in a row the conversation pauses instead of listening forever
const YES = new Set(['haan', 'han', 'haa', 'ha', 'ji', 'yes', 'yeah', 'yep', 'ok', 'okay', 'theek', 'thik', 'sahi', 'bilkul', 'kar', 'kardo', 'save', 'confirm', 'done', 'correct'])
const NO = new Set(['nahi', 'nahin', 'na', 'no', 'nope', 'cancel', 'mat', 'rehne', 'ruko', 'galat'])

const words = (text) => text.toLowerCase().replace(/[^\p{L}\p{N}\s]/gu, ' ').split(/\s+/).filter(Boolean)
const isYes = (text) => { const w = words(text); return w.length > 0 && w.length <= 4 && YES.has(w[0]) }
const isNo = (text) => { const w = words(text); return w.length > 0 && w.length <= 3 && NO.has(w[0]) }
const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

/** "Rajesh Sharma" -> "Rajesh". Nothing for a name that is not a name (a phone number, empty). */
function firstName(full) {
  const first = (full || '').trim().split(/\s+/)[0]
  return /^\p{L}/u.test(first) ? first : ''
}

/**
 * One conversation with Saathi, as a small state machine (see voiceStates.js). Tapping the mic runs
 * GREETING -> INTRODUCTION -> LISTENING; after that every sentence goes PROCESSING -> NAVIGATING -> RESPONDING and back to
 * LISTENING. Every step checks that it is still the current one (`run`), so pausing, closing, or a newer sentence
 * cleanly cancels whatever was still speaking or listening: the microphone never opens while Saathi is talking.
 *
 * start says how the conversation was opened: { greet } speaks the greeting first, { listen } listens at once,
 * { text } sends that sentence, { reply } continues an answer that is waiting for a yes.
 */
export default function useVoiceConversation({ start, ready, speakReplies, userName, onDraft }) {
  const { token } = useAuth()
  const navigate = useNavigate()
  const [state, setStateRaw] = useState(start.reply ? S.AWAITING_CONFIRMATION : S.IDLE)
  const [chat, setChat] = useState(() => {
    if (start.reply) {
      return [{ id: 1, from: 'me', text: start.reply.heard },
        { id: 2, from: 'saathi', text: start.reply.message, options: start.reply.options, utteranceId: start.reply.utteranceId }]
    }
    return start.hint ? [{ id: 1, from: 'saathi', text: start.hint }] : []
  })
  const [showSuggestions, setShowSuggestions] = useState(false)
  const [error, setError] = useState('')

  const stateRef = useRef(state)
  const run = useRef(0)          // every new step takes a number; a step whose number is out of date stops quietly
  const busy = useRef(false)     // a sentence is being sent to the server
  const nextId = useRef(10)
  const context = useRef(start.reply?.context || null) // what the conversation is about; goes back to the server with each sentence
  const pending = useRef(start.reply ? { utteranceId: start.reply.utteranceId, count: start.reply.options?.length || 0 } : null)
  const voiceDriven = useRef(!!(start.greet || start.listen || start.reply)) // listen again after each answer
  const lastSent = useRef({ text: '', at: 0 })
  const lastText = useRef('')
  const quiet = useRef(0)

  const setState = (next) => { stateRef.current = next; setStateRaw(next) }
  const add = (message) => setChat((c) => [...c, { id: nextId.current++, ...message }])
  const settle = (utteranceId) => setChat((c) => c.map((m) => (m.utteranceId === utteranceId ? { ...m, options: [] } : m)))

  const mic = useListening((spoken) => heard(spoken), {
    onNothing: () => nothingHeard(),
    onError: (message) => fail(message),
  })
  const micRef = useRef(mic)
  micRef.current = mic

  // ---- speaking and listening

  /** Says it, unless spoken replies are switched off in Settings. False when something newer has taken over meanwhile. */
  const say = async (text, my) => {
    if (speakReplies) await speak(text)
    return run.current === my
  }

  const openMic = () => {
    micRef.current.cancel()
    micRef.current.start()
  }

  const listen = () => {
    if (!micRef.current.supported) {
      setError('This browser cannot listen. Please type instead, or use Chrome.')
      setState(S.ERROR)
      return
    }
    setError('')
    setState(S.LISTENING)
    openMic()
  }

  /** After Saathi has spoken: wait for a yes/no if a change is pending, otherwise listen again (in a voice conversation). */
  const afterSpeaking = () => {
    const waiting = !!pending.current
    if (voiceDriven.current && micRef.current.supported) {
      setError('')
      setState(waiting ? S.AWAITING_CONFIRMATION : S.LISTENING)
      openMic()
    } else {
      setState(waiting ? S.AWAITING_CONFIRMATION : S.IDLE)
    }
  }

  /** Shows the page the answer is about, then says the answer. */
  const respond = async (reply, my) => {
    if (reply.navigate) {
      setState(S.NAVIGATING)
      navigate(reply.navigate)
      await sleep(450)
      if (run.current !== my) return
    }
    setState(S.RESPONDING)
    if (!(await say(reply.message, my))) return
    afterSpeaking()
  }

  const fail = (message) => {
    run.current++
    stopSpeaking()
    micRef.current.cancel()
    setError(message)
    setState(S.ERROR)
  }

  // ---- the first words

  const greet = async () => {
    const my = ++run.current
    const name = firstName(userName)
    const hello = `Haan${name ? ` ${name} ji` : ' ji'}! Boliye, aaj main aapke liye kya kar sakta hoon?`
    setState(S.GREETING)
    add({ from: 'saathi', text: hello })
    if (speakReplies) prefetchSpeech(INTRO) // ready by the time the greeting ends
    if (!(await say(hello, my))) return
    setState(S.INTRODUCTION)
    setShowSuggestions(true)
    add({ from: 'saathi', text: INTRO })
    if (!(await say(INTRO, my))) return
    listen() // only now, so the microphone never hears Saathi's own greeting
  }

  // ---- what the shopkeeper says

  const send = async (text, via = 'typed') => {
    const sentence = text.trim()
    if (!sentence || busy.current) return
    const now = Date.now()
    // The same words twice within a moment are one request (an echo, a double tap)
    if (lastSent.current.text === sentence.toLowerCase() && now - lastSent.current.at < 2500) return
    lastSent.current = { text: sentence.toLowerCase(), at: now }
    lastText.current = sentence

    busy.current = true
    stopSpeaking()
    micRef.current.cancel()
    const my = ++run.current
    quiet.current = 0
    setShowSuggestions(false)
    setError('')
    add({ from: 'me', text: sentence })
    if (pending.current) { // a new sentence replaces the change that was waiting: it is not done, and its buttons go away
      const old = pending.current.utteranceId
      pending.current = null
      settle(old)
      post('/api/assistant/cancel', { utteranceId: old }, token).catch(() => {})
    }
    setState(S.PROCESSING)

    let reply
    try {
      reply = await post('/api/assistant/interpret', { text: sentence, via, context: context.current }, token)
    } catch (err) {
      busy.current = false
      lastSent.current = { text: '', at: 0 } // so "Try again" is allowed
      if (run.current === my) fail(err.message)
      return
    }
    busy.current = false

    context.current = reply.context
    onDraft?.(reply.draft || null)
    if (reply.options?.length) pending.current = { utteranceId: reply.utteranceId, count: reply.options.length }
    add({ from: 'saathi', text: reply.message, options: reply.options, utteranceId: reply.utteranceId })
    if (run.current !== my) return // paused or closed while it was thinking: show the answer, but do not speak or listen
    await respond(reply, my)
  }

  /** What the microphone heard: a yes or no to a waiting change, or a new sentence. */
  function heard(spoken) {
    quiet.current = 0
    const waiting = pending.current
    if (waiting && stateRef.current === S.AWAITING_CONFIRMATION) {
      if (waiting.count === 1 && isYes(spoken)) return confirm(waiting.utteranceId, 0)
      if (isNo(spoken)) return cancelPending(waiting.utteranceId)
    }
    return send(spoken, micRef.current.engine)
  }

  function nothingHeard() {
    const now = stateRef.current
    if (now !== S.LISTENING && now !== S.AWAITING_CONFIRMATION) return
    quiet.current += 1
    if (quiet.current >= MAX_QUIET_ROUNDS) {
      quiet.current = 0
      add({ from: 'saathi', text: GAVE_UP })
      setState(S.PAUSED)
      return
    }
    const my = ++run.current
    setState(S.RESPONDING)
    add({ from: 'saathi', text: NOT_HEARD })
    say(NOT_HEARD, my).then((current) => current && afterSpeaking())
  }

  async function confirm(utteranceId, choice) {
    if (busy.current) return
    busy.current = true
    stopSpeaking()
    micRef.current.cancel()
    const my = ++run.current
    setState(S.PROCESSING)
    let done
    try {
      done = await post('/api/assistant/execute', { utteranceId, choice }, token)
    } catch (err) {
      busy.current = false
      // Only when it was already done or cancelled is the proposal gone; after a network error its buttons stay so it can be retried
      if (err.status === 404 || err.status === 409) {
        pending.current = null
        settle(utteranceId)
      }
      if (run.current === my) fail(err.message)
      return
    }
    busy.current = false
    pending.current = null
    settle(utteranceId)
    context.current = done.context
    onDraft?.(null)
    add({ from: 'saathi', text: done.message })
    refreshData()
    if (run.current !== my) return
    await respond(done, my)
  }

  async function cancelPending(utteranceId) {
    stopSpeaking()
    micRef.current.cancel()
    const my = ++run.current
    pending.current = null
    settle(utteranceId)
    post('/api/assistant/cancel', { utteranceId }, token).catch(() => {})
    const message = context.current?.mode === 'ORDER'
      ? 'Theek hai ji, order abhi save nahi kiya. Kuch badalna ho to bataiye.'
      : 'Theek hai ji, kuch nahi badla.'
    add({ from: 'saathi', text: message })
    await respond({ message }, my)
  }

  // ---- controls

  /** Stops everything: Saathi stops talking, the microphone closes and nothing heard is used. */
  const pause = () => {
    run.current++
    stopSpeaking()
    micRef.current.cancel()
    setState(S.PAUSED)
  }

  /** The microphone button: end the sentence, cut Saathi short and listen, or start listening. */
  const micToggle = () => {
    voiceDriven.current = true
    quiet.current = 0
    if (micRef.current.listening) {
      micRef.current.stop() // the sentence so far is sent
      return
    }
    run.current++
    stopSpeaking()
    if (pending.current) {
      setState(S.AWAITING_CONFIRMATION)
      openMic()
    } else {
      listen()
    }
  }

  const retry = () => {
    if (lastText.current && stateRef.current === S.ERROR && !micRef.current.problem) send(lastText.current, 'typed')
    else micToggle()
  }

  useEffect(() => {
    if (!ready) return undefined
    if (start.text) send(start.text, 'typed')
    else if (start.greet) greet()
    else if (start.listen) listen()
    return () => { // closing the panel switches everything off
      run.current++
      stopSpeaking()
      micRef.current.cancel()
    }
  }, [ready])

  return {
    state,
    chat,
    error,
    showSuggestions,
    listening: mic.listening,
    working: mic.working,
    micSupported: mic.supported,
    speaking: SPEAKING.includes(state),
    send: (text) => send(text, 'typed'),
    micToggle,
    pause,
    resume: micToggle,
    retry,
    confirm,
    cancelPending,
  }
}
