package com.hopcape.odo.feature.support.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * The support sheet's body container. Scrolls, because the hub is taller than a
 * comfortable sheet on a small screen and the resources chips must stay reachable.
 */
@Composable
internal fun SupportSheet(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        content = content,
    )
}

/** A small tracked-caps section header ("GET IN TOUCH"). */
@Composable
internal fun SectionLabel(text: String) {
    OdoText(
        text,
        style = OdoTheme.typography.caption,
        color = OdoTheme.colors.textDim,
        modifier = Modifier.padding(start = OdoTheme.spacing.xs),
    )
}

/** A card grouping support rows, hairline-divided. */
@Composable
internal fun SupportGroup(content: @Composable ColumnScope.() -> Unit) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
}

/** A rounded, tinted tile holding a single glyph. */
@Composable
internal fun IconTile(icon: ImageVector, tint: Color = OdoTheme.colors.accent, size: Dp = 44.dp) {
    Box(
        modifier = Modifier.size(size).clip(OdoTheme.shapes.field).background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.medium)
    }
}

/**
 * One support row: a tinted icon tile, a title over a dim subtitle, and either a caller
 * supplied [trailing] (the "Online" pill, the open-ticket count) or a chevron.
 */
@Composable
internal fun SupportRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = OdoTheme.colors.accent,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, tint = iconTint)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(title, style = OdoTheme.typography.heading, maxLines = 1)
            OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, maxLines = 1)
        }
        if (trailing != null) {
            trailing()
        } else {
            OdoIcon(
                IcChevronRight,
                contentDescription = null,
                tint = OdoTheme.colors.textDim,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}
