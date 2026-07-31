package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** A rounded, tinted tile holding a single glyph — the recurring leading icon in the garage UI. */
@Composable
internal fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = OdoTheme.colors.accent,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(OdoTheme.shapes.field)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.large)
    }
}

/** The brand car glyph on an accent-tinted tile — shared by the card, sheets and rows. */
@Composable
internal fun CarAvatar(size: Dp = 48.dp, tint: Color = OdoTheme.colors.accent) =
    IconTile(IcCar, tint = tint, size = size)

/**
 * The standard bottom-sheet body container for the garage sheets — the sheet chrome
 * (drag handle, scrim, container) comes from
 * [com.hopcape.odo.core.navigation.ModalBottomSheetSceneStrategy]; this lays the
 * content out with the screen-edge padding + nav-bar inset every sheet shares.
 */
@Composable
internal fun GarageSheet(
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(OdoTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** A full-screen editor header: a circular close (X) affordance + the screen title. */
@Composable
internal fun CloseTopBar(title: String, closeLabel: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoCircularIconButton(
            IcClose,
            contentDescription = closeLabel,
            onClick = onClose,
            variant = OdoCircularIconButtonVariant.Raised,
        )
        OdoText(title, style = OdoTheme.typography.title)
    }
}

/** A pinned full-width CTA row for the full-screen editors, clear of the nav bar. */
@Composable
internal fun GarageBottomButton(
    label: String,
    onClick: () -> Unit,
    variant: OdoButtonVariant = OdoButtonVariant.Primary,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(vertical = OdoTheme.spacing.md)
            .navigationBarsPadding(),
    ) {
        OdoButton(
            label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            variant = variant,
            enabled = enabled,
        )
    }
}
