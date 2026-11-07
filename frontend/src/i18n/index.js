import { setVoiceLanguage } from '../components/speech'

/*
 * Page language. The text in the pages is written in English in the code. Each language file here (hi.json, ta.json ...) maps an
 * English text to its translation. When a language is picked, every text on the screen that has a translation is swapped; texts
 * that are not in the file (names, numbers, anything from the database) stay exactly as they are.
 * The files are made by a script that asks Gemini once, so there is no translation call while the app is used.
 */
export const PAGE_LANGUAGES = [
  { code: 'en', label: 'English', voice: 'en-IN' },
  { code: 'hi', label: 'हिन्दी', voice: 'en-IN' },
  { code: 'pa', label: 'ਪੰਜਾਬੀ', voice: 'pa-IN' },
  { code: 'ta', label: 'தமிழ்', voice: 'ta-IN' },
  { code: 'te', label: 'తెలుగు', voice: 'te-IN' },
  { code: 'kn', label: 'ಕನ್ನಡ', voice: 'kn-IN' },
  { code: 'ml', label: 'മലയാളം', voice: 'ml-IN' },
  { code: 'mr', label: 'मराठी', voice: 'mr-IN' },
  { code: 'bn', label: 'বাংলা', voice: 'bn-IN' },
  { code: 'gu', label: 'ગુજરાતી', voice: 'gu-IN' },
]

const KEY = 'galla.pagelang'
const ATTRIBUTES = ['placeholder', 'title', 'aria-label']
const SKIP = new Set(['SCRIPT', 'STYLE', 'TEXTAREA', 'NOSCRIPT'])
const files = import.meta.glob('./*.json')

let fixed = null // English text -> translation (from the language file), null while the page is in English
let names = {} // the shop's product and customer names -> translation (from the server)
let words = null // both together
let token = null
let timer = 0
const seen = new WeakMap() // text node or element -> { original, shown }

const listeners = new Set()
/** For React: lets a component start over when the language changes, so dates and other computed text are written again. */
export function subscribeLanguage(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** The locale for dates: month names in the page language. */
export function dateLocale() {
  const code = getPageLanguage()
  return code === 'en' ? 'en-IN' : `${code}-IN`
}

export function getPageLanguage() {
  try { return localStorage.getItem(KEY) || 'en' } catch { return 'en' }
}

function swap(original) {
  const key = original.trim()
  const to = words && words[key]
  return to ? original.replace(key, to) : original
}

/** Translates one text node, remembering the English so it can go back. */
function text(node) {
  const memo = seen.get(node)
  const now = node.nodeValue
  const original = memo && memo.shown === now ? memo.original : now // React wrote new text: that is the new English
  const shown = swap(original)
  if (shown !== now) node.nodeValue = shown
  if (shown !== original || memo) seen.set(node, { original, shown })
}

function attributes(el) {
  for (const name of ATTRIBUTES) {
    const now = el.getAttribute(name)
    if (!now) continue
    const slot = seen.get(el) || {}
    const memo = slot[name]
    const original = memo && memo.shown === now ? memo.original : now
    const shown = swap(original)
    if (shown !== now) el.setAttribute(name, shown)
    if (shown !== original || memo) { slot[name] = { original, shown }; seen.set(el, slot) }
  }
}

function walk(root) {
  if (root.nodeType === Node.TEXT_NODE) { if (!SKIP.has(root.parentNode?.nodeName)) text(root); return }
  if (root.nodeType !== Node.ELEMENT_NODE || SKIP.has(root.nodeName) || root.closest?.('[data-notranslate]')) return
  attributes(root)
  for (const child of root.childNodes) walk(child)
}

let queue = new Set()
let frame = 0
function later(node) {
  queue.add(node)
  if (!frame) {
    frame = requestAnimationFrame(() => {
      const nodes = queue
      queue = new Set()
      frame = 0
      nodes.forEach((n) => { if (n.isConnected) walk(n) })
    })
  }
}

const watcher = new MutationObserver((list) => {
  for (const m of list) {
    if (m.type === 'childList') m.addedNodes.forEach(later)
    else later(m.target)
  }
})

async function load(code) {
  const file = files[`./${code}.json`]
  fixed = file ? (await file()).default : null
  names = {}
  words = fixed
  await loadNames()
}

/** The shop's own names in this language. A new name takes a few seconds to be translated, so the page asks again now and then. */
async function loadNames() {
  const code = getPageLanguage()
  if (!token || code === 'en') return
  try {
    const res = await fetch(`/api/translations?language=${code}`, { headers: { Authorization: `Bearer ${token}` } })
    if (!res.ok || getPageLanguage() !== code) return
    const next = await res.json()
    const changed = JSON.stringify(next) !== JSON.stringify(names)
    names = next
    words = { ...names, ...fixed }
    if (changed) walk(document.body)
  } catch { /* offline: the names stay as typed */ }
}

/** Called once the person is logged in (and with null when they log out). */
export function startNameTranslations(loginToken) {
  token = loginToken
  clearInterval(timer)
  if (!loginToken) return
  loadNames()
  timer = setInterval(loadNames, 15000)
}

export async function setPageLanguage(code) {
  try { localStorage.setItem(KEY, code) } catch { /* the choice just is not remembered */ }
  await load(code)
  document.documentElement.lang = code
  walk(document.body)
  listeners.forEach((l) => l())
}

/** Call once, before the app is drawn. Keeps translating whatever the app draws later. */
export function startTranslator() {
  watcher.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRIBUTES })
  setVoiceLanguage('') // Munim Ji finds out the language from what the person says; an old choice must not force one
  const code = getPageLanguage()
  document.documentElement.lang = code
  if (code !== 'en') load(code).then(() => walk(document.body))
}
