package org.rabicraft.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import java.io.File
import java.io.IOException

/**
 * RabiCraft on Android: the browser build (`apps/rabicraft-web`) in a WebView, plus the two things
 * a bare WebView does not give it.
 *
 * **The app is served from its own origin.** [Site] answers `https://rabicraft.localhost/` out of
 * the APK's assets instead of loading the site from `file://` or `data:`. The page needs a real
 * origin: egui keeps preferences, the workspace and the session in `localStorage`, which browsers
 * refuse to `file://` documents, and an origin of our own keeps the app out of any other page's
 * origin. `*.localhost` resolves to loopback and counts as a secure context, so nothing leaves the
 * device and the app needs no network permission.
 *
 * **The host bridge.** A WebView has no download of its own, so the browser build's Save, which
 * clicks an `<a download>`, would go nowhere. The app frames the page in [HostPage] with
 * `?host=parent`, which makes it deliver saves — and exports, which are files too — as
 * `rabicraft:save` messages instead; the app writes them with MediaStore (Downloads/RabiCraft),
 * and hands documents it was opened with back as `rabicraft:open`. The message shapes are pinned
 * by the page's test.
 *
 * **Files.** [Chooser] gives the page's own `<input type=file>` the system picker (a WebView has
 * none), and [Files] lists what the app saved and offers the system picker for anywhere else. Both
 * go through Android's documents UI, so wherever the phone can mount a provider — another cloud
 * app, a USB stick, Drive — is a place a document can be opened from or copied to, with no
 * account and no permission of the app's own.
 */
class MainActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView

    /** The page's file inputs, and the files this app opens. */
    private lateinit var chooser: Chooser

    /** The app's own file list, share and save-as. */
    private lateinit var files: Files

    /** A document from Open with / Share, delivered once [HostPage] says the frame is ready. */
    private var queued: Pair<String, ByteArray>? = null

    /** Whether the frame reports unsaved work, so leaving can ask first. Written from the bridge. */
    @Volatile
    private var unsaved = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = WebView(this)
        web.setBackgroundColor(0xFFF1F1F3.toInt())
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The app is not a browser: a mobile user agent keeps the page's own text fields and
            // viewport behaviour sane, and nothing here loads a remote site.
            userAgentString = "$userAgentString RabiCraft/$VERSION"
            // The whole app is in the APK: no page may reach a file or a content provider.
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.addJavascriptInterface(Bridge(), "RabiCraftAndroid")
        web.webViewClient = Site()
        chooser = Chooser(this)
        files = Files(this)
        web.webChromeClient = chooser
        status = TextView(this).apply {
            setText(R.string.opening)
            setPadding(32, 32, 32, 32)
            visibility = View.GONE
        }
        setContentView(FrameLayout(this).apply {
            addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(status, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        readIntent(intent)
        web.loadUrl("$ORIGIN/host.html")
    }

    /** The result of a picker this app started: the page's `<input type=file>`, or Open with. */
    @Deprecated("The platform's own callback is deprecated since API 30; FILE_CHOOSER_RESULT is still how a WebView answers.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        chooser.onActivityResult(requestCode, resultCode, data)
        files.onActivityResult(requestCode, resultCode, data)
    }

    /** The file sheet: what this app saved, the system picker, and where to send a copy. */
    fun showFiles() {
        files.sheet()
    }

    /** A document sent from another app while RabiCraft is open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
        queued?.let { (name, bytes) -> deliver(name, bytes) }
    }

    override fun onDestroy() {
        // Drops the page's listeners and the JavaScript bridge with the session.
        web.destroy()
        super.onDestroy()
    }

    /** Back leaves the app; with unsaved work it asks first, as the desktop build does on quit. */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!unsaved) {
            super.onBackPressed()
            return
        }
        AlertDialog.Builder(this)
            .setMessage(R.string.unsaved)
            .setPositiveButton(R.string.discard) { _, _ -> finish() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun readIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_SEND) return
        readDocument(uri)
    }

    /**
     * Open `uri` (Open with, Share, or a row of the file sheet) as a tab. The bytes go through the
     * host page so the frame opens them like any other document, name and all.
     */
    fun readDocument(uri: Uri) {
        val name = displayName(uri)
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            Log.e(TAG, "could not read $uri: $e")
            show(getString(R.string.open_failed, name, e.message.orEmpty()))
            return
        } catch (e: SecurityException) {
            Log.e(TAG, "refused reading $uri: $e")
            show(getString(R.string.open_failed, name, e.message.orEmpty()))
            return
        }
        if (bytes == null) {
            Log.e(TAG, "no stream for $uri")
            show(getString(R.string.open_failed, name, getString(R.string.files_missing)))
            return
        }
        queued = name to bytes
        deliver(name, bytes)
    }

    /**
     * Serves the APK's assets, and the host page above them, from the app's own origin.
     *
     * Two failures that would otherwise leave a blank window are reported: an address the WebView
     * refuses outright (no notice at all, it only calls `onReceivedError`) and the host page itself
     * missing from the APK.
     */
    inner class Site : WebViewClient() {
        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            Log.e(TAG, "could not load ${request.url}: ${error.description}")
            // A frame's own 404 is not an error the WebView reports, but a refused document is:
            // that is the case where the window would stay blank with nothing on screen.
            if (request.isForMainFrame) show(getString(R.string.page_failed, request.url.toString(), error.description.toString()))
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url
            if (url.host != HOST) return null
            val path = url.path.orEmpty().trimStart('/').ifEmpty { "host.html" }
            // Nothing climbs out of the assets: the page asks only for what index.html names.
            if (path.split('/').any { it == ".." } || path.contains('\u0000')) return reply(403, "text/plain", ByteArray(0))
            if (path == "host.html") return reply(200, "text/html", HostPage.html().toByteArray(Charsets.UTF_8))
            val stream = try {
                assets.open("web/$path")
            } catch (e: IOException) {
                // A frame pointed at a file the APK does not have is an empty screen and nothing
                // else: no error is raised for it, so it is reported here.
                Log.e(TAG, "missing from the APK: web/$path")
                if (path == "index.html") {
                    val text = getString(R.string.page_missing, path)
                    runOnUiThread {
                        show(text)
                        web.evaluateJavascript("window.RabiCraftNotice && window.RabiCraftNotice(${js(text)})", null)
                    }
                }
                return reply(404, "text/plain", ByteArray(0))
            }
            // As packaging/web/README.md asks of any host: a hashed file never changes, and
            // index.html is always revalidated.
            return WebResourceResponse(mime(path), null, 200, "OK", cacheHeaders(path), stream)
        }
    }

    /** `window.RabiCraftAndroid`: the app side of [HostPage]'s script. Called from a WebView thread. */
    inner class Bridge {
        /**
         * One save. The raw bytes travel as base64 because the bridge carries primitives only (or
         * JSON through `evaluateJavascript`), and a PDF does not survive a string round trip.
         */
        @JavascriptInterface
        fun save(name: String, saveAs: Boolean, bytes: String) {
            val data = try {
                Base64.decode(bytes, Base64.DEFAULT)
            } catch (e: IllegalArgumentException) {
                runOnUiThread { show(getString(R.string.save_failed, name, e.message.orEmpty())) }
                return
            }
            runOnUiThread { save(name, data, saveAs) }
        }

        /** Unsaved work changed: the frame posts this whenever the flag flips. */
        @JavascriptInterface
        fun dirty(dirty: Boolean) {
            unsaved = dirty
        }

        /** The frame mounted its listeners: documents opened before this were queued. */
        @JavascriptInterface
        fun ready() {
            queued?.let { (name, bytes) -> deliver(name, bytes) }
        }

        /** The frame's own "Files" button: the app's file list, over the page. */
        @JavascriptInterface
        fun files() {
            runOnUiThread { showFiles() }
        }

        /** The frame could not start (no WebGL 2, for instance). */
        @JavascriptInterface
        fun failed(error: String) {
            Log.e(TAG, "the app could not start: $error")
            runOnUiThread { show(getString(R.string.start_failed, error)) }
        }
    }

    /**
     * Writes a save. Save As (or a document with no file yet) creates Downloads/RabiCraft/<name>;
     * a plain Save overwrites the file the last save went to, as on the desktop. The app reports
     * where the file went itself, as `mod host` expects of an embedding page.
     */
    private fun save(name: String, bytes: ByteArray, saveAs: Boolean) {
        // A plain Save overwrites the file this document was saved to, and only that one: a
        // document opened from another app has no file of its own, and the last save may belong
        // to a different tab.
        val path = if (!saveAs && lastSaved?.endsWith("/$name") == true) overwrite(lastSaved!!, bytes) else create(name, bytes)
        if (path == null) {
            show(getString(R.string.save_failed, name, getString(R.string.storage_refused)))
            return
        }
        lastSaved = path
        unsaved = false
        show(getString(R.string.saved, path))
        web.evaluateJavascript("window.RabiCraftSaved && window.RabiCraftSaved(${js(path)})", null)
    }

    private var lastSaved: String? = null

    /** Creates `Downloads/RabiCraft/<name>`, writes `bytes` to it, and returns its path. */
    private fun create(name: String, bytes: ByteArray): String? {
        val safe = name.substringAfterLast('/').ifEmpty { "document.pdf" }
        val relative = "${Environment.DIRECTORY_DOWNLOADS}/RabiCraft/$safe"
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safe)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/RabiCraft")
                }
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val uri = contentResolver.insert(collection, values) ?: return null
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
                relative
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RabiCraft")
                if (!dir.mkdirs() && !dir.isDirectory) return null
                File(dir, safe).writeBytes(bytes)
                relative
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    /** Writes `bytes` over the file the last save created; falls back to a new file if it is gone. */
    private fun overwrite(path: String, bytes: ByteArray): String? {
        val safe = path.substringAfterLast('/')
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return try {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val existing = contentResolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.RELATIVE_PATH}=? AND ${MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf("${Environment.DIRECTORY_DOWNLOADS}/RabiCraft/", safe),
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null
                }
                // The user deleted it from Downloads between two saves: write a new one.
                if (existing == null) return create(safe, bytes)
                val out = contentResolver.openOutputStream(existing, "wt") ?: return null
                out.use { it.write(bytes) }
                path
            } catch (e: IOException) {
                null
            } catch (e: SecurityException) {
                null
            }
        }
        return try {
            @Suppress("DEPRECATION")
            val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "RabiCraft/$safe")
            file.writeBytes(bytes)
            path
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun deliver(name: String, bytes: ByteArray) {
        // The page's own queue holds it until it is ready, so this only waits for the bridge.
        if (!ready) return
        web.evaluateJavascript("window.RabiCraftOpen && window.RabiCraftOpen(${js(name)}, ${js(encode(bytes))})", null)
        if (queued?.first == name) queued = null
    }

    /** Set by [HostPage] once the frame has posted `rabicraft:ready`. */
    private var ready = false

    private fun show(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private companion object {
        const val HOST = "rabicraft.localhost"
        const val ORIGIN = "https://$HOST"

        /** Everything the WebView says goes to logcat under this tag: `adb logcat -s RabiCraft`. */
        const val TAG = "RabiCraft"
    }
}
