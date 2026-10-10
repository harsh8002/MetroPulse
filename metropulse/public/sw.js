const CACHE_NAME = 'metropulse-shell-v2';
const APP_SHELL = ['/', '/?view=employee', '/?view=admin', '/styles.css?v=1.2.0', '/app.js?v=1.2.0', '/manifest.json', '/icon.svg'];
self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', event => {
  event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', event => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) return;
  if (req.mode === 'navigate') {
    event.respondWith(fetch(req).then(response => {
      const copy = response.clone(); caches.open(CACHE_NAME).then(cache => cache.put('/', copy)); return response;
    }).catch(async () => (await caches.match(req)) || (await caches.match('/')) || Response.error()));
    return;
  }
  event.respondWith(caches.match(req).then(cached => cached || fetch(req).then(response => {
    if (response.ok) { const copy = response.clone(); caches.open(CACHE_NAME).then(cache => cache.put(req, copy)); }
    return response;
  })));
});
