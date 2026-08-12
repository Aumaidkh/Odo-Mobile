package com.hopcape.odo.core.platform.pdf

import android.content.Context
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.writeTo
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Android actual — an off-screen [WebView], printed through its own
 * [android.print.PrintDocumentAdapter].
 *
 * The WebView is never attached to the window. It only has to lay the document out, which it
 * does from `loadDataWithBaseURL` alone, so nothing is ever drawn on screen and the owner
 * sees the sheet they are on rather than a flash of a document.
 *
 * Everything touching the WebView runs on the main thread, because that is where a WebView
 * must be created, loaded, printed and destroyed. Only the file read moves off it.
 */
@Composable
actual fun rememberHtmlToPdf(): suspend (html: String, documentName: String) -> ByteArray? {
    val context = LocalContext.current
    return remember(context) {
        { html, documentName -> renderHtmlToPdf(context, html, documentName) }
    }
}

/**
 * How long to wait for the document's embedded fonts to decode before printing anyway.
 *
 * A base64 `@font-face` is decoded after the page reports itself finished, so printing on
 * `onPageFinished` alone catches the document mid-swap and produces a PDF set in the
 * fallback font. Two seconds is far longer than decoding two weights actually takes; the
 * timeout exists so a font that never decodes still yields a document.
 */
private const val FONT_TIMEOUT_MILLIS = 2_000L
private const val FONT_POLL_MILLIS = 50L

/** The resolution the adapter is asked to lay out at. Text is vector, so this only sets scale. */
private const val PRINT_DPI = 600

private suspend fun renderHtmlToPdf(
    context: Context,
    html: String,
    documentName: String,
): ByteArray? {
    // One file per render, deleted in the same call: the bytes are what the caller wanted,
    // and a half-written PDF left in the cache is a file the share sheet could pick up.
    val scratch = File(context.cacheDir, "odo-render-${documentName.hashCode()}.pdf")

    return try {
        val webView = withContext(Dispatchers.Main) { loadOffScreen(context, html) }
        try {
            val written = withContext(Dispatchers.Main) {
                ParcelFileDescriptor
                    .open(scratch, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE)
                    .use { descriptor ->
                        webView.createPrintDocumentAdapter(documentName)
                            .writeTo(a4Attributes(), descriptor)
                    }
            }
            if (written) withContext(Dispatchers.IO) { scratch.readBytes() } else null
        } finally {
            withContext(Dispatchers.Main) { webView.destroy() }
        }
    } catch (error: Throwable) {
        // A device with no WebView installed, a disabled WebView package, or no room in the
        // cache. The caller has one thing to say to the owner either way.
        if (error is kotlinx.coroutines.CancellationException) throw error
        null
    } finally {
        scratch.delete()
    }
}

/** A4 at [PRINT_DPI], with the margins left to the document's own CSS. */
private fun a4Attributes(): PrintAttributes = PrintAttributes.Builder()
    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
    .setResolution(PrintAttributes.Resolution("odo-pdf", "Odo", PRINT_DPI, PRINT_DPI))
    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
    .build()

/**
 * A WebView holding [html], laid out and with its fonts decoded.
 *
 * JavaScript is on only so the font state can be read back; the document itself has no
 * script in it, and nothing is loaded from anywhere, so there is nothing for it to reach.
 */
private suspend fun loadOffScreen(context: Context, html: String): WebView {
    val webView = WebView(context)
    webView.settings.javaScriptEnabled = true

    suspendCancellableCoroutine { continuation ->
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
        // No base URL: the document is self-contained, and giving it one would let a stray
        // relative path resolve to something on the device.
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    webView.awaitFonts()
    return webView
}

/** Poll until the document's fonts report themselves loaded, or [FONT_TIMEOUT_MILLIS] passes. */
private suspend fun WebView.awaitFonts() {
    repeat((FONT_TIMEOUT_MILLIS / FONT_POLL_MILLIS).toInt()) {
        // evaluateJavascript answers a JSON value, so a loaded state comes back quoted.
        if (evaluate("document.fonts.status") == "\"loaded\"") return
        delay(FONT_POLL_MILLIS)
    }
}

private suspend fun WebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavascript(script) { result ->
            if (continuation.isActive) continuation.resume(result)
        }
    }
