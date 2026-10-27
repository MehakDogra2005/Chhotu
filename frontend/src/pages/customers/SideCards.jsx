import { useRef, useState } from 'react'
import { FileAudio, MessageCircleQuestion, Mic, Phone, PhoneCall, Upload } from 'lucide-react'
import { useAuth } from '../../AuthContext'
import { refreshData } from '../../useFetch'
import { Card } from '../../components/ui'
import { useAssistant } from '../../components/AssistantContext'

/**
 * Questions Saathi answers from the ledger (see assistant/CommandParser.java), using real customers of this shop who owe
 * money. A customer is named by first name when no one else shares it, else by full name, so Saathi finds just one person.
 */
function questions(customers) {
  const count = (key) => (name) => customers.filter((c) => key(c.name) === key(name)).length
  const first = (n) => n.split(' ')[0]
  const label = (c) => (count(first)(c.name) === 1 ? first(c.name) : count((n) => n)(c.name) === 1 ? c.name : null)
  const [a, b] = customers.filter((c) => c.pendingPaise > 0 && label(c))
  const half = b ? Math.max(10, Math.floor(b.pendingPaise / 200 / 10) * 10) : 500
  return [
    `${a ? label(a) : 'Ramesh'} ka kitna udhaar baki hai?`,
    'Kis-kis ko payment reminder bhejna hai?',
    'Aaj ka payment register dikhao',
    `${b ? label(b) : 'Suresh'} ne ${half} rupaye de diye`,
    'Kaunse customers ka udhaar 1 mahine se zyada purana hai?',
  ]
}

export function AskSaathi({ customers }) {
  const { ask } = useAssistant()
  return (
    <Card className="bg-panel/60 p-4">
      <div className="flex items-center gap-3">
        <button onClick={() => ask({ listen: true })} aria-label="Ask Saathi by voice"
          className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-brand text-white hover:bg-brand-dark"><Mic size={20} /></button>
        <h3 className="text-[16px] font-bold">Ask Saathi about customers</h3>
      </div>
      <div className="mt-3 space-y-2">
        {questions(customers).map((q) => (
          <button key={q} onClick={() => ask({ text: q })}
            className="flex w-full items-center gap-2 rounded-full border border-edge bg-white px-3 py-2 text-left text-[13px] hover:border-brand">
            <MessageCircleQuestion size={15} className="shrink-0 text-muted" /> "{q}"
          </button>
        ))}
      </div>
    </Card>
  )
}

const TABS = [
  ['voice', 'Voice Note', Mic],
  ['calls', 'Call Recording', PhoneCall],
  ['upload', 'Upload File', Upload],
]
const MAX_MB = 20

/** How long an audio file is, read by the browser (null if it cannot tell). */
const durationOf = (file) => new Promise((done) => {
  const audio = new Audio()
  const url = URL.createObjectURL(file)
  const finish = (s) => { URL.revokeObjectURL(url); done(Number.isFinite(s) ? Math.round(s) : null) }
  audio.onloadedmetadata = () => finish(audio.duration)
  audio.onerror = () => finish(null)
  audio.src = url
})

export const seconds = (s) => (s == null ? '' : `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`)

function RecordingList({ items, onOpen, empty }) {
  if (items.length === 0) return <p className="py-4 text-center text-[12.5px] text-muted">{empty}</p>
  return (
    <ul className="mt-2 divide-y divide-edge/60">
      {items.slice(0, 4).map((r) => (
        <li key={r.recordingId}>
          <button onClick={() => onOpen(r)} className="flex w-full items-center gap-2 py-2 text-left text-[12.5px] hover:text-brand">
            <FileAudio size={16} className="shrink-0 text-muted" />
            <span className="min-w-0 flex-1 truncate">{r.fileName}</span>
            <span className="text-[11px] text-muted">{seconds(r.durationSeconds)}</span>
            <span className={`rounded px-1.5 py-0.5 text-[10.5px] font-semibold ${r.transcript ? 'bg-[#fff1dc] text-[#a36a00]' : 'bg-stone-100 text-muted'}`}>
              {r.transcript ? 'Check' : 'No text'}
            </span>
          </button>
        </li>
      ))}
    </ul>
  )
}

/** Speak to Saathi, or upload a voice note / call recording to go through later. onOpen opens a recording. */
export function AddByVoice({ recordings, onOpen }) {
  const { ask } = useAssistant()
  const { token } = useAuth()
  const [tab, setTab] = useState('voice')
  const [type, setType] = useState('VOICE_NOTE')
  const [consent, setConsent] = useState(false)
  const [status, setStatus] = useState(null) // { busy } | { error } | { done }
  const input = useRef(null)

  const startUpload = (kind) => {
    setType(kind)
    setTab('upload')
    setStatus(null)
  }

  const upload = async (file) => {
    if (!file) return
    if (file.size > MAX_MB * 1024 * 1024) return setStatus({ error: `That file is bigger than ${MAX_MB} MB.` })
    setStatus({ busy: true })
    const form = new FormData()
    form.append('file', file)
    form.append('type', type)
    form.append('consent', consent)
    const duration = await durationOf(file)
    if (duration != null) form.append('durationSeconds', duration)
    try {
      const res = await fetch('/api/recordings', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) throw new Error(data.message || 'The upload did not work')
      setStatus({ done: data })
      refreshData()
    } catch (err) {
      setStatus({ error: err.message })
    } finally {
      if (input.current) input.current.value = ''
    }
  }

  return (
    <Card className="p-4">
      <div className="flex items-center gap-3">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-[#e1f5e8] text-brand"><Mic size={20} /></span>
        <h3 className="text-[15.5px] font-bold leading-tight">Add Udhaar/Payment by Voice or Upload</h3>
      </div>
      <div className="mt-3 flex gap-3 border-b border-edge text-[12px]">
        {TABS.map(([key, label, Icon]) => (
          <button key={key} onClick={() => setTab(key)}
            className={`-mb-px flex items-center gap-1 whitespace-nowrap border-b-2 pb-2 ${tab === key ? 'border-brand font-bold text-brand' : 'border-transparent text-muted'}`}>
            <Icon size={14} /> {label}
          </button>
        ))}
      </div>

      {tab === 'voice' && (
        <>
          <button onClick={() => ask({ listen: true, hint: 'Say, for example: "Ramesh ne 500 rupaye de diye" or "Pooja ko 200 ka udhaar diya"' })}
            className="mt-3 flex w-full flex-col items-center gap-2 rounded-xl border-2 border-dashed border-edge bg-white/70 px-4 py-5 text-center hover:border-brand">
            <span className="grid h-11 w-11 place-items-center rounded-full bg-brand text-white"><Mic size={22} /></span>
            <span className="text-[13.5px] font-medium">Click to record or speak naturally</span>
            <span className="text-[11.5px] text-muted">e.g. "Ramesh ne 500 rupaye de diye"<br />"Pooja ka udhaar 200 aur add karo"</span>
          </button>
          <p className="mt-2 text-[11.5px] text-muted">Saathi shows what it will change and waits for your Yes.</p>
          <RecordingList items={recordings.filter((r) => r.type === 'VOICE_NOTE')} onOpen={onOpen} empty="No voice notes uploaded yet." />
        </>
      )}

      {tab === 'calls' && <RecordingList items={recordings.filter((r) => r.type === 'CALL_RECORDING')} onOpen={onOpen} empty="No call recordings uploaded yet." />}

      {tab === 'upload' && (
        <div className="mt-3 space-y-3 text-[13px]">
          <div className="flex gap-2">
            {[['VOICE_NOTE', 'Voice note', Mic], ['CALL_RECORDING', 'Call recording', Phone]].map(([key, label, Icon]) => (
              <button key={key} onClick={() => setType(key)}
                className={`flex flex-1 items-center justify-center gap-1.5 rounded-lg border px-2 py-2 font-medium ${type === key ? 'border-brand bg-[#e1f5e8] text-brand' : 'border-edge bg-white'}`}>
                <Icon size={15} /> {label}
              </button>
            ))}
          </div>
          {type === 'CALL_RECORDING' && (
            <label className="flex items-start gap-2 text-[12.5px]">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} className="mt-0.5" />
              The customer knew this call was being recorded.
            </label>
          )}
          <label className={`flex cursor-pointer flex-col items-center gap-1 rounded-xl border-2 border-dashed border-edge bg-white/70 px-4 py-4 text-center hover:border-brand ${status?.busy ? 'pointer-events-none opacity-60' : ''}`}>
            <Upload size={20} className="text-brand" />
            <span className="font-medium">{status?.busy ? 'Uploading...' : 'Choose an audio file'}</span>
            <span className="text-[11.5px] text-muted">m4a, mp3, wav, ogg, opus, aac, amr · up to {MAX_MB} MB</span>
            <input ref={input} type="file" accept="audio/*,.m4a,.mp3,.wav,.ogg,.opus,.webm,.aac,.amr,.3gp" className="sr-only"
              disabled={type === 'CALL_RECORDING' && !consent} onChange={(e) => upload(e.target.files[0])} />
          </label>
          {type === 'CALL_RECORDING' && !consent && <p className="text-[12px] text-muted">Tick the box above first.</p>}
          {status?.error && <p className="text-[12.5px] text-red-600">{status.error}</p>}
          {status?.done && (
            <p className="rounded-lg bg-mint p-2.5 text-[12.5px]">
              Uploaded <b>{status.done.fileName}</b>. <button onClick={() => onOpen(status.done)} className="font-semibold text-brand underline">Open it</button> to add what was said.
            </p>
          )}
          <p className="text-[11.5px] text-muted">Automatic speech-to-text is not set up yet: open a recording, listen, and type or say what it says. Nothing changes until you confirm it with Saathi.</p>
        </div>
      )}

      <div className="mt-3 grid grid-cols-2 gap-2">
        <button onClick={() => startUpload('VOICE_NOTE')} className="flex items-center justify-center gap-1.5 rounded-lg bg-[#e6effd] px-2 py-2 text-[12.5px] font-semibold text-[#1f5fbf] hover:bg-[#d3e2fb]"><Upload size={14} /> Upload Voice Note</button>
        <button onClick={() => startUpload('CALL_RECORDING')} className="flex items-center justify-center gap-1.5 rounded-lg bg-[#e6effd] px-2 py-2 text-[12.5px] font-semibold text-[#1f5fbf] hover:bg-[#d3e2fb]"><PhoneCall size={14} /> Upload Call Recording</button>
      </div>
    </Card>
  )
}
