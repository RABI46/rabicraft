package org.rabicraft.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * The WebView's half of "Open…": a file input the page clicks has no picker of its own.
 *
 * The browser build opens documents with `rfd::AsyncFileDialog`, which on the web is an
 * `<input type="file">` — and a WebView hands that to `WebChromeClient.onShowFileChooser`, whose
 * default implementation does nothing at all. Without this the Open button, Attach, Insert image
 * and OCR all look broken: the tap lands, and no picker ever appears.
 *
 * The system picker does the rest: it shows the device, and any provider mounted on it (Drive,
 * a cloud app, a USB stick) without the app asking for a Drive scope, an account or a permission.
 */
internal class Chooser(private val activity: MainActivity) : WebChromeClient() {
    /** The page's pending `<input type=file>` callback; null when no picker is showing. */
    private var pending: ValueCallback<Array<Uri>>? = null

    override fun onShowFileChooser(web: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        // A second request while one is showing: answer the old one rather than leak it.
        pending?.onReceiveValue(null)
        pending = callback
        val pick = plan(params.acceptTypes, params.mode)
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = pick.types.first()
            if (pick.types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, pick.types.toTypedArray())
            if (pick.multiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        return try {
            activity.startActivityForResult(intent, PICK)
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(MainActivity.TAG, "no app can pick a file: $e")
            pending = null
            false
        }
    }

    /** The picker's answer; `resultCode` is the activity's, and `data` holds one file or several. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != PICK) return
        val callback = pending ?: return
        pending = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            callback.onReceiveValue(null)
            return
        }
        val many = data.clipData
        val uris: Array<Uri> = when {
            many != null -> Array(many.itemCount) { many.getItemAt(it).uri }
            data.data != null -> arrayOf(data.data!!)
            else -> emptyArray()
        }
        callback.onReceiveValue(uris.ifEmpty { null })
    }

    /** The page's console, in logcat under one tag: `adb logcat -s RabiCraft`. */
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        Log.i(MainActivity.TAG, "${message.messageLevel()} ${message.sourceId()}:${message.lineNumber()} ${message.message()}")
        return true
    }

    /** What the picker should offer, worked out before any Android call so it can be tested. */
    internal data class Pick(val types: List<String>, val multiple: Boolean)

    internal companion object {
        /** `startActivityForResult` code for a `<input type=file>` pick. */
        const val PICK = 0x5AB1

        /**
         * The picker for a file input: the page's `accept` list as MIME types (`application/pdf` as
         * it is, `.pdf` as its type, a wildcard passed through), and several files only when the
         * input asked for several.
         */
        fun plan(accepted: Array<String>?, mode: Int): Pick {
            // A WebView hands the whole attribute over, so one entry can be `application/pdf,image/*`:
            // split every entry, not only the first comma of the first.
            val types = accepted.orEmpty()
                .flatMap { it.split(',') }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { if (it.startsWith('.')) mime(it) else it }
                .filter { it == "*/*" || it.contains('/') }
                .distinct()
                .ifEmpty { listOf("*/*") }
            return Pick(types, mode == FileChooserParams.MODE_OPEN_MULTIPLE)
        }
    }
}

/** A file's name as Android knows it, for a save or an open. */
internal fun Activity.displayName(uri: Uri): String {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst() && !it.isNull(0)) return it.getString(0)
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
}

/** Base64 without line breaks: the bridge and `atob` both take it as one string. */
internal fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
