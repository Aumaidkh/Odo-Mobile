package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A slim, full-width strip for a standing condition the owner should see on every screen
 * until it clears — a maintenance window, a degraded connection. Not for a one-off result
 * ("saved", "failed to load"); that is a snackbar or inline state, not a banner.
 *
 * Sits above a screen's content, outside [OdoScreen]'s own padding, so it reads as
 * app-wide chrome rather than belonging to whichever screen happens to be on top. Because
 * of that, it is also the one place in the tree with nobody else to apply
 * [Modifier.statusBarsPadding] for it — [OdoScreen]'s `Scaffold` normally does this, but a
 * banner sitting above the whole nav host has no `Scaffold` above it, so it applies its own.
 *
 * `surfaceRaised` (not a low-alpha tint) is deliberate: Odo's near-black dark palette makes
 * a translucent warning tint nearly invisible against `bg`, the same problem a warning-only
 * tint would have on the light palette's own busy backgrounds. `surfaceRaised` is already
 * the token every card uses to read as a distinct layer against `bg` in both themes, so the
 * banner reads clearly regardless of theme; the [OdoDivider] and warning-tinted icon carry
 * the "this is a warning" cue instead.
 *
 * ```
 * Column {
 *     OdoBanner("Some features are temporarily unavailable for maintenance.")
 *     OdoNavHost(...)
 * }
 * ```
 *
 * @param message the copy; short enough for one or two lines.
 */
@Composable
fun OdoBanner(message: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().background(OdoTheme.colors.surfaceRaised).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoIcon(
                IcWarning,
                contentDescription = null,
                tint = OdoTheme.colors.warning,
                size = OdoTheme.iconSizes.small,
            )
            OdoText(
                message,
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.text,
            )
        }
        OdoDivider()
    }
}

@OdoThemePreviews
@Composable
private fun OdoBannerPreview() = OdoPreview {
    OdoBanner("Some features are temporarily unavailable for maintenance.")
}
