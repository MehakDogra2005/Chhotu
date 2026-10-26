import Modal from './Modal'
import useSubmit from './useSubmit'

/** "Are you sure?" box. onConfirm is the thing to do; the dialog closes by itself when it worked. */
export default function ConfirmDialog({ title, message, confirmLabel, danger, onConfirm, onClose }) {
  const { busy, error, run } = useSubmit(onClose)

  return (
    <Modal title={title} onClose={onClose}>
      <div className="space-y-4 p-5">
        <p className="text-[14px] leading-relaxed">{message}</p>
        {error && <p className="text-[13px] text-red-600">{error}</p>}
        <div className="flex gap-3">
          <button onClick={onClose} className="h-11 flex-1 rounded-xl border border-line bg-white text-[14px] font-semibold hover:bg-stone-50">Cancel</button>
          <button onClick={run(onConfirm)} disabled={busy}
            className={`h-11 flex-1 rounded-xl text-[14px] font-semibold text-white disabled:opacity-60 ${danger ? 'bg-red-600 hover:bg-red-700' : 'bg-brand hover:bg-brand-dark'}`}>
            {busy ? 'Please wait...' : confirmLabel}
          </button>
        </div>
      </div>
    </Modal>
  )
}
