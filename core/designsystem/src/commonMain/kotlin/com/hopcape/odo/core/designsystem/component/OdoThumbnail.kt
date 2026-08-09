package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/** The size a thumbnail takes unless the caller sizes it through the modifier. */
private val DefaultThumbnailSize: Dp = 72.dp

/**
 * A small preview tile for a file the app stored — a scanned bill, a policy page, an uploaded
 * RC. Shows the [image] when there is one, and [placeholderIcon] on a muted surface when there
 * is not: while it is still being decoded, or because the file is a PDF nobody has rendered a
 * page of yet.
 *
 * Give it an [onClick] to open the full viewer. A thumbnail the owner cannot enlarge is close
 * to useless — the text on a bill is unreadable at this size.
 *
 * ```
 * OdoThumbnail(
 *     image = rememberStoredImage(document.storagePath),
 *     contentDescription = "Insurance policy",
 *     badge = "PDF",
 *     onClick = { onPreview(document.id) },
 * )
 * ```
 *
 * @param badge short label over the bottom-left corner — "PDF", or a page count.
 * @param placeholderIcon shown when [image] is null; pass [IcPdf] for a PDF.
 */
@Composable
fun OdoThumbnail(
    image: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = IcFileFilled,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = OdoTheme.shapes.small
    Box(
        // The default size comes first so a caller's own .size() in [modifier] wins.
        modifier = Modifier
            .size(DefaultThumbnailSize)
            .then(modifier)
            .clip(shape)
            .background(OdoTheme.colors.surfaceRaised)
            .border(1.dp, OdoTheme.colors.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            OdoIcon(
                placeholderIcon,
                contentDescription = contentDescription,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.large,
            )
        }
        if (badge != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(OdoTheme.spacing.xs)
                    .clip(OdoTheme.shapes.pill)
                    .background(OdoTheme.colors.bg.copy(alpha = 0.75f))
                    .padding(horizontal = OdoTheme.spacing.sm, vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                OdoText(badge, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
            }
        }
    }
}

@OdoThemePreviews
@Composable
private fun OdoThumbnailPreview() = OdoPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
        OdoThumbnail(image = null, contentDescription = "Bill photo", onClick = {})
        OdoThumbnail(image = null, contentDescription = "Policy", placeholderIcon = IcPdf, badge = "PDF")
    }
}
