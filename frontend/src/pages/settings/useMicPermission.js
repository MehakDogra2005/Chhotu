import { useEffect, useState } from 'react'

/** Whether the browser lets this page use the microphone: 'granted', 'denied', 'prompt' (not asked yet) or 'unknown'. */
export default function useMicPermission() {
  const [state, setState] = useState('unknown')

  useEffect(() => {
    navigator.permissions?.query({ name: 'microphone' })
      .then((status) => {
        setState(status.state)
        status.onchange = () => setState(status.state)
      })
      .catch(() => setState('unknown'))
  }, [])

  // Asks the browser for permission (it shows its own "Allow microphone?" box), then lets go of the mic straight away.
  const request = () =>
    navigator.mediaDevices.getUserMedia({ audio: true })
      .then((stream) => { stream.getTracks().forEach((track) => track.stop()); setState('granted') })
      .catch(() => setState('denied'))

  return { state, request }
}
