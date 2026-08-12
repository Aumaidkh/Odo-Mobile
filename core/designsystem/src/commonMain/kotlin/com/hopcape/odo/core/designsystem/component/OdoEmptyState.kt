package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The centred placeholder a screen shows when a list or section has nothing to
 * display yet — "No service logs yet. Scan your first bill." An empty state should
 * always point at the next action, so pair the copy with an [action] (usually a
 * primary [OdoButton]) that starts it.
 *
 * ```
 * OdoEmptyState(
 *     title = "Koi service log nahi",
 *     message = "Apni pehli bill scan karke shuru karein.",
 *     icon = { OdoIcon(scanVector, contentDescription = null, size = OdoTheme.iconSizes.large) },
 *     action = { OdoButton("Scan bill", onClick = vm::scan) },
 * )
 * ```
 *
 * @param title the headline; short and reassuring, not an error.
 * @param message optional secondary line explaining what will appear here.
 * @param icon optional illustrative glyph above the title.
 * @param action optional call-to-action below the copy (e.g. an [OdoButton]).
 */
@Composable
fun OdoEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(OdoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        icon?.invoke()
        OdoText(
            title,
            style = OdoTheme.typography.heading,
            textAlign = TextAlign.Center,
        )
        if (message != null) {
            OdoText(
                message,
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
        }
        // A touch more breathing room before the CTA than between the text lines.
        if (action != null) {
            Column(Modifier.padding(top = OdoTheme.spacing.sm)) { action() }
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoEmptyStatePreview() = OdoPreview {
    OdoEmptyState(
        title = "Koi service log nahi",
        message = "Apni pehli bill scan karke shuru karein.",
        action = { OdoButton("Scan bill", onClick = {}) },
    )
}
