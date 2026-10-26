import { useEffect, useState } from 'react'

/** Counts down in seconds. Returns [secondsLeft, restart]: restart(60) starts a new 60 second countdown. */
export default function useCountdown() {
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    if (secondsLeft <= 0) return
    const tick = setTimeout(() => setSecondsLeft(secondsLeft - 1), 1000)
    return () => clearTimeout(tick)
  }, [secondsLeft])

  return [secondsLeft, setSecondsLeft]
}

/** 75 becomes "1:15", 5 becomes "0:05". */
export const clock = (seconds) => `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
