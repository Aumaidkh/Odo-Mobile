package com.hopcape.odo.feature.documentvault.presentation.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.OdoThumbnail
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcDotsVertical
import com.hopcape.odo.core.designsystem.icons.IcDownload
import com.hopcape.odo.core.designsystem.icons.IcEyeFilled
import com.hopcape.odo.core.designsystem.icons.IcImage
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.icons.IcTrash
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.platform.file.StoredFileKind
import com.hopcape.odo.core.platform.file.StoredFileKinds
import com.hopcape.odo.core.platform.file.rememberStoredImage
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.presentation.vault.docName
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_badge_pdf
import com.hopcape.odo.feature.documentvault.resources.dv_cd_more
import com.hopcape.odo.feature.documentvault.resources.dv_cd_preview_file
import com.hopcape.odo.feature.documentvault.resources.dv_detail_days_suffix
import com.hopcape.odo.feature.documentvault.resources.dv_detail_expired_for
import com.hopcape.odo.feature.documentvault.resources.dv_detail_expires_in
import com.hopcape.odo.feature.documentvault.resources.dv_detail_file_missing
import com.hopcape.odo.feature.documentvault.resources.dv_detail_issued_on
import com.hopcape.odo.feature.documentvault.resources.dv_detail_lifetime
import com.hopcape.odo.feature.documentvault.resources.dv_detail_renew
import com.hopcape.odo.feature.documentvault.resources.dv_cd_back
import com.hopcape.odo.feature.documentvault.resources.dv_detail_verified
import com.hopcape.odo.feature.documentvault.resources.dv_detail_view
import com.hopcape.odo.feature.documentvault.resources.dv_menu_delete
import com.hopcape.odo.feature.documentvault.resources.dv_menu_edit_dates
import com.hopcape.odo.feature.documentvault.resources.dv_menu_download
import com.hopcape.odo.feature.documentvault.resources.dv_menu_share
import com.hopcape.odo.feature.documentvault.resources.dv_reminder
import com.hopcape.odo.feature.documentvault.resources.dv_status_expired
import com.hopcape.odo.feature.documentvault.resources.dv_status_lifetime
import com.hopcape.odo.feature.documentvault.resources.dv_status_valid_till
import org.jetbrains.compose.resources.stringResource

/**
 * A single document's detail — its name and Verified badge, the expiry countdown with the
 * bar, the next reminder, and the file actions (share, download, delete) behind
 * the overflow menu, with "Renew now" pinned to the bottom for a document that needs it.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun DocumentDetailScreen(
    state: DocumentDetailUiState,
    onView: () -> Unit,
    onRenew: () -> Unit,
    onEditDates: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = (state.content as? Loadable.Ready)?.value
    OdoScreen(
        modifier = modifier,
        title = content?.let { it.title ?: docName(it.type) }.orEmpty(),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.dv_cd_back),
        actions = {
            if (content != null) {
                DocumentMenu(
                    onEditDates = onEditDates,
                    onShare = onShare,
                    onDownload = onDownload,
                    onDelete = onDelete,
                )
            }
        },
        bottomBar = { if (content?.validity?.needsAttention == true) RenewBar(onRenew) },
    ) { padding ->
        when (val loadable = state.content) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { OdoLoadingIndicator() }

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(
                    loadable.message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
            }

            is Loadable.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(vertical = OdoTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
            ) {
                HeroCard(content = loadable.value, onView = onView)
                ValidityCard(content = loadable.value)
            }
        }
    }
}

@Composable
private fun RowScope.DocumentMenu(
    onEditDates: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OdoIconButton(IcDotsVertical, contentDescription = stringResource(Res.string.dv_cd_more), onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // First in the list: a document with no expiry produces no reminder, and this is
            // the only way to give it one.
            MenuItem(stringResource(Res.string.dv_menu_edit_dates), IcClock) { expanded = false; onEditDates() }
            MenuItem(stringResource(Res.string.dv_menu_share), IcShare) { expanded = false; onShare() }
            MenuItem(stringResource(Res.string.dv_menu_download), IcDownload) { expanded = false; onDownload() }
            HorizontalDivider(color = OdoTheme.colors.border)
            MenuItem(stringResource(Res.string.dv_menu_delete), IcTrash, tint = OdoTheme.colors.danger) { expanded = false; onDelete() }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, tint: Color = OdoTheme.colors.text, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { OdoText(label, style = OdoTheme.typography.body, color = tint) },
        leadingIcon = { OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small) },
        onClick = onClick,
    )
}

/** The document itself: what it is, whether it is an official copy, and how to open it. */
@Composable
private fun HeroCard(content: DocumentDetailContent, onView: () -> Unit) {
    OdoCard(
        color = OdoTheme.colors.accent.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.35f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.Top) {
            if (content.isFileAvailable && content.storagePath != null) {
                DocumentPreviewTile(storagePath = content.storagePath, onView = onView)
            } else {
                IconChip(IcShieldFilled, OdoTheme.colors.accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(content.title ?: docName(content.type), style = OdoTheme.typography.heading)
                content.issuedOn?.let {
                    OdoText(
                        stringResource(Res.string.dv_detail_issued_on, formatDate(it)),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textMuted,
                    )
                }
            }
            if (content.isVerified) {
                OdoBadge(
                    text = stringResource(Res.string.dv_detail_verified),
                    tone = OdoBadgeTone.Success,
                    leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
                )
            }
        }
        if (content.isFileAvailable) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { ViewButton(onView) }
        } else {
            OdoText(
                stringResource(Res.string.dv_detail_file_missing),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun ViewButton(onView: () -> Unit) {
    Box(
        Modifier
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.surfaceRaised)
            .clickable(onClick = onView)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcEyeFilled, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(stringResource(Res.string.dv_detail_view), style = OdoTheme.typography.label)
        }
    }
}

/**
 * How much cover is left. The bar is drawn only when the document knows both of its dates;
 * without an issue date there is nothing to measure the fraction against.
 */
@Composable
private fun ValidityCard(content: DocumentDetailContent) {
    val tone = validityTone(content.validity)
    OdoCard(
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.35f)),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(IcClock, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(validityHeadline(content.validity), style = OdoTheme.typography.heading)
                validitySubtitle(content.validity)?.let {
                    OdoText(it, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                }
            }
            daysCounter(content.validity)?.let { days ->
                Row(verticalAlignment = Alignment.Bottom) {
                    OdoText("$days", style = OdoTheme.typography.title, color = tone, modifier = Modifier.alignByBaseline())
                    OdoText(
                        stringResource(Res.string.dv_detail_days_suffix),
                        style = OdoTheme.typography.body,
                        color = tone,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
        }
        content.validityProgress?.let { progress ->
            Box(Modifier.fillMaxWidth().height(8.dp).clip(OdoTheme.shapes.pill).background(OdoTheme.colors.surfaceRaised)) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(8.dp)
                        .clip(OdoTheme.shapes.pill)
                        .background(Brush.horizontalGradient(listOf(OdoTheme.colors.success, tone))),
                )
            }
        }
        content.reminderDaysBefore?.let { days ->
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OdoIcon(IcBellFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
                OdoText(
                    stringResource(Res.string.dv_reminder, days),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

/**
 * The document itself, shown small and tappable, in place of the generic shield.
 *
 * The point of a vault is being able to see the paper, and a thumbnail is what turns "a row
 * that says Insurance" into "my policy". A PDF has no image to draw here, so it falls back to
 * a PDF glyph and a badge saying which of the two it is.
 */
@Composable
private fun DocumentPreviewTile(storagePath: String, onView: () -> Unit) {
    val isPdf = StoredFileKinds.of(storagePath) == StoredFileKind.PDF
    OdoThumbnail(
        image = rememberStoredImage(storagePath),
        contentDescription = stringResource(Res.string.dv_cd_preview_file),
        modifier = Modifier.size(PREVIEW_TILE_SIZE),
        placeholderIcon = if (isPdf) IcPdf else IcImage,
        badge = if (isPdf) stringResource(Res.string.dv_badge_pdf) else null,
        onClick = onView,
    )
}

/** Big enough to recognise the document, small enough to leave the title room. */
private val PREVIEW_TILE_SIZE = 64.dp

@Composable
private fun IconChip(icon: ImageVector, tone: Color) {
    Box(
        Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(tone.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(icon, contentDescription = null, tint = tone, size = OdoTheme.iconSizes.medium)
    }
}

@Composable
private fun RenewBar(onRenew: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.sm, bottom = OdoTheme.spacing.md),
    ) {
        OdoButton(stringResource(Res.string.dv_detail_renew), onClick = onRenew, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun validityTone(validity: DocumentValidity): Color = when (validity) {
    is DocumentValidity.ExpiringSoon -> OdoTheme.colors.warning
    is DocumentValidity.Expired -> OdoTheme.colors.danger
    else -> OdoTheme.colors.success
}

@Composable
private fun validityHeadline(validity: DocumentValidity): String = when (validity) {
    DocumentValidity.NoExpiry -> stringResource(Res.string.dv_detail_lifetime)
    is DocumentValidity.Valid -> stringResource(Res.string.dv_detail_expires_in, validity.daysLeft)
    is DocumentValidity.ExpiringSoon -> stringResource(Res.string.dv_detail_expires_in, validity.daysLeft)
    is DocumentValidity.Expired -> stringResource(Res.string.dv_detail_expired_for, validity.daysAgo)
}

@Composable
private fun validitySubtitle(validity: DocumentValidity): String? = when (validity) {
    DocumentValidity.NoExpiry -> stringResource(Res.string.dv_status_lifetime)
    is DocumentValidity.Valid -> stringResource(Res.string.dv_status_valid_till, formatDate(validity.until))
    is DocumentValidity.ExpiringSoon -> stringResource(Res.string.dv_status_valid_till, formatDate(validity.until))
    is DocumentValidity.Expired -> stringResource(Res.string.dv_status_expired, formatDate(validity.since))
}

/** The big number beside the headline. A lifetime document has no countdown. */
private fun daysCounter(validity: DocumentValidity): Int? = when (validity) {
    DocumentValidity.NoExpiry -> null
    is DocumentValidity.Valid -> validity.daysLeft
    is DocumentValidity.ExpiringSoon -> validity.daysLeft
    is DocumentValidity.Expired -> validity.daysAgo
}

@OdoThemePreviews
@Composable
private fun DocumentDetailPreview() = OdoPreview(padded = false) {
    DocumentDetailScreen(sampleDocumentDetail(), {}, {}, {}, {}, {}, {}, {},)
}

@OdoThemePreviews
@Composable
private fun DocumentDetailLifetimePreview() = OdoPreview(padded = false) {
    DocumentDetailScreen(sampleLifetimeDocumentDetail(), {}, {}, {}, {}, {}, {}, {},)
}
