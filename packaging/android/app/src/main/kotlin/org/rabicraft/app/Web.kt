package org.rabicraft.app

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * What the WebView must be told about a file it is served. Browsers pick a decoder from the MIME
 * type, not the extension: `.wasm` under anything but `application/wasm` is refused for streaming
 * compilation, and `.js` under anything but a JavaScript type is not run as a module
 * (packaging/web/README.md, "Required server settings").
 */
internal fun mime(path: String): String =
    when (path.substringAfterLast('/').substringAfterLast('.', "").lowercase()) {
        "html", "htm" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "wasm" -> "application/wasm"
        "css" -> "text/css"
        "json", "map" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        else -> "application/octet-stream"
    }

/**
 * Cache rules, as the hosting notes ask: trunk writes a content hash into every asset's name, so
 * those never change and may be kept forever; `index.html` is the only file that does change.
 */
internal fun cacheHeaders(path: String): MutableMap<String, String> =
    mutableMapOf("Cache-Control" to if (path.substringAfterLast('/') == "index.html") "no-cache" else "public, max-age=31536000, immutable")

/** An empty response for a path the APK has no file for, or one that tries to leave the assets. */
internal fun reply(status: Int, type: String, body: ByteArray): WebResourceResponse =
    WebResourceResponse(type, "utf-8", status, if (status == 200) "OK" else "Error", mutableMapOf(), ByteArrayInputStream(body))

/**
 * A JavaScript string literal, for `WebView.evaluateJavascript`. Written here rather than with
 * `org.json.JSONObject.quote`, whose Android class is an empty stub in a JVM unit test.
 */
internal fun js(text: String): String {
    val out = StringBuilder(text.length + 2)
    out.append('"')
    for (c in text) {
        when {
            c == '"' -> out.append("\\\"")
            c == '\\' -> out.append("\\\\")
            c == '\n' -> out.append("\\n")
            c == '\r' -> out.append("\\r")
            c == '\t' -> out.append("\\t")
            c < ' ' -> out.append("\\u%04x".format(c.code))
            else -> out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}
