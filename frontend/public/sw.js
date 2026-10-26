// Service worker: lets the app be installed, and lets it open even with a weak connection.
// It keeps a copy of the app's own files (pages, images, scripts). It never stores anything from /api,
// so shop data is always fresh and never left on the phone by this file.

const CACHE = 'dukaan-saathi-v1'

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.add('/')))
  self.skipWaiting()
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((names) => Promise.all(names.filter((name) => name !== CACHE).map((name) => caches.delete(name)))),
  )
  self.clients.claim()
})

// Online: always use the latest file (and remember it). Offline: use the remembered copy.
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (event.request.method !== 'GET' || url.origin !== location.origin || url.pathname.startsWith('/api')) return

  event.respondWith(
    fetch(event.request)
      .then((response) => {
        if (response.ok) {
          const copy = response.clone()
          caches.open(CACHE).then((cache) => cache.put(event.request, copy))
        }
        return response
      })
      .catch(() => caches.match(event.request).then((saved) => saved || caches.match('/'))),
  )
})
