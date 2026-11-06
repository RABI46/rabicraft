package org.rabicraft.app

import android.webkit.WebChromeClient.FileChooserParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's `accept` list becomes the picker's types, and its `multiple` becomes the picker's.
 *
 * This is what made Open a dead button before the chooser existed: a WebView hands a file input
 * nowhere without `onShowFileChooser`, and the list it hands over is the only thing that can
 * narrow the picker to PDFs. The picker itself is the platform's; the mapping is ours, so that is
 * what a unit test holds.
 */
class ChooserTest {
    private fun plan(accepted: Array<String>?, multiple: Boolean = false) =
        Chooser.plan(accepted, if (multiple) FileChooserParams.MODE_OPEN_MULTIPLE else FileChooserParams.MODE_OPEN)

    @Test
    fun `a PDF input picks PDFs`() {
        assertEquals(listOf("application/pdf"), plan(arrayOf("application/pdf")).types)
        assertEquals(listOf("application/pdf"), plan(arrayOf(".pdf")).types)
    }

    @Test
    fun `a comma-separated list is split, and wildcards pass through`() {
        assertEquals(listOf("application/pdf", "image/*"), plan(arrayOf("application/pdf,image/*")).types)
    }

    @Test
    fun `an empty or useless list shows everything rather than nothing`() {
        assertEquals(listOf("*/*"), plan(null).types)
        assertEquals(listOf("*/*"), plan(arrayOf()).types)
        assertEquals(listOf("*/*"), plan(arrayOf("", "  ", "not a type")).types)
    }

    @Test
    fun `a type offered twice is offered once`() {
        assertEquals(listOf("application/pdf"), plan(arrayOf("application/pdf", ".pdf")).types)
    }

    @Test
    fun `several files only when the input asked for several`() {
        assertTrue(plan(arrayOf("application/pdf"), multiple = true).multiple)
        assertFalse(plan(arrayOf("application/pdf")).multiple)
    }
}
