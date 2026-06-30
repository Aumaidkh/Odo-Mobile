package com.hopcape.odo.core.designsystem.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The wrapper every Odo `@Preview` should put around its content: it applies
 * [OdoTheme] and paints the themed background, so a preview looks exactly like
 * the running app. Pair it with [OdoThemePreviews] to get light **and** dark in
 * one annotation:
 *
 * ```
 * @OdoThemePreviews
 * @Composable
 * private fun OdoButtonPreview() = OdoPreview { OdoButton(text = "Scan bill") {} }
 * ```
 *
 * Why a wrapper rather than a pure annotation? A multipreview annotation can only
 * multiply `@Preview` configs — it cannot inject a composable around the target.
 * (Compose Multiplatform has a `Preview(wrapper = …)` hook, but Android Studio's
 * renderer ignores it.) So themeing is done here, in one shared composable, and
 * the dark/light split is driven by the annotation's `uiMode` via
 * [isSystemInDarkTheme] — no separate light/dark preview functions needed.
 *
 * @param darkTheme defaults to the preview's system setting (which
 *   [OdoThemePreviews] toggles); override to pin a single theme.
 * @param padded wrap the content in [OdoTheme] `spacing.lg` padding (handy for a
 *   single component; set `false` to preview a full-bleed screen).
 */
@Composable
fun OdoPreview(
    darkTheme: Boolean = isSystemInDarkTheme(),
    padded: Boolean = true,
    content: @Composable () -> Unit,
) {
    OdoTheme(darkTheme = darkTheme) {
        Surface(
            color = OdoTheme.colors.bg,
            contentColor = OdoTheme.colors.text,
        ) {
            if (padded) {
                Box(Modifier.padding(OdoTheme.spacing.lg)) { content() }
            } else {
                content()
            }
        }
    }
}
