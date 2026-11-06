// The states of a conversation with Munim Ji. Only one is true at a time, so the screen can never show, for example,
// "listening" and "speaking" together.
export const S = {
  IDLE: 'IDLE',                         // waiting for the shopkeeper to tap the microphone
  GREETING: 'GREETING',                 // speaking the personal greeting
  INTRODUCTION: 'INTRODUCTION',         // explaining what Munim Ji can do
  LISTENING: 'LISTENING',               // the microphone is open
  PROCESSING: 'PROCESSING',             // turning speech into words and working out what was asked
  NAVIGATING: 'NAVIGATING',             // opening the page that matches the request
  RESPONDING: 'RESPONDING',             // speaking the answer
  AWAITING_CONFIRMATION: 'AWAITING_CONFIRMATION', // a change is waiting for a yes or a no
  ERROR: 'ERROR',                       // the microphone, the network or the voice service failed
  PAUSED: 'PAUSED',                     // voice is stopped until the shopkeeper resumes
}

// What the small status line says, and the colour of its dot.
export const STATE_INFO = {
  [S.IDLE]: { label: 'Ready. Tap the mic or type', tone: 'bg-stone-400' },
  [S.GREETING]: { label: 'Munim Ji is speaking', tone: 'bg-brand' },
  [S.INTRODUCTION]: { label: 'Munim Ji is speaking', tone: 'bg-brand' },
  [S.LISTENING]: { label: 'Listening', tone: 'bg-red-500' },
  [S.PROCESSING]: { label: 'Understanding...', tone: 'bg-amber-500' },
  [S.NAVIGATING]: { label: 'Opening the page', tone: 'bg-sky-500' },
  [S.RESPONDING]: { label: 'Munim Ji is speaking', tone: 'bg-brand' },
  [S.AWAITING_CONFIRMATION]: { label: 'Waiting for your yes or no', tone: 'bg-amber-500' },
  [S.ERROR]: { label: 'Something went wrong', tone: 'bg-red-600' },
  [S.PAUSED]: { label: 'Paused', tone: 'bg-stone-400' },
}

export const SPEAKING = [S.GREETING, S.INTRODUCTION, S.RESPONDING]
