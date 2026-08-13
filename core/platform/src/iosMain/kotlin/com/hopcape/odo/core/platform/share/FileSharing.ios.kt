package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.hopcape.odo.core.platform.file.absolutePathFor
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/**
 * iOS actual — the system share sheet over a file URL.
 *
 * A URL rather than the file's bytes: handing `UIActivityViewController` an `NSURL` lets it
 * offer "Save to Files", AirDrop and the print targets, all of which want a file. Loading a
 * multi-page record into memory to hand over a `Data` would offer fewer targets and cost
 * more.
 *
 * Presented from the topmost controller rather than the root, for the same reason as
 * [rememberTextSharer]: a sheet already on screen owns the presentation, and the share
 * button that reaches this is on one.
 */
@Composable
actual fun rememberFileSharer(): (storageKey: String, mimeType: String, title: String) -> Unit =
    remember {
        // The MIME type is Android's way of describing the file; iOS reads the type from the
        // URL's extension, so there is nothing to do with it here.
        { storageKey, _, title ->
            val url = NSURL.fileURLWithPath(absolutePathFor(storageKey))
            val controller = UIActivityViewController(
                activityItems = listOf(url),
                applicationActivities = null,
            )
            controller.setTitle(title)

            var top = UIApplication.sharedApplication.keyWindow?.rootViewController
            while (top?.presentedViewController != null) {
                top = top.presentedViewController
            }
            // On iPad the sheet is a popover, and one with no anchor terminates the app.
            // Anchoring it to the presenting view keeps a phone-shaped assumption from
            // crashing a tablet.
            controller.popoverPresentationController?.sourceView = top?.view
            top?.presentViewController(controller, animated = true, completion = null)
        }
    }
