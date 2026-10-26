import { useEffect, useRef, useState } from 'react'

// Speech comes from the browser itself (Chrome and Android Chrome). It is free and needs no setup.
// It is tuned for Indian English, which also copes with Hindi spoken in English words ("Hinglish").
const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition

/** Listening with the microphone. start() listens once and gives the words to onText. */
export function useListening(onText) {
  const recogniser = useRef(null)
  const [listening, setListening] = useState(false)
  const [problem, setProblem] = useState('')

  // Closing the window (or leaving the page) must switch the microphone off straight away.
  useEffect(() => () => recogniser.current?.abort(), [])

  const start = () => {
    if (!Recognition) return
    setProblem('')
    const r = new Recognition()
    r.lang = 'en-IN'
    r.interimResults = false
    r.onresult = (event) => onText(event.results[0][0].transcript)
    r.onerror = (event) => setProblem(
      event.error === 'not-allowed' ? 'The microphone is blocked. Allow it in your browser settings, or type instead.'
        : event.error === 'no-speech' ? 'I did not hear anything. Tap the mic and try again.'
        : 'The microphone did not work. You can type instead.')
    r.onend = () => setListening(false)
    recogniser.current = r
    r.start()
    setListening(true)
  }

  return { supported: !!Recognition, listening, problem, start, stop: () => recogniser.current?.stop() }
}

/** Reads text aloud. */
export function speak(text) {
  if (!('speechSynthesis' in window)) return
  const voice = new SpeechSynthesisUtterance(text.replace(/[•\n]+/g, '. '))
  voice.lang = 'en-IN'
  window.speechSynthesis.cancel()
  window.speechSynthesis.speak(voice)
}
