package com.hopcape.odo.core.platform.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity

@Composable
actual fun OdoLightSystemBars() {
    val view = LocalView.current

    // A preview or a test renders into a view with no window behind it. Nothing to set,
    // and nothing worth crashing over.
    if (view.isInEditMode) return

    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars

        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false

        onDispose {
            // Restored, not left dark: the next screen follows the theme again, and a
            // screen that quietly changed a window-wide setting on its way past is the
            // kind of thing nobody finds until a different screen looks wrong.
            previous?.let {
                controller.isAppearanceLightStatusBars = it
                controller.isAppearanceLightNavigationBars = it
            }
        }
    }
}
