import { useState } from 'react'
import { useAuth } from '../../AuthContext'
import Landing from './Landing'
import AuthCard from './AuthCard'

/** Landing panel on the left, login / sign-up card on the right. */
export default function LoginPage() {
  const { login } = useAuth()
  const [mode, setMode] = useState('login') // 'login' | 'signup'
  const [lang, setLang] = useState('English')

  return (
    <div className="min-h-dvh bg-cream min-[1400px]:flex">
      <Landing lang={lang} setLang={setLang} />
      <AuthCard mode={mode} setMode={setMode} lang={lang} onLogin={login} />
    </div>
  )
}
