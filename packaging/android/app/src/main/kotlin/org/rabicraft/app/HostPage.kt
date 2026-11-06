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
     * The browser build, relative to this page: `package.sh` copies trunk's output to the root of
     * `assets/web`, so the site is `./index.html` next to `host.html`, not in a folder of its own.
     * `HostPageTest` and `check-page.py` both fail if this names a file the APK does not ship — a
     * frame pointed at a missing page is an empty screen, with no error anywhere on a phone.
     */
    const val FRAME = "./index.html?host=parent&compact=1"

    /**
     * `window.RabiCraftOpen(name, base64)` hands a document to the frame. It waits for
     * `rabicraft:ready`: the frame installs its listener in the run callback that posts `ready`, so
     * a message sent earlier is dropped. Anything opened while it loads is queued.
     *
     * The app answers a save with `window.RabiCraftSaved(path)`; the frame needs no answer, the
     * write has already happened by then.
     *
     * Nothing here is silent: if the frame does not answer in time, and whenever the browser
     * refuses to load it, the notice says so instead of leaving an empty screen.
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
              /* The app's own Files button: small, over the right end of the app's one bar, clear
                 of its height so a tap misses neither. */
              #files { position: absolute; top: 5px; right: 6px; z-index: 2; padding: 9px 11px;
                       font: 600 13px/1 sans-serif; color: #ffffff; background: #b70228;
                       border: 0; border-radius: 6px; }
              /* Shown until the app answers rabicraft:ready; the app paints itself after. */
              #loading { position: absolute; inset: 0; display: flex; align-items: center;
                         justify-content: center; padding: 24px; box-sizing: border-box;
                         text-align: center; font: 16px/1.5 sans-serif; color: #55555c; }
              @media (prefers-color-scheme: dark) {
                html, body { background: #19191c; }
                #loading { color: #b9b9c0; }
              }
            </style>
          </head>
          <body>
            <div id="loading">Opening RabiCraft…</div>
            <!-- Over the frame's own bar: the files this device holds, and where a copy goes. -->
            <button id="files" type="button">Files</button>
            <iframe id="app" src="$FRAME" allow="fullscreen; clipboard-read; clipboard-write"></iframe>
            <script>
              const frame = document.getElementById('app');
              const loading = document.getElementById('loading');
              let ready = false;
              let queued = null;

              // The app's own notice, if one arrived before the frame failed: it says more than
              // "could not load", and the frame's message must not overwrite it.
              let detail = '';

              function notice(text) {
                if (ready) return;
                loading.textContent = detail || text;
                loading.style.display = 'flex';
              }

              function deliver(name, bytes) {
                frame.contentWindow.postMessage({ type: 'rabicraft:open', name: name, bytes: bytes }, '$ORIGIN');
              }

              // The app's file list: what this device holds, and where a save may go. The app
              // draws it (native, over this page); nothing here needs to know what is in it.
              document.getElementById('files').addEventListener('click', function () {
                window.RabiCraftAndroid.files();
              });

              // Called from the app (MainActivity.deliver); `bytes` is a Uint8Array.
              window.RabiCraftOpen = function (name, array) {
                const bytes = Uint8Array.from(atob(array), function (c) { return c.charCodeAt(0); });
                if (ready) deliver(name, bytes); else queued = [name, bytes];
              };

              // The app found something it had to refuse (a page it cannot serve, a document it
              // cannot open): say it here rather than over an empty canvas.
              window.RabiCraftNotice = function (text) { detail = String(text); notice(detail); };

              // The watch covers the frames that never reach this page's message listener — a
              // missing file, a load the browser refused — which used to show as a blank screen.
              // The frame's own diagnostics (`rabicraft:failed`) arrive sooner and are specific.
              window.setTimeout(function () {
                notice('RabiCraft is taking longer than expected to start.\n\n' +
                       'If this stays, the app could not load its page: the WebView may not be ' +
                       'drawing (WebGL 2 is required).');
              }, 8000);

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
