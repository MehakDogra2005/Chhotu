import { NavLink } from 'react-router-dom'
import { AudioLines, Bell, CircleHelp, Mail, Mic, Phone, ShieldCheck, Store, UserRound } from 'lucide-react'
import { CATEGORIES } from '../../categories'
import { Card, CardHeader, Field, Toggle, inputStyle } from '../../components/ui'
import useMicPermission from './useMicPermission'

const LANGUAGES = ['English', 'Hindi', 'Hinglish']

// A value saved earlier (for example from sign-up) may not be in our list; keep it selectable.
const withCurrent = (list, current) => (current && !list.includes(current) ? [current, ...list] : list)

/** form is the settings being edited; change(field, value) updates one of them. */
export function ShopProfile({ form, change }) {
  const text = (field) => ({ value: form[field] || '', onChange: (e) => change(field, e.target.value), className: inputStyle })

  return (
    <Card className="p-5">
      <CardHeader viewAll={false} icon={<Store size={22} />} tint="bg-[#e1f5e8] text-brand"
        title="Shop Profile" subtitle="How you and your shop appear in Saathi." />
      <div className="mt-5 grid gap-4 sm:grid-cols-2">
        <Field label="Your name"><input {...text('ownerName')} /></Field>
        <Field label="Shop name"><input {...text('shopName')} /></Field>
        <Field label="Business category">
          <select {...text('category')}>
            {withCurrent(CATEGORIES, form.category).map((c) => <option key={c}>{c}</option>)}
          </select>
        </Field>
        <Field label="City"><input {...text('city')} placeholder="e.g. Delhi" /></Field>
        <Field label="Preferred language">
          <select {...text('language')}>
            {withCurrent(LANGUAGES, form.language).map((l) => <option key={l}>{l}</option>)}
          </select>
        </Field>
      </div>
    </Card>
  )
}

const MODES = [
  ['PUSH_TO_TALK', Mic, 'Push to talk', 'Press the mic when you want to speak.'],
  ['HANDS_FREE', AudioLines, 'Hands-free', 'Saathi keeps listening until you pause it.'],
]

const MIC_STATUS = {
  granted: ['text-brand', 'Allowed: your browser lets Saathi use the microphone.'],
  denied: ['text-red-600', "Blocked: allow the microphone in your browser's site settings, then reload."],
  prompt: ['text-orange', 'Not allowed yet: your browser will ask when you press Allow.'],
  unknown: ['text-muted', 'Your browser has not said whether the microphone is allowed.'],
}

export function VoicePreferences({ form, change }) {
  const mic = useMicPermission()
  const [statusColour, statusText] = MIC_STATUS[mic.state]

  return (
    <Card className="p-5">
      <CardHeader viewAll={false} icon={<Mic size={22} />} tint="bg-[#e3ecfb] text-[#2563c9]"
        title="Voice Preferences" subtitle="Choose how you talk to Saathi. Voice is coming soon; your choices are saved for it." />

      <div className="mt-5 grid gap-3 sm:grid-cols-2">
        {MODES.map(([value, Icon, title, text]) => {
          const selected = form.voiceMode === value
          return (
            <button key={value} type="button" aria-pressed={selected} onClick={() => change('voiceMode', value)}
              className={`flex items-start gap-3 rounded-xl border p-4 text-left ${selected ? 'border-brand bg-mint' : 'border-line bg-white hover:bg-stone-50'}`}>
              <Icon size={22} className={selected ? 'text-brand' : 'text-muted'} />
              <span>
                <span className="block text-[15px] font-semibold">{title}</span>
                <span className="block text-[13px] text-muted">{text}</span>
              </span>
            </button>
          )
        })}
      </div>

      <div className="mt-2 divide-y divide-edge">
        <Toggle checked={form.voiceReplies} onChange={(v) => change('voiceReplies', v)}
          label="Speak replies aloud" description="Saathi reads its answers out as well as showing them." />
        <div className="flex flex-wrap items-center gap-4 py-3">
          <div className="min-w-0 flex-1">
            <div className="text-[15px] font-semibold">Microphone access</div>
            <div className={`text-[13px] ${statusColour}`}>{statusText}</div>
          </div>
          {mic.state !== 'granted' && (
            <button type="button" onClick={mic.request} className="rounded-xl border border-brand px-4 py-2 text-[14px] font-semibold text-brand hover:bg-mint">
              Allow microphone
            </button>
          )}
        </div>
      </div>
    </Card>
  )
}

export function Notifications({ form, change }) {
  return (
    <Card className="p-5">
      <CardHeader viewAll={false} icon={<Bell size={22} />} tint="bg-[#fdebd0] text-orange"
        title="Notifications" subtitle="Choose what Saathi should tell you about." />
      <div className="mt-3 divide-y divide-edge">
        <Toggle checked={form.lowStockAlerts} onChange={(v) => change('lowStockAlerts', v)}
          label="Low-stock alerts" description="Show a red number on the bell when products are low or out of stock." />
        <Toggle checked={form.paymentReminders} onChange={(v) => change('paymentReminders', v)}
          label="Overdue payment reminders" description="Remind me about customers who have not paid by their due date." />
      </div>
    </Card>
  )
}

export function AccountCard({ settings, onLogout }) {
  const rows = [
    [UserRound, 'Role', 'Owner'],
    settings.phone && [Phone, 'Mobile', `+91 ${settings.phone}`],
    settings.email && [Mail, 'Email', settings.email],
  ].filter(Boolean)

  return (
    <Card className="p-5">
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 place-items-center rounded-xl bg-[#e1f5e8] text-brand"><ShieldCheck size={22} /></span>
        <h3 className="text-[16px] font-bold">Account &amp; Security</h3>
      </div>
      <ul className="mt-4 space-y-3">
        {rows.map(([Icon, label, value]) => (
          <li key={label} className="flex items-center gap-3 text-[14px]">
            <Icon size={18} className="shrink-0 text-muted" />
            <span className="w-14 shrink-0 text-muted">{label}</span>
            <span className="min-w-0 break-words font-medium">{value}</span>
          </li>
        ))}
      </ul>
      <p className="mt-4 rounded-xl bg-secure p-3 text-[13px] leading-relaxed text-muted">
        Your shop's data is private. Other shops can never see it.
      </p>
      <button onClick={onLogout} className="mt-4 h-11 w-full rounded-xl border border-red-200 bg-white text-[14px] font-semibold text-red-600 hover:bg-red-50">
        Log out
      </button>
    </Card>
  )
}

export function NeedHelp() {
  return (
    <div className="rounded-2xl border border-[#d5ecdd] bg-linear-to-b from-[#eaf8ef] to-[#f4fbf6] p-5">
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 place-items-center rounded-full bg-white text-brand shadow-sm"><CircleHelp size={20} /></span>
        <h3 className="text-[16px] font-bold">Need help?</h3>
      </div>
      <p className="mt-3 text-[13px] text-muted">Quick answers about stock, udhaar and profit, and what you can ask Saathi.</p>
      <NavLink to="/help" className="mt-4 flex h-11 items-center justify-center rounded-xl bg-brand text-[14px] font-semibold text-white hover:bg-brand-dark">
        Open Help
      </NavLink>
    </div>
  )
}
