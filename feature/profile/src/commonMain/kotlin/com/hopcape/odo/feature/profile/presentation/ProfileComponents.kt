package com.hopcape.odo.feature.profile.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.profile.resources.Res
import com.hopcape.odo.feature.profile.resources.pf_cd_avatar
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.stringResource

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
 * A circular avatar: the owner's stored photo when there is one, and their monogram until
 * then. The photo is read from app storage, so a file that has gone missing falls back to
 * the monogram rather than to an empty circle.
 */
@Composable
internal fun Avatar(
    initial: String,
    modifier: Modifier = Modifier,
    photoKey: String? = null,
    size: Dp = 56.dp,
    onClick: (() -> Unit)? = null,
) {
    val photo = rememberStoredImage(photoKey)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(OdoTheme.colors.accent)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = stringResource(Res.string.pf_cd_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            OdoText(initial, style = OdoTheme.typography.title, color = OdoTheme.colors.onAccent)
        }
    }
}

/** A small tracked-caps section header. */
@Composable
internal fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim, modifier = Modifier.padding(start = OdoTheme.spacing.xs))
}

/** A card grouping settings rows, hairline-divided. */
@Composable
internal fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    OdoCard(verticalArrangement = Arrangement.spacedBy(0.dp), content = content)
}

/** One settings row: leading icon, title, optional trailing value + chevron. */
@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    danger: Boolean = false,
    showChevron: Boolean = true,
    testTag: String? = null,
) {
    val color = if (danger) OdoTheme.colors.danger else OdoTheme.colors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clickable(onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(icon, contentDescription = null, tint = if (danger) OdoTheme.colors.danger else OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
        OdoText(title, style = OdoTheme.typography.heading, color = color, maxLines = 1, modifier = Modifier.weight(1f))
        if (value != null) OdoText(value, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        if (showChevron && !danger) Chevron()
    }
}

/** A hairline between grouped rows. */
@Composable
internal fun RowDivider() = OdoDivider()

/** A right-pointing chevron in dim ink. */
@Composable
internal fun Chevron(tint: Color = OdoTheme.colors.textDim) {
    OdoIcon(IcChevronRight, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small)
}

/** The standard bottom-sheet body container for the profile sheets. */
@Composable
internal fun ProfileSheet(
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

/** A full-screen editor header: a circular close (X) + title. */
@Composable
internal fun CloseTopBar(title: String, closeLabel: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
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
