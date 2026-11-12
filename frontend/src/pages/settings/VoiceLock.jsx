import { useRef, useState } from 'react'
import { Lock, Mic } from 'lucide-react'
import { del } from '../../api'
import { useAuth } from '../../AuthContext'
import useFetch from '../../useFetch'
import { Card, CardHeader } from '../../components/ui'
import { microphoneBlocked, openMic } from '../../components/mic'

// The person reads these out, one at a time. Any words work: what matters is that it is their own voice.
const SENTENCES = [
  'Rahul ne paanch sau rupaye de diye hain',
  'Chocolate ka stock kitna bacha hai aaj',
  'Mujhe aaj ki total sale batao',
  'Rohan ko teen sau ka udhaar likh do',
  'Is hafte kaun sa product sabse zyada bika',
]

/** Settings > Voice lock: record 5 sentences so that only this person's voice works with Munim Ji. */
export default function VoiceLock() {
  const { token } = useAuth()
  const { data: lock, reload } = useFetch('/api/voicelock')
  const [recording, setRecording] = useState(false) // is the 5-sentence recording in progress
  const [sentence, setSentence] = useState(0)       // which of the 5 is being read now
  const [listening, setListening] = useState(false)
  const [message, setMessage] = useState({ text: '', good: false })
  const clips = useRef([])                          // the sentences recorded so far

  const finish = (text, good) => {
    setRecording(false)
    setListening(false)
    setMessage({ text, good })
  }

  const upload = async () => {
    setListening(false)
    setMessage({ text: 'Saving your voice...', good: true })
    const form = new FormData()
    clips.current.forEach((clip, i) => form.append('audio', clip, `sentence${i + 1}.wav`))
    try {
      const res = await fetch('/api/voicelock/enroll', { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form })
      const data = await res.json().catch(() => ({}))
      if (!res.ok) throw new Error(data.message || 'Your voice could not be saved')
      finish('Done. From now on only your voice works with Munim Ji.', true)
      reload()
    } catch (err) {
      finish(err.message, false)
    }
  }

  const readNext = async () => {
    setMessage({ text: '', good: false })
    setListening(true)
    try {
      await openMic({
        once: true,
        onSentence: (clip) => {
          clips.current.push(clip)
          if (clips.current.length === SENTENCES.length) return upload()
          setSentence(clips.current.length)
          return setListening(false)
        },
        onNoSpeech: () => {
          setListening(false)
          setMessage({ text: 'We did not hear you. Tap the microphone and read the sentence again.', good: false })
        },
      })
    } catch (err) {
      finish(microphoneBlocked(err) ? 'Allow the microphone in your browser, then try again.' : 'The microphone did not work.', false)
    }
  }

  const start = () => {
    clips.current = []
    setSentence(0)
    setRecording(true)
    setMessage({ text: '', good: false })
  }

  const remove = async () => {
    await del('/api/voicelock', token)
    setMessage({ text: 'Your voice was removed. Everyone can speak to Munim Ji again.', good: true })
    reload()
  }

  return (
    <Card className="p-5">
      <CardHeader icon={<Lock size={22} />} tint="bg-[#e1f5e8] text-brand" title="Voice lock"
        subtitle="Only your voice works with Munim Ji. Other people who speak to it are ignored." />

      <p className="mt-4 text-[13.5px]">
        {lock?.on ? 'Voice lock is on: Munim Ji only listens to your voice.' : 'Voice lock is off: anyone can speak to Munim Ji.'}
      </p>

      {recording ? (
        <div className="mt-4 rounded-xl bg-panel p-4 text-center">
          <p className="text-[12.5px] text-muted">Sentence {sentence + 1} of {SENTENCES.length}. Tap the microphone and read it in your normal voice.</p>
          <p className="mt-2 text-[17px] font-semibold">“{SENTENCES[sentence]}”</p>
          <button onClick={readNext} disabled={listening} aria-label="Record this sentence"
            className={`mx-auto mt-4 grid h-14 w-14 place-items-center rounded-full text-white ${listening ? 'animate-pulse bg-red-500' : 'bg-brand'}`}>
            <Mic size={26} />
          </button>
          <p className="mt-2 text-[12.5px] text-muted">{listening ? 'Listening... read the sentence now.' : 'Speak clearly, in a quiet place.'}</p>
        </div>
      ) : (
        <div className="mt-4 flex flex-wrap gap-3">
          <button onClick={start} className="h-11 rounded-xl bg-brand px-5 text-[14px] font-semibold text-white hover:bg-brand-dark">
            {lock?.on ? 'Record my voice again' : 'Record my voice'}
          </button>
          {lock?.on && <button onClick={remove} className="h-11 rounded-xl border border-line bg-white px-5 text-[14px] font-semibold hover:bg-stone-50">Remove my voice</button>}
        </div>
      )}

      {message.text && <p className={`mt-3 text-[13px] ${message.good ? 'text-brand' : 'text-red-600'}`}>{message.text}</p>}
      <p className="mt-3 text-[12px] text-muted">
        Only a list of numbers made from your voice is saved, never the recordings. It works when Munim Ji listens through the Gnani speech service,
        and it is a convenience, not a safe lock: a noisy shop or a cold can make it miss your voice. You can remove it any time.
      </p>
    </Card>
  )
}
