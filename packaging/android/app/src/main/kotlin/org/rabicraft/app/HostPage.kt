package org.rabicraft.app

/**
 * The page RabiCraft's browser build is framed in, as text so the build needs no JavaScript of
 * its own (CLAUDE.md: Rust only in the product and build).
 *
 * The frame is what `?host=parent` expects (`apps/rabicraft-web/src/main.rs`, `mod host`): saves
 * arrive here as `rabicraft:save` messages instead of a download, and documents are handed in
 * with `rabicraft:open`. Messages are posted from this document, which is the same origin as the
 * frame, so the frame's origin check passes; its `event.source` check passes too, because the
 * frame sees this page as its parent.
 */
internal object HostPage {
    private const val ORIGIN = "https://rabicraft.localhost"

    /**
     * `window.RabiCraftOpen(name, base64)` hands a document to the frame. It waits for
     * `rabicraft:ready`: the frame installs its listener in the run callback that posts `ready`, so
     * a message sent earlier is dropped. Anything opened while it loads is queued.
     *
     * The app answers a save with `window.RabiCraftSaved(path)`; the frame needs no answer, the
     * write has already happened by then.
     */
    fun html(): String =
        """
        <!doctype html>
        <html lang="en">
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1" />
            <title>RabiCraft</title>
            <style>
              html, body { margin: 0; height: 100%; overflow: hidden; background: #f1f1f3; }
              iframe { display: block; width: 100%; height: 100%; border: 0; }
              /* Shown until the frame answers rabicraft:ready; the frame paints itself after. */
              #loading { position: absolute; inset: 0; display: flex; align-items: center;
                         justify-content: center; font: 16px sans-serif; color: #55555c; }
              @media (prefers-color-scheme: dark) {
                html, body { background: #19191c; }
                #loading { color: #b9b9c0; }
              }
            </style>
          </head>
          <body>
            <div id="loading">Opening RabiCraft…</div>
            <iframe id="app" src="./app/index.html?host=parent" allow="fullscreen; clipboard-read; clipboard-write"></iframe>
            <script>
              const frame = document.getElementById('app');
              const loading = document.getElementById('loading');
              let ready = false;
              let queued = null;

              function deliver(name, bytes) {
                frame.contentWindow.postMessage({ type: 'rabicraft:open', name: name, bytes: bytes }, '$ORIGIN');
              }

              // Called from the app (MainActivity.deliver); `bytes` is a Uint8Array.
              window.RabiCraftOpen = function (name, array) {
                const bytes = Uint8Array.from(atob(array), function (c) { return c.charCodeAt(0); });
                if (ready) deliver(name, bytes); else queued = [name, bytes];
              };

              // The frame's own script paints over this notice; keep it until then.
              window.setTimeout(function () { loading.style.display = 'none'; }, 8000);

              window.addEventListener('message', function (event) {
                if (event.origin !== '$ORIGIN' || event.source !== frame.contentWindow) return;
                const data = event.data || {};
                switch (data.type) {
                  case 'rabicraft:ready':
                    ready = true;
                    loading.style.display = 'none';
                    window.RabiCraftAndroid.ready();
                    if (queued) { deliver(queued[0], queued[1]); queued = null; }
                    break;
                  case 'rabicraft:save':
                    // The app writes the bytes and reports where with window.RabiCraftSaved.
                    window.RabiCraftAndroid.save(data.name, !!data.saveAs, data.bytes);
                    break;
                  case 'rabicraft:dirty':
                    window.RabiCraftAndroid.dirty(!!data.dirty);
                    break;
                  case 'rabicraft:failed':
                    window.RabiCraftAndroid.failed(String(data.error || 'unknown error'));
                    break;
                }
              });

            </script>
          </body>
        </html>
        """.trimIndent()
}
