package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCurrencyDollar
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The rounded tile that heads a value row, an option card, or a matched-car card. One helper
 * so every such glyph sits in the same square with the same wash.
 *
 * ```
 * OdoIconTile(IcCurrencyDollar, tint = OdoTheme.colors.textDim)
 * ```
 */
@Composable
fun OdoIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = OdoIconTileDefaults.Size,
    tint: Color = OdoTheme.colors.accent,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(OdoTheme.shapes.small)
            .background(tint.copy(alpha = OdoIconTileDefaults.WashAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = contentDescription, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

object OdoIconTileDefaults {
    val Size: Dp = 44.dp
    const val WashAlpha = 0.12f
}

@OdoThemePreviews
@Composable
private fun OdoIconTilePreview() = OdoPreview {
    OdoIconTile(IcCurrencyDollar)
}
