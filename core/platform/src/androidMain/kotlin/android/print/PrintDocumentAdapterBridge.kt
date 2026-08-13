package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Drives a [PrintDocumentAdapter] straight to a file, with no print dialog.
 *
 * This file declares itself in `android.print` on purpose. `LayoutResultCallback` and
 * `WriteResultCallback` are abstract classes whose constructors are package-private, so the
 * only way to implement them is from inside the package that declares them. Nothing else
 * here reaches into the framework — the two subclasses below exist solely to hand their
 * results back as coroutines.
 *
 * `WebView.createPrintDocumentAdapter` is the only supported way to get a laid-out,
 * paginated, vector PDF out of Android's own layout engine; the alternative is drawing the
 * WebView onto a canvas and cutting pages by hand, which puts a page break through whatever
 * happens to be at that pixel.
 *
 * Every call must be made on the main thread — the adapter is a `WebView`'s.
 */
internal suspend fun PrintDocumentAdapter.writeTo(
    attributes: PrintAttributes,
    destination: ParcelFileDescriptor,
): Boolean {
    val laidOut = layOut(attributes)
    if (!laidOut) return false

    val written = write(destination)
    onFinish()
    return written
}

/** Ask the adapter to lay the document out for [attributes]. False if it declined. */
private suspend fun PrintDocumentAdapter.layOut(attributes: PrintAttributes): Boolean =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }

        onLayout(
            // No previous attributes: this adapter has never been laid out before.
            null,
            attributes,
            signal,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: PrintDocumentInfo?, changed: Boolean) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onLayoutCancelled() {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
            null,
        )
    }

/** Write every page into [destination]. False if the adapter declined or was cancelled. */
private suspend fun PrintDocumentAdapter.write(destination: ParcelFileDescriptor): Boolean =
    suspendCancellableCoroutine { continuation ->
        val signal = CancellationSignal()
        continuation.invokeOnCancellation { signal.cancel() }

        onWrite(
            arrayOf(PageRange.ALL_PAGES),
            destination,
            signal,
            object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<out PageRange>?) {
                    // An empty page set means the adapter wrote nothing, which would leave a
                    // zero-byte file that opens as a corrupt PDF.
                    if (continuation.isActive) continuation.resume(!pages.isNullOrEmpty())
                }

                override fun onWriteFailed(error: CharSequence?) {
                    if (continuation.isActive) continuation.resume(false)
                }

                override fun onWriteCancelled() {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
    }
