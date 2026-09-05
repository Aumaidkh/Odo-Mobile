package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import com.hopcape.odo.core.platform.file.absolutePathFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * iOS actual — the system share sheet over the image and its caption.
 *
 * [preferredApp] is ignored. iOS offers no supported way to aim `UIActivityViewController` at
 * one app, and the URL schemes that pretend to are refused for arbitrary attachments. The
 * sheet is the whole of what the platform gives.
 */
@Composable
actual fun rememberImageSharer(): (storageKey: String, caption: String, preferredApp: String?) -> Unit =
    remember {
        { storageKey, caption, _ ->
            val url = NSURL.fileURLWithPath(absolutePathFor(storageKey))
            val controller = UIActivityViewController(
                // Both, in this order: the sheet shows the picture and carries the sentence
                // into whichever app takes it.
                activityItems = listOf(url, caption),
                applicationActivities = null,
            )

            var top = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (top?.presentedViewController != null) {
                top = top.presentedViewController
            }
            // On iPad the sheet is a popover, and one with no anchor terminates the app.
            controller.popoverPresentationController?.sourceView = top?.view
            top?.presentViewController(controller, animated = true, completion = null)
        }
    }

/** iOS actual — Skia's own encoder, which is what Compose already drew the card with. */
actual suspend fun ImageBitmap.toPngBytes(): ByteArray? = withContext(Dispatchers.Default) {
    runCatching {
        Image.makeFromBitmap(asSkiaBitmap())
            .encodeToData(EncodedImageFormat.PNG)
            ?.bytes
    }.getOrNull()
}
