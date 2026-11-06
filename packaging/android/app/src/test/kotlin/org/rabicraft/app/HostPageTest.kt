package org.rabicraft.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two contracts the Android port rests on, neither of which is checked by the Rust tests:
 * the messages [HostPage] exchanges with `apps/rabicraft-web/src/main.rs` (`mod host`), and the
 * MIME types the browser build needs (packaging/web/README.md).
 */
class HostPageTest {
    private val page = HostPage.html()

    @Test
    fun the_frame_is_the_browser_build_asked_to_be_a_framed_page() {
        // Without ?host=parent the frame downloads its saves instead of posting them here.
        assertTrue(page.contains("/index.html?host=parent"))
        // package.sh copies trunk's output to the root of assets/web, so the site sits next to
        // host.html. A frame pointed anywhere else is an empty window and no error at all: that
        // shipped once, with ./app/index.html.
        assertEquals("./index.html?host=parent", HostPage.FRAME)
        // Every message must be addressed to the frame's origin: postMessage with "*" would let
        // any navigated frame read the documents the user hands over.
        assertFalse("a wildcard postMessage target", page.contains(", '*')"))
        assertTrue(page.contains("'https://rabicraft.localhost'"))
    }

    @Test
    fun the_messages_are_the_ones_the_frame_posts_and_listens_for() {
        // mod host::post_ready, post_failed, save_fn and dirty_fn; mod host::listen takes `open`.
        for (message in listOf("rabicraft:ready", "rabicraft:failed", "rabicraft:save", "rabicraft:dirty", "rabicraft:open")) {
            assertTrue(message, page.contains(message))
        }
        // `listen` reads `name` and `bytes` from rabicraft:open and defaults an empty name.
        assertTrue(page.contains("name: name"))
        assertTrue(page.contains("bytes: bytes"))
        // save_fn posts `name`, `saveAs` and `bytes`; the page must pass all three on.
        assertTrue(page.contains("data.saveAs"))
        assertTrue(page.contains("data.name"))
        assertTrue(page.contains("data.bytes"))
        // Dirty messages carry `dirty`.
        assertTrue(page.contains("data.dirty"))
    }

    @Test
    fun opening_waits_for_ready() {
        // The frame installs its message listener in the callback that posts `ready`: a document
        // sent before that arrives at nothing.
        assertTrue(page.contains("if (ready) deliver(name, bytes); else queued = [name, bytes];"))
        assertTrue(page.contains("if (queued) { deliver(queued[0], queued[1]); queued = null; }"))
    }

    @Test
    fun the_framed_page_is_a_file_the_apk_ships() {
        // The test runs in packaging/android/app, next to src/main (Gradle's working directory).
        val framed = HostPage.FRAME.substringBefore('?').removePrefix("./")
        val site = File("src/main/assets/web/$framed")
        // Absent until package.sh has run; the release build refuses to start without it.
        if (!site.parentFile.isDirectory) return
        assertTrue("the host page frames $framed, which the APK does not ship", site.isFile)
        assertTrue(site.readText(), site.readText().contains("id=\"rabicraft\""))
    }

    @Test
    fun a_start_that_stalls_says_so_instead_of_showing_an_empty_window() {
        // The notice used to fade out on a timer, which turned a failure into a blank screen.
        assertFalse(page.contains("loading.style.display = 'none'; },"))
        assertTrue(page.contains("window.RabiCraftNotice"))
        assertTrue(page.contains("window.setTimeout"))
    }

    @Test
    fun the_assets_are_served_with_the_types_the_browser_build_needs() {
        // Trunk's output: index.html names the two hashed files.
        assertEquals("application/wasm", mime("rabicraft-web-0123456789abcdef_bg.wasm"))
        assertEquals("text/javascript", mime("rabicraft-web-0123456789abcdef.js"))
        assertEquals("text/html", mime("index.html"))
        // Hashed assets may be kept; index.html may not.
        assertTrue(cacheHeaders("rabicraft-web-0123456789abcdef_bg.wasm").getValue("Cache-Control").startsWith("public, max-age="))
        assertEquals("no-cache", cacheHeaders("index.html").getValue("Cache-Control"))
    }

    @Test
    fun javascript_arguments_are_quoted_as_literals() {
        // A document name comes from another app; it reaches the page through JavaScript.
        assertEquals("\"a\\\"b.pdf\"", js("a\"b.pdf"))
        assertEquals("\"C:\\\\RabiCraft\\\\x.pdf\"", js("C:\\RabiCraft\\x.pdf"))
    }
}
