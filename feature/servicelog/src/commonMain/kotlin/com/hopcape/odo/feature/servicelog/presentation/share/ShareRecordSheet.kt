package com.hopcape.odo.feature.servicelog.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChat
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.icons.IcJournal
import com.hopcape.odo.core.designsystem.icons.IcLink
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_detail_share
import com.hopcape.odo.feature.servicelog.resources.sl_share_copied
import com.hopcape.odo.feature.servicelog.resources.sl_share_copy
import com.hopcape.odo.feature.servicelog.resources.sl_share_download_pdf
import com.hopcape.odo.feature.servicelog.resources.sl_share_email
import com.hopcape.odo.feature.servicelog.resources.sl_share_more
import com.hopcape.odo.feature.servicelog.resources.sl_share_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_share_whatsapp
import org.jetbrains.compose.resources.stringResource

/** Where a verified record can be shared to. */
internal enum class ShareTarget { WHATSAPP, EMAIL, MORE }

/** The record being shared — the resale passport summary + its public link. */
internal data class ShareRecordUiState(
    val carName: String,
    val verifiedCount: Int,
    val serviceCount: Int,
    val link: String,
    val copied: Boolean = false,
)

internal fun sampleShareRecord(copied: Boolean = false): ShareRecordUiState =
    ShareRecordUiState(carName = "Swift VXI", verifiedCount = 4, serviceCount = 6, link = "odo.app/p/swift-9F2K", copied = copied)

/**
 * Renders the "share verified record" sheet while [visible], managing the transient
 * "Copied" state itself. Callers just flip a boolean and pass [onDismiss].
 */
@Composable
internal fun ShareRecordSheetHost(visible: Boolean, onDismiss: () -> Unit) {
    if (!visible) return
    var copied by remember { mutableStateOf(false) }
    ShareRecordSheet(
        state = sampleShareRecord(copied),
        onCopy = { copied = true /* TODO: copy to clipboard */ },
        onShareVia = { /* TODO(passport): open the share target. */ },
        onDownloadPdf = { /* TODO(passport): render + save the PDF. */ },
        onDismiss = { copied = false; onDismiss() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareRecordSheet(
    state: ShareRecordUiState,
    onCopy: () -> Unit,
    onShareVia: (ShareTarget) -> Unit,
    onDownloadPdf: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = OdoTheme.colors.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge).padding(bottom = OdoTheme.spacing.md).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            Header(state)
            LinkRow(state, onCopy)
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
                ShareTargetButton(stringResource(Res.string.sl_share_whatsapp), OdoTheme.colors.success, OdoTheme.colors.onAccent, IcChat) { onShareVia(ShareTarget.WHATSAPP) }
                ShareTargetButton(stringResource(Res.string.sl_share_email), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcEnvelope) { onShareVia(ShareTarget.EMAIL) }
                ShareTargetButton(stringResource(Res.string.sl_share_more), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcShare) { onShareVia(ShareTarget.MORE) }
            }
            OdoButton(
                text = stringResource(Res.string.sl_share_download_pdf),
                onClick = onDownloadPdf,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
            )
        }
    }
}

@Composable
private fun Header(state: ShareRecordUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.success.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcShieldCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.sl_detail_share), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.sl_share_subtitle, state.carName, state.verifiedCount, state.serviceCount),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun LinkRow(state: ShareRecordUiState, onCopy: () -> Unit) {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcLink, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(state.link, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
            OdoText(
                text = stringResource(if (state.copied) Res.string.sl_share_copied else Res.string.sl_share_copy),
                style = OdoTheme.typography.label,
                color = if (state.copied) OdoTheme.colors.success else OdoTheme.colors.accent,
                modifier = Modifier.clip(OdoTheme.shapes.pill).clickable(onClick = onCopy).padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun ShareTargetButton(label: String, background: Color, iconTint: Color, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        Box(
            Modifier.size(56.dp).clip(OdoTheme.shapes.card).background(background).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(icon, contentDescription = null, tint = iconTint, size = OdoTheme.iconSizes.medium)
        }
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
    }
}
