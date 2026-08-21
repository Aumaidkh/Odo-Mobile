package com.hopcape.odo.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.hopcape.odo.core.designsystem.icons.IcChevronDown
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A row that opens to show more underneath it: a title, a chevron that turns as it opens,
 * and caller-supplied [content] below.
 *
 * **Controlled, not self-managing.** [expanded] comes from the caller and taps report back
 * through [onToggle]. A list where opening one row closes the others is the common case,
 * and a row holding its own state cannot do that.
 *
 * The whole header is the tap target, not just the chevron, and it keeps the minimum touch
 * height regardless of how short the title is.
 *
 * @param title the always-visible line.
 * @param expanded whether [content] is currently showing.
 * @param onToggle called when the header is tapped.
 * @param toggleContentDescription what the header announces to a screen reader. The chevron
 *   itself is decorative — the state it indicates is already in the row's own semantics, and
 *   announcing both reads the row out twice.
 * @param content shown below the header while [expanded].
 */
@Composable
fun OdoExpandableRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    toggleContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Turns rather than swaps to a second icon: the rotation is what tells the owner the row
    // they tapped is the one that opened, which matters in a list where several can look alike.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f,
        label = "OdoExpandableRow chevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = OdoTheme.spacing.minTouchTarget)
                .padding(vertical = OdoTheme.spacing.sm)
                .then(
                    if (toggleContentDescription != null) {
                        Modifier.semantics { this.contentDescription = toggleContentDescription }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(
                title,
                style = OdoTheme.typography.heading,
                modifier = Modifier.weight(1f),
            )
            OdoIcon(
                IcChevronDown,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = OdoTheme.spacing.sm),
                content = content,
            )
        }
    }
}

/** Half a turn, so the chevron points up when the row is open. */
private const val CHEVRON_EXPANDED_DEGREES = 180f

@OdoThemePreviews
@Composable
private fun OdoExpandableRowPreview() = OdoPreview {
    Column {
        OdoExpandableRow(title = "Closed row", expanded = false, onToggle = {}) {
            OdoText("Hidden while closed.", style = OdoTheme.typography.body)
        }
        OdoDivider()
        OdoExpandableRow(title = "Open row", expanded = true, onToggle = {}) {
            OdoText(
                "The answer sits here, in body type, dimmed against the title above it.",
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}
