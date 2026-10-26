import { useEffect, useRef } from 'react'
import { get } from '../../api'

/**
 * Returns a function that opens Google's sign-in popup and passes the access token to onToken.
 * The client id and Google's script are loaded when the page opens, so the popup can start
 * straight from the click (browsers block popups that open late).
 */
export default function useGoogleLogin(onToken, onError) {
  const clientId = useRef('')

  useEffect(() => {
    get('/api/auth/config').then((config) => { clientId.current = config.googleClientId })
    if (!document.getElementById('google-script')) {
      const script = document.createElement('script')
      script.id = 'google-script'
      script.src = 'https://accounts.google.com/gsi/client'
      script.async = true
      document.head.appendChild(script)
    }
  }, [])

  return () => {
    if (!clientId.current) return onError('Google sign-in is not set up yet. See the README to add your Google client ID.')
    if (!window.google) return onError('Google is still loading. Please try again in a moment.')
    window.google.accounts.oauth2.initTokenClient({
      client_id: clientId.current,
      scope: 'openid email profile',
      callback: (response) => (response.access_token ? onToken(response.access_token) : onError('Google sign-in was cancelled')),
      error_callback: () => onError('Google sign-in was cancelled'),
    }).requestAccessToken()
  }
}
