import { useEffect } from 'react'
import { X } from 'lucide-react'

/** A box in the middle of the screen over a dimmed page. Closes with the X, the Esc key, or a click outside. */
export default function Modal({ title, icon, onClose, children }) {
  useEffect(() => {
    const onKey = (event) => event.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4" onMouseDown={onClose}>
      <div className="flex max-h-[92dvh] w-full max-w-lg flex-col overflow-hidden rounded-t-3xl bg-card shadow-2xl sm:rounded-3xl" onMouseDown={(e) => e.stopPropagation()}>
        <div className="flex items-center gap-3 border-b border-edge px-5 py-4">
          {icon}
          <h2 className="flex-1 text-[18px] font-bold">{title}</h2>
          <button onClick={onClose} aria-label="Close" className="grid h-9 w-9 place-items-center rounded-full hover:bg-stone-100"><X size={20} /></button>
        </div>
        {children}
      </div>
    </div>
  )
}
