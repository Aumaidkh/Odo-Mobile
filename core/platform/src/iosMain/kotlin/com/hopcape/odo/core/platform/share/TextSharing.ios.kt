package com.hopcape.odo.core.platform.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

/**
 * iOS actual — the system share sheet, presented from the key window's root controller.
 *
 * Presented from the *topmost* controller rather than the root: a sheet already on screen
 * owns the presentation, and asking the root to present over it does nothing but log a
 * warning. Walking `presentedViewController` finds whatever is actually visible.
 */
@Composable
actual fun rememberTextSharer(): (String) -> Unit = remember {
    { text ->
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        var top = root
        while (top?.presentedViewController != null) {
            top = top.presentedViewController
        }
        top?.presentViewController(
            UIActivityViewController(activityItems = listOf(text), applicationActivities = null),
            animated = true,
            completion = null,
        )
    }
}
