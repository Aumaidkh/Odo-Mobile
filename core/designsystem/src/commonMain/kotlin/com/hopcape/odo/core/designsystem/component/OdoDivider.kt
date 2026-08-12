package com.hopcape.odo.core.designsystem.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * A hairline horizontal rule in the brand's [OdoTheme.colors] `border` — the
 * separator between list rows and stacked sections. On Odo's near-black UI this
 * is deliberately faint; use it for structure, not emphasis (a full [OdoCard]
 * carries its own border, so dividers are for splitting content *within* a
 * surface).
 *
 * @param color the rule colour; defaults to the brand border token.
 * @param thickness the rule's height; 1dp hairline by default.
 */
@Composable
fun OdoDivider(
    modifier: Modifier = Modifier,
    color: Color = OdoTheme.colors.border,
    thickness: Dp = 1.dp,
) {
    HorizontalDivider(modifier = modifier, thickness = thickness, color = color)
}

/**
 * The vertical counterpart to [OdoDivider], for splitting side-by-side content
 * (e.g. two stats in a row). Give the parent an intrinsic or fixed height so the
 * rule has something to span.
 */
@Composable
fun OdoVerticalDivider(
    modifier: Modifier = Modifier,
    color: Color = OdoTheme.colors.border,
    thickness: Dp = 1.dp,
) {
    VerticalDivider(modifier = modifier, thickness = thickness, color = color)
}

@OdoThemePreviews
@Composable
private fun OdoDividerPreview() = OdoPreview {
    OdoDivider()
}
