package com.hopcape.odo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hopcape.odo.core.platform.pdf.rememberHtmlToPdf
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * The HTML-to-PDF seam, on a real device.
 *
 * It cannot be covered on the host JVM: the whole implementation is a `WebView` and the
 * framework's own print pipeline, neither of which exists off a device. So this is where it
 * is proved at all.
 *
 * The first test is the important one. The Android implementation reaches the print pipeline
 * through two callback classes whose constructors are package-private, which means the
 * bridge that implements them declares itself in `android.print`. Whether the runtime allows
 * that is not something a compiler can answer — it either links on the device or throws
 * `IllegalAccessError`. This is that answer.
 */
@RunWith(AndroidJUnit4::class)
class HtmlToPdfTest {

    @get:Rule
    val compose = createComposeRule()

    /** The four bytes every PDF file starts with. */
    private val pdfMagic = "%PDF"

    private lateinit var htmlToPdf: suspend (String, String) -> ByteArray?

    /**
     * The seam, composed once. The rule's activity takes one `setContent` for the whole
     * test, so a test that renders twice reaches the renderer twice rather than composing
     * twice.
     */
    @Before
    fun composeRenderer() {
        compose.setContent { htmlToPdf = rememberHtmlToPdf() }
        compose.waitForIdle()
    }

    private fun render(html: String): ByteArray? = runBlocking { htmlToPdf(html, "odo-record-test") }

    @Test
    fun renders_html_into_a_real_pdf() {
        val bytes = render(
            """
            <html><head><style>@page { size: A4; margin: 14mm }</style></head>
            <body><h1>Verified service record</h1><p>Maruti Swift VXI</p></body></html>
            """.trimIndent(),
        )

        assertNotNull("the print pipeline produced nothing at all", bytes)
        val pdf = bytes!!
        assertTrue("a one-page document is not 1 KB; got ${pdf.size} bytes", pdf.size > 1_000)
        assertEquals("the bytes are not a PDF", pdfMagic, pdf.decodeToString(0, 4))
    }

    @Test
    fun a_long_document_paginates_rather_than_truncating() {
        val rows = (1..120).joinToString("") { "<tr><td>Row $it</td><td>Rs. 3,200</td></tr>" }
        val long = render("<html><body><table>$rows</table></body></html>")
        val short = render("<html><body><p>One line</p></body></html>")

        assertNotNull(long)
        assertNotNull(short)
        assertTrue(
            "120 rows produced no more bytes than one line, so the tail was dropped",
            long!!.size > short!!.size,
        )
    }

    @Test
    fun a_document_that_cannot_be_parsed_still_yields_a_file_rather_than_a_crash() {
        // Deliberately broken markup. A browser engine recovers from this; the point is that
        // nothing here throws into the caller, which renders on a sheet the owner is looking at.
        val bytes = render("<html><body><div><p>unclosed")

        assertNotNull("malformed input must degrade to a document, not to an exception", bytes)
        assertEquals(pdfMagic, bytes!!.decodeToString(0, 4))
    }
}
