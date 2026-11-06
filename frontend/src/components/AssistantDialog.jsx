import ConversationPanel from './voice/ConversationPanel'
import useVoiceConversation from './voice/useVoiceConversation'

/**
 * Talk to Munim Ji. The panel and its voice conversation live in components/voice/.
 * `start` says how it was opened: { greet: true } greets and explains first, { listen: true } listens at once,
 * { text } sends that sentence now, { hint } replaces the first line, { reply } continues an answer waiting for a yes.
 */
export default function AssistantDialog({ start, ready, speakReplies, userName, keepListening, onKeepListening, onDraft, onClose }) {
  const voice = useVoiceConversation({ start, ready, speakReplies, userName, onDraft })
  return <ConversationPanel voice={voice} keepListening={keepListening} onKeepListening={onKeepListening} onClose={onClose} />
}
