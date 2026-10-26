import { useEffect, useState } from 'react'
import { useAuth } from './AuthContext'
import { get } from './api'

/** Loads `url` from the backend with the login token. If the login has expired, logs out. Call reload() to load it again. */
export default function useFetch(url) {
  const { token, logout } = useAuth()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [version, setVersion] = useState(0)

  useEffect(() => {
    setError('')
    get(url, token)
      .then(setData)
      .catch((err) => (err.status === 401 ? logout() : setError(err.message)))
  }, [url, token, version])

  return { data, error, reload: () => setVersion((v) => v + 1) }
}
