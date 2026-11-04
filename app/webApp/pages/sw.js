const name = 'networkhelm-@VERSION@';

self.addEventListener('install', () => self.skipWaiting());

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys.filter(key => key !== name).map(key => caches.delete(key))))
            .then(() => self.clients.claim()),
    );
});

// the files of the page come from the network while it is there and are kept; kept ones stand in when it is away. The
// requests to the devices are of other origins and go past.
self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET' || new URL(request.url).origin !== self.location.origin) return;
    event.respondWith(
        fetch(request)
            .then(response => {
                if (response.ok) {
                    const copy = response.clone();
                    caches.open(name).then(cache => cache.put(request, copy));
                }
                return response;
            })
            .catch(() => caches.match(request)),
    );
});
