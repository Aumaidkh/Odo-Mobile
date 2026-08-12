package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Odo's themed top app bar — the default header [OdoScreen] renders when given a
 * title. Left-aligned title in [OdoTheme.typography] `title`, brand colours, and
 * an optional back affordance. Features rarely call this directly; they pass a
 * `title` / `onBack` to [OdoScreen] instead.
 *
 * Back is an [OdoCircularIconButton] — a soft disc rather than a bare chevron — which
 * is the brand's affordance for "leave this screen" everywhere it appears, from a
 * full-screen editor's close button to this bar. Trailing actions should be circular
 * too, so the header reads as one row of round controls.
 *
 * @param title shown left-aligned; truncated with an ellipsis if it overflows.
 * @param onBack when non-null, a circular back button is shown that invokes this.
 * @param backContentDescription screen-reader label for that back button. Copy-free
 *   like the rest of the design system, so the feature supplies the localised string.
 * @param actions trailing action slot, right-aligned — pass [OdoCircularIconButton]s.
 * @param colors override the brand defaults if a screen needs a different header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = OdoTheme.colors.bg,
        scrolledContainerColor = OdoTheme.colors.bg,
        titleContentColor = OdoTheme.colors.text,
        navigationIconContentColor = OdoTheme.colors.text,
        actionIconContentColor = OdoTheme.colors.text,
    ),
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = OdoTheme.typography.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                OdoCircularIconButton(
                    imageVector = IcArrowLeft,
                    contentDescription = backContentDescription,
                    onClick = onBack,
                    modifier = Modifier.padding(start = OdoTheme.spacing.xs),
                )
            }
        },
        actions = actions,
        colors = colors,
    )
}

@OdoThemePreviews
@Composable
private fun OdoTopBarPreview() = OdoPreview(padded = false) {
    OdoTopBar(title = "Apni gaadi", onBack = {})
}
