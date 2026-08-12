package com.hopcape.odo.core.platform.pdf

import androidx.compose.runtime.Composable

/**
 * Turns a self-contained HTML document into the bytes of a printable PDF.
 *
 * Both platforms already carry a browser engine that lays out text, wraps paragraphs, breaks
 * pages and embeds fonts — so a printed record is written as HTML and handed to it, rather
 * than laid out twice against two drawing APIs. The output is a real PDF with selectable
 * text, not a picture of one.
 *
 * A composable rather than an injected port, for the same reason as the camera, the file
 * picker and the share sheet: rendering needs a UI context on both platforms, and no Koin
 * singleton can hold one without leaking it.
 *
 * The HTML must be self-contained. Nothing is fetched: no stylesheet link, no remote image,
 * no web font by URL. Anything the document needs is inline or a `data:` URI, because this
 * has to work on a phone with no network — which is where a record usually gets shared.
 *
 * @return a suspending function taking the document's HTML and the name it should carry
 *   inside the file, and answering its bytes — or `null` if it could not be produced. There
 *   is nothing more useful to say about a failure than that it happened: the caller shows
 *   one message either way.
 */
@Composable
expect fun rememberHtmlToPdf(): suspend (html: String, documentName: String) -> ByteArray?

/**
 * The paper every Odo document is produced on, in PostScript points (1/72 inch) — the unit
 * PDF itself is measured in.
 *
 * A4 because the record is an Indian document and A4 is what an Indian printer, RTO or
 * workshop uses. Fixed rather than a parameter: two platforms rendering the same record onto
 * different paper is a difference nobody would ever want.
 */
object PdfPage {
    const val A4_WIDTH_POINTS: Int = 595
    const val A4_HEIGHT_POINTS: Int = 842

    /**
     * No printer margin. The HTML sets its own margins in millimetres, so the two platforms
     * cannot end up disagreeing about them — and a document that is centred on one phone and
     * shifted on another is worse than one with margins we chose.
     */
    const val MARGIN_POINTS: Int = 0
}
