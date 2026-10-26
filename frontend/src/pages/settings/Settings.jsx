import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { Settings as SettingsIcon } from 'lucide-react'
import { put } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import PageHeader from '../../components/PageHeader'
import { Card } from '../../components/ui'
import { AccountCard, InstallApp, NeedHelp, Notifications, ShopProfile, VoicePreferences } from './SettingsCards'

export default function Settings() {
  const { reloadMe } = useOutletContext() // refreshes the name and shop in the top bar after saving
  const { token, logout } = useAuth()
  const { data, error } = useFetch('/api/settings')
  const [form, setForm] = useState(null) // what is on screen, which may not be saved yet
  const [status, setStatus] = useState(null) // { ok: true/false, text }
  const [saving, setSaving] = useState(false)

  useEffect(() => { if (data) setForm(data) }, [data])

  if (error) return <p className="p-10 text-center text-red-600">{error}</p>
  if (!form) return <p className="p-10 text-center text-muted">Loading your settings...</p>

  const change = (field, value) => {
    setForm({ ...form, [field]: value })
    setStatus(null)
  }

  const save = async () => {
    setSaving(true)
    try {
      setForm(await put('/api/settings', { ...form, voiceMode: 'PUSH_TO_TALK' }, token))
      setStatus({ ok: true, text: 'Your settings are saved.' })
      reloadMe()
    } catch (err) {
      setStatus({ ok: false, text: err.message })
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <PageHeader icon={<SettingsIcon size={46} strokeWidth={1.4} className="text-brand" />}
        title="Settings" subtitle="Manage your shop, voice and notification preferences." />

      <div className="mt-5 grid grid-cols-1 gap-4 xl:grid-cols-[1fr_330px]">
        <div className="space-y-4">
          <ShopProfile form={form} change={change} />
          <VoicePreferences form={form} change={change} />
          <Notifications form={form} change={change} />

          <Card className="flex items-center gap-4 p-4">
            <p className={`flex-1 text-[14px] ${status?.ok ? 'text-brand' : 'text-red-600'}`}>{status?.text}</p>
            <button onClick={save} disabled={saving} className="h-12 rounded-xl bg-brand px-8 text-[15px] font-semibold text-white hover:bg-brand-dark disabled:opacity-60">
              {saving ? 'Saving...' : 'Save changes'}
            </button>
          </Card>
        </div>

        <div className="space-y-4">
          <AccountCard settings={form} onLogout={logout} />
          <InstallApp />
          <NeedHelp />
        </div>
      </div>
    </>
  )
}
