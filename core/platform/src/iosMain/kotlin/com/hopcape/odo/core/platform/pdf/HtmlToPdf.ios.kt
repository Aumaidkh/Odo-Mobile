package com.hopcape.odo.core.platform.pdf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hopcape.odo.core.platform.file.toByteArray
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSMakeRange
import platform.Foundation.NSMutableData
import platform.Foundation.NSValue
import platform.Foundation.setValue
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.UIGraphicsGetPDFContextBounds
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.valueWithCGRect
import platform.UIKit.viewPrintFormatter
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * iOS actual — an off-screen [WKWebView] printed through [UIPrintPageRenderer].
 *
 * The print formatter is the system's own paginator: it lays the document out for the paper
 * it is given, honours the print CSS, and reports how many pages it produced. Drawing each of
 * those pages into a PDF context gives a file with real text in it rather than a picture of
 * the page.
 *
 * All of it runs on the main thread — a `WKWebView` and the UIKit graphics context both
 * require it.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberHtmlToPdf(): suspend (html: String, documentName: String) -> ByteArray? =
    remember {
        // The document's name lives inside the file on Android; iOS takes it from the file
        // the caller writes, so there is nothing to do with it here.
        { html, _ -> renderHtmlToPdf(html) }
    }

/**
 * How long to give the document's embedded fonts before printing anyway. A base64
 * `@font-face` decodes after navigation reports itself finished, so printing immediately
 * catches the page mid-swap and sets the PDF in the fallback face.
 */
private const val FONT_TIMEOUT_MILLIS = 2_000L
private const val FONT_POLL_MILLIS = 50L

@OptIn(ExperimentalForeignApi::class)
private suspend fun renderHtmlToPdf(html: String): ByteArray? = withContext(Dispatchers.Main) {
    val paper = CGRectMake(
        x = 0.0,
        y = 0.0,
        width = PdfPage.A4_WIDTH_POINTS.toDouble(),
        height = PdfPage.A4_HEIGHT_POINTS.toDouble(),
    )

    val webView = WKWebView(frame = paper, configuration = WKWebViewConfiguration())
    // Held for the life of the call: WKWebView keeps only a weak reference to its delegate,
    // and a collected one means the load never reports back.
    val delegate = LoadDelegate()
    webView.navigationDelegate = delegate

    runCatching {
        webView.loadHTMLString(html, baseURL = null)
        delegate.awaitLoad()
        webView.awaitFonts()

        val renderer = UIPrintPageRenderer()
        renderer.addPrintFormatter(webView.viewPrintFormatter(), startingAtPageAtIndex = 0L)
        // paperRect and printableRect are read-only on the renderer; key-value coding is the
        // documented way to set the paper a formatter paginates against.
        renderer.setValue(NSValue.valueWithCGRect(paper), forKey = "paperRect")
        renderer.setValue(NSValue.valueWithCGRect(paper), forKey = "printableRect")

        val pdf = NSMutableData()
        UIGraphicsBeginPDFContextToData(pdf, paper, null)
        val pages = renderer.numberOfPages
        renderer.prepareForDrawingPages(NSMakeRange(0u, pages.toULong()))
        for (page in 0 until pages) {
            UIGraphicsBeginPDFPage()
            renderer.drawPageAtIndex(page, UIGraphicsGetPDFContextBounds())
        }
        UIGraphicsEndPDFContext()

        // A renderer that paginated to nothing writes a header-only file that opens as a
        // corrupt document, so an empty run is a failure rather than an empty PDF.
        if (pages == 0L) null else pdf.toByteArray()
    }.getOrNull().also {
        webView.navigationDelegate = null
    }
}

/** Poll until the document's fonts report themselves loaded, or the timeout passes. */
private suspend fun WKWebView.awaitFonts() {
    repeat((FONT_TIMEOUT_MILLIS / FONT_POLL_MILLIS).toInt()) {
        if (evaluate("document.fonts.status") == "loaded") return
        kotlinx.coroutines.delay(FONT_POLL_MILLIS)
    }
}

private suspend fun WKWebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        evaluateJavaScript(script) { result, _ ->
            if (continuation.isActive) continuation.resume(result as? String)
        }
    }

/**
 * Reports the first navigation to finish, successfully or not. One shot: the document is
 * loaded from a string and never navigates again.
 */
private class LoadDelegate : NSObject(), WKNavigationDelegateProtocol {

    private var onSettled: ((Unit) -> Unit)? = null
    private var settled = false

    suspend fun awaitLoad(): Unit = suspendCancellableCoroutine { continuation ->
        if (settled) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        onSettled = { if (continuation.isActive) continuation.resume(Unit) }
        continuation.invokeOnCancellation { onSettled = null }
    }

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = settle()

    // A failed load still settles: the renderer produces whatever did parse, which for a
    // self-contained document with nothing to fetch is the whole of it.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = settle()

    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        didFailProvisionalNavigation: WKNavigation?,
        withError: NSError,
    ) = settle()

    private fun settle() {
        settled = true
        onSettled?.invoke(Unit)
        onSettled = null
    }
}
