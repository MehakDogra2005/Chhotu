import { createContext, useContext, useState } from 'react'
import AssistantDialog from './AssistantDialog'

const AssistantContext = createContext(null)

/** Anything on any page can call ask() to open Saathi: ask({ text }), ask({ listen: true }), ask({ hint }). */
export const useAssistant = () => useContext(AssistantContext)

export function AssistantProvider({ speakReplies, children }) {
  const [start, setStart] = useState(null) // null = closed

  return (
    <AssistantContext.Provider value={{ ask: (options = {}) => setStart({ ...options, id: Date.now() }) }}>
      {children}
      {/* a new id makes a fresh conversation each time it is opened, and keeps this one while it stays open */}
      {start && <AssistantDialog key={start.id} start={start} speakReplies={speakReplies} onClose={() => setStart(null)} />}
    </AssistantContext.Provider>
  )
}
