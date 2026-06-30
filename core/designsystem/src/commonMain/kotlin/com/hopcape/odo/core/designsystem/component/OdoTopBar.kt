package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Odo's themed top app bar — the default header [OdoScreen] renders when given a
 * title. Left-aligned title in [OdoTheme.typography] `title`, brand colours, and
 * an optional back affordance. Features rarely call this directly; they pass a
 * `title` / `onBack` to [OdoScreen] instead.
 *
 * @param title shown left-aligned; truncated with an ellipsis if it overflows.
 * @param onBack when non-null, a back chevron is shown that invokes this.
 * @param actions trailing action slot (e.g. icon buttons), right-aligned.
 * @param colors override the brand defaults if a screen needs a different header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
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
                IconButton(onClick = onBack) { OdoBackChevron() }
            }
        },
        actions = actions,
        colors = colors,
    )
}

/**
 * A back chevron drawn with the [OdoTheme.iconSizes] stroke weight, so the design
 * system carries no Material-icons dependency. Inherits the current content
 * colour from its [IconButton].
 */
@Composable
private fun OdoBackChevron(modifier: Modifier = Modifier) {
    val color = OdoTheme.colors.text
    val strokeDp = OdoTheme.iconSizes.stroke
    Canvas(modifier.size(OdoTheme.iconSizes.large)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.60f, h * 0.28f)
            lineTo(w * 0.38f, h * 0.50f)
            lineTo(w * 0.60f, h * 0.72f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeDp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@OdoThemePreviews
@Composable
private fun OdoTopBarPreview() = OdoPreview(padded = false) {
    OdoTopBar(title = "Apni gaadi", onBack = {})
}
