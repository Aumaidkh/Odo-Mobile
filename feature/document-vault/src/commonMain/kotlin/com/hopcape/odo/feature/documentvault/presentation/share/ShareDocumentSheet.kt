package com.hopcape.odo.feature.documentvault.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcDownload
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.presentation.vault.docName
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_share_download
import com.hopcape.odo.feature.documentvault.resources.dv_status_expired
import com.hopcape.odo.feature.documentvault.resources.dv_status_lifetime
import com.hopcape.odo.feature.documentvault.resources.dv_share_send
import com.hopcape.odo.feature.documentvault.resources.dv_share_subtitle
import com.hopcape.odo.feature.documentvault.resources.dv_share_title
import org.jetbrains.compose.resources.stringResource

/**
 * The "share document" sheet **body** — the document summary and the two things that can be
 * done with the file: hand it to another app, or keep a copy. Shown as a bottom-sheet
 * destination ([OdoDestination.Documents.Share]); the
 * [androidx.compose.material3.ModalBottomSheet] chrome comes from the navigation layer.
 *
 * Both actions are disabled when the stored file is gone — a restore brings the row back
 * without the bytes, and there is nothing to send.
 */
@Composable
internal fun ShareDocumentSheetContent(
    state: ShareDocumentUiState,
    onShare: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Header(state)
        // Two actions rather than a row of app icons. Odo holds the paper as a file and
        // nothing else — there is no link to copy — and which app it goes to is a question
        // the system's own chooser answers better than four buttons can.
        OdoButton(
            text = stringResource(Res.string.dv_share_send),
            onClick = onShare,
            enabled = state.isFileAvailable,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        OdoButton(
            text = stringResource(Res.string.dv_share_download),
            onClick = onDownload,
            variant = OdoButtonVariant.Secondary,
            enabled = state.isFileAvailable,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { OdoIcon(IcDownload, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        // Saving a copy opens nothing, so the sheet says what happened. Shown here rather
        // than as a passing message because the sheet is still on screen either way.
        state.notice?.let {
            OdoText(
                it.asString(),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun Header(state: ShareDocumentUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcShieldFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(
                stringResource(Res.string.dv_share_title, (state.title ?: docName(state.type)).lowercase()),
                style = OdoTheme.typography.heading,
            )
            OdoText(
                shareSubtitle(state.validity),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/** "Valid till 3 Jul 2026", or the lifetime line for a document that never expires. */
@Composable
private fun shareSubtitle(validity: DocumentValidity): String = when (validity) {
    DocumentValidity.NoExpiry -> stringResource(Res.string.dv_status_lifetime)
    is DocumentValidity.Valid -> stringResource(Res.string.dv_share_subtitle, formatDate(validity.until))
    is DocumentValidity.ExpiringSoon -> stringResource(Res.string.dv_share_subtitle, formatDate(validity.until))
    is DocumentValidity.Expired -> stringResource(Res.string.dv_status_expired, formatDate(validity.since))
}

@OdoThemePreviews
@Composable
private fun ShareDocumentSheetPreview() = OdoPreview {
    ShareDocumentSheetContent(state = sampleShareDocument(), onShare = {}, onDownload = {})
}
