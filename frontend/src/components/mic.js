// Records from the microphone and cuts what it hears into sentences, each returned as a small WAV file (16 kHz, mono).
// WAV is used because the speech service does not read the browser's own recording formats (webm/opus).

const RATE = 16000
const FRAME = 4096              // samples per processing step (about 85 ms at 48 kHz)
const MIN_LEVEL = 0.02          // quietest sound that can count as speech
const MAX_NOISE = 0.01          // a noisy room raises the bar for speech, but never above 3 x this
const START_MS = 120            // speech must last this long before a sentence begins
const END_SILENCE_MS = 900      // this much quiet ends the sentence
const MIN_SPEECH_MS = 300       // shorter sounds (a cough, a click) are dropped
const MAX_SENTENCE_MS = 20000   // a very long sentence is cut here so results keep coming
const NO_SPEECH_MS = 7000       // when listening once: give up if nobody starts talking
const PRE_ROLL_FRAMES = 3       // keep the sound just before speech starts, so the first word is not clipped

// Runs on the audio thread, so a busy page cannot make it miss sound: it hands over the sound in blocks of FRAME samples.
const WORKLET = `
class Capture extends AudioWorkletProcessor {
  constructor() { super(); this.block = new Float32Array(${FRAME}); this.at = 0 }
  process(inputs) {
    const sound = inputs[0][0]
    if (!sound) return true
    for (let i = 0; i < sound.length; i++) {
      this.block[this.at++] = sound[i]
      if (this.at === this.block.length) { this.port.postMessage(this.block.slice(0)); this.at = 0 }
    }
    return true
  }
}
registerProcessor('capture', Capture)`

/** Calls onFrame(Float32Array) for every block of sound. Returns a function that stops it. */
async function capture(context, source, mute, onFrame) {
  if (context.audioWorklet) {
    const url = URL.createObjectURL(new Blob([WORKLET], { type: 'application/javascript' }))
    try {
      await context.audioWorklet.addModule(url)
      const node = new AudioWorkletNode(context, 'capture', { numberOfInputs: 1, numberOfOutputs: 1, channelCount: 1 })
      node.port.onmessage = (event) => onFrame(event.data)
      source.connect(node)
      node.connect(mute)
      return () => { node.port.onmessage = null; node.disconnect() }
    } catch {
      // the worklet could not start (blocked or unsupported): use the page's own thread below
    } finally {
      URL.revokeObjectURL(url)
    }
  }
  // Older browsers, or when the worklet failed: the same on the page's own thread
  const processor = context.createScriptProcessor(FRAME, 1, 1)
  processor.onaudioprocess = (event) => onFrame(new Float32Array(event.inputBuffer.getChannelData(0)))
  source.connect(processor)
  processor.connect(mute)
  return () => { processor.onaudioprocess = null; processor.disconnect() }
}

export const microphoneBlocked = (err) => ['NotAllowedError', 'SecurityError', 'PermissionDeniedError'].includes(err?.name)

/**
 * Opens the microphone. onSentence(blob) gets each sentence as a WAV file. With once: true it stops after the first
 * sentence (or calls onNoSpeech if nothing is said). Returns a controller:
 *   setCapturing(false) pauses it (what is heard meanwhile is thrown away), setCapturing(true) resumes,
 *   finish() ends the sentence being spoken right now, close() switches the microphone off.
 */
export async function openMic({ onSentence, onNoSpeech, once = false }) {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, channelCount: 1 } })
  let closed = false
  let context
  let source
  let mute
  try {
    const Context = window.AudioContext || window.webkitAudioContext
    context = new Context()
    source = context.createMediaStreamSource(stream)
    mute = context.createGain() // the capture node only runs when connected onward; this keeps the sound off the speakers
    mute.gain.value = 0
    mute.connect(context.destination)
  } catch (err) {
    stream.getTracks().forEach((t) => t.stop()) // never leave the microphone on after a failed start
    context?.close().catch(() => {})
    throw err
  }
  // Not awaited: without a recent tap the browser may keep the context suspended, and the microphone must not wait on that.
  // If the browser suspends it later (an incoming call, a background tab), start it again.
  context.resume().catch(() => {})
  context.onstatechange = () => {
    if (!closed && context.state !== 'running' && context.state !== 'closed') context.resume().catch(() => {})
  }

  const frameMs = (FRAME / context.sampleRate) * 1000
  let capturing = true
  let speaking = false
  let noise = 0
  let voicedMs = 0
  let silenceMs = 0
  let sentenceMs = 0
  let waitedMs = 0
  let preRoll = []
  let chunks = []

  const reset = () => {
    speaking = false
    voicedMs = 0
    silenceMs = 0
    sentenceMs = 0
    chunks = []
    preRoll = []
  }

  let stopCapture = () => {}
  const close = () => {
    if (closed) return
    closed = true
    context.onstatechange = null
    stopCapture()
    source.disconnect()
    mute.disconnect()
    stream.getTracks().forEach((t) => t.stop())
    context.close().catch(() => {})
  }

  const endSentence = () => {
    const speechMs = sentenceMs - silenceMs
    const heard = chunks
    reset()
    if (speechMs >= MIN_SPEECH_MS && heard.length) onSentence(toWav(heard, context.sampleRate))
    if (once) close()
  }

  const onFrame = (frame) => {
    if (closed || !capturing) return
    const level = Math.sqrt(frame.reduce((sum, x) => sum + x * x, 0) / frame.length)
    const threshold = Math.max(MIN_LEVEL, noise * 3)
    const voiced = level > threshold

    if (!speaking) {
      // Learn how loud the room is, only while nobody is speaking: a dip between two words must not raise it
      if (!voiced) noise = Math.min(noise === 0 ? level : noise * 0.97 + level * 0.03, MAX_NOISE)
      waitedMs += frameMs
      voicedMs = voiced ? voicedMs + frameMs : 0
      preRoll.push(frame)
      if (preRoll.length > PRE_ROLL_FRAMES + Math.ceil(START_MS / frameMs)) preRoll.shift()
      if (voicedMs >= START_MS) {
        speaking = true
        chunks = preRoll
        preRoll = []
        sentenceMs = chunks.length * frameMs
        silenceMs = 0
      } else if (once && waitedMs >= NO_SPEECH_MS) {
        close()
        onNoSpeech?.()
      }
      return
    }

    chunks.push(frame)
    sentenceMs += frameMs
    silenceMs = voiced ? 0 : silenceMs + frameMs
    if (silenceMs >= END_SILENCE_MS || sentenceMs >= MAX_SENTENCE_MS) endSentence()
  }

  try {
    stopCapture = await capture(context, source, mute, onFrame)
  } catch (err) {
    close() // do not leave the microphone on when the recorder could not start
    throw err
  }

  return {
    setCapturing(on) {
      if (on === capturing) return
      capturing = on
      waitedMs = 0
      reset()
    },
    finish() {
      if (closed) return
      if (speaking) endSentence()
      else {
        close()
        if (once) onNoSpeech?.()
      }
    },
    close,
  }
}

/** The frames as a 16 kHz, 16-bit, mono WAV file. */
function toWav(frames, fromRate) {
  const length = frames.reduce((n, f) => n + f.length, 0)
  const all = new Float32Array(length)
  let at = 0
  for (const f of frames) {
    all.set(f, at)
    at += f.length
  }
  const samples = downsample(all, fromRate)
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const text = (offset, s) => [...s].forEach((c, i) => view.setUint8(offset + i, c.charCodeAt(0)))
  text(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  text(8, 'WAVE')
  text(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)        // PCM
  view.setUint16(22, 1, true)        // mono
  view.setUint32(24, RATE, true)
  view.setUint32(28, RATE * 2, true) // bytes per second
  view.setUint16(32, 2, true)        // bytes per sample
  view.setUint16(34, 16, true)       // bits per sample
  text(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  samples.forEach((x, i) => view.setInt16(44 + i * 2, Math.max(-1, Math.min(1, x)) * 0x7fff, true))
  return new Blob([buffer], { type: 'audio/wav' })
}

/** Lowers the sample rate by averaging, which also keeps the high-pitched noise out. */
function downsample(samples, fromRate) {
  if (fromRate <= RATE) return samples
  const ratio = fromRate / RATE
  const out = new Float32Array(Math.floor(samples.length / ratio))
  for (let i = 0; i < out.length; i++) {
    const from = Math.floor(i * ratio)
    const to = Math.min(Math.floor((i + 1) * ratio), samples.length)
    let sum = 0
    for (let j = from; j < to; j++) sum += samples[j]
    out[i] = sum / Math.max(to - from, 1)
  }
  return out
}
