import { useState } from 'react'
import { refreshData } from '../useFetch'

/**
 * For a dialog that saves something. run(action) gives a submit handler that shows "busy" while saving,
 * puts any error message in the dialog, and when it works refreshes the page's numbers and closes the dialog.
 */
export default function useSubmit(onClose) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const run = (action) => async (event) => {
    event?.preventDefault()
    setBusy(true)
    setError('')
    try {
      await action()
      refreshData()
      onClose()
    } catch (err) {
      setError(err.message)
      setBusy(false)
    }
  }

  return { busy, error, run }
}
