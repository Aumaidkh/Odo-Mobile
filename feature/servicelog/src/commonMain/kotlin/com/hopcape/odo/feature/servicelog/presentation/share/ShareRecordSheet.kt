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
import androidx.compose.runtime.Composable
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
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
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

/** Shown where the car could not be named. */
private const val EMPTY_FIELD = "—"

/**
 * The "share verified record" sheet **body** — the resale-passport summary, its public
 * link, and the share targets. Shown as a bottom-sheet destination
 * ([OdoDestination.ServiceLog.Share]); the [androidx.compose.material3.ModalBottomSheet]
 * chrome comes from the navigation layer. Stateless: it renders [state] and reports taps
 * through [onEvent].
 */
@Composable
internal fun ShareRecordSheetContent(
    state: ShareRecordUiState,
    onEvent: (ShareRecordEvent) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.screenEdge).padding(bottom = OdoTheme.spacing.md).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Header(state.content)
        // Absent until the Resale Passport issues a link — a row with no URL in it would
        // offer the owner something to copy that goes nowhere.
        (state.link as? PassportLinkUiState.Ready)?.let { link ->
            LinkRow(link, onCopy = { onEvent(ShareRecordEvent.CopyLinkClicked) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
            ShareTargetButton(stringResource(Res.string.sl_share_whatsapp), OdoTheme.colors.success, OdoTheme.colors.onAccent, IcChatOutlined) {
                onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.WHATSAPP))
            }
            ShareTargetButton(stringResource(Res.string.sl_share_email), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcEnvelope) {
                onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.EMAIL))
            }
            ShareTargetButton(stringResource(Res.string.sl_share_more), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcShare) {
                onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.MORE))
            }
        }
        OdoButton(
            text = stringResource(Res.string.sl_share_download_pdf),
            onClick = { onEvent(ShareRecordEvent.DownloadPdfClicked) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
    }
}

@Composable
private fun Header(content: ShareRecordUiState.Content) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.success.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcShieldCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.sl_detail_share), style = OdoTheme.typography.heading)
            if (content is ShareRecordUiState.Content.Loaded) {
                OdoText(
                    stringResource(
                        Res.string.sl_share_subtitle,
                        content.carName ?: EMPTY_FIELD,
                        content.verifiedCount,
                        content.serviceCount,
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun LinkRow(link: PassportLinkUiState.Ready, onCopy: () -> Unit) {
    OdoCard(color = OdoTheme.colors.surfaceRaised) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcLink, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
            OdoText(link.url, style = OdoTheme.typography.body, modifier = Modifier.weight(1f))
            OdoText(
                text = stringResource(if (link.copied) Res.string.sl_share_copied else Res.string.sl_share_copy),
                style = OdoTheme.typography.label,
                color = if (link.copied) OdoTheme.colors.success else OdoTheme.colors.accent,
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
