import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

// Keep the browser's "install this app" offer until the Settings page asks for it.
window.addEventListener('beforeinstallprompt', (event) => {
  event.preventDefault()
  window.installPrompt = event
})

// Makes the app installable. Only in the real (built) app, not while developing.
if ('serviceWorker' in navigator && import.meta.env.PROD) {
  navigator.serviceWorker.register('/sw.js')
}

createRoot(document.getElementById('root')).render(<App />)
