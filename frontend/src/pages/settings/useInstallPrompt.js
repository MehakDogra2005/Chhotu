import { useEffect, useState } from 'react'

/**
 * Returns a function that shows the browser's "Install app" box, or null when that is not possible right now
 * (already installed, or the browser does not offer it, for example Safari on iPhone).
 */
export default function useInstallPrompt() {
  const [prompt, setPrompt] = useState(window.installPrompt || null) // saved early by main.jsx

  useEffect(() => {
    const onReady = (event) => { event.preventDefault(); setPrompt(event) }
    const onInstalled = () => setPrompt(null)
    window.addEventListener('beforeinstallprompt', onReady)
    window.addEventListener('appinstalled', onInstalled)
    return () => {
      window.removeEventListener('beforeinstallprompt', onReady)
      window.removeEventListener('appinstalled', onInstalled)
    }
  }, [])

  return prompt ? () => prompt.prompt().then(() => { window.installPrompt = null; setPrompt(null) }) : null
}
