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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.icons.IcPdf
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_detail_share
import com.hopcape.odo.feature.servicelog.resources.sl_share_bill_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_share_download_pdf
import com.hopcape.odo.feature.servicelog.resources.sl_share_email
import com.hopcape.odo.feature.servicelog.resources.sl_share_failed
import com.hopcape.odo.feature.servicelog.resources.sl_share_more
import com.hopcape.odo.feature.servicelog.resources.sl_share_preparing
import com.hopcape.odo.feature.servicelog.resources.sl_share_subtitle
import com.hopcape.odo.feature.servicelog.resources.sl_share_whatsapp
import org.jetbrains.compose.resources.stringResource

/** Shown where the car could not be named. */
private const val EMPTY_FIELD = "—"

/** Dimmed rather than hidden, so the sheet does not change shape mid-tap. */
private const val DISABLED_ALPHA = 0.4f

/**
 * The "share verified record" sheet **body** — the resale summary and the targets it can be
 * sent to. Shown as a bottom-sheet destination
 * ([OdoDestination.ServiceLog.Share][com.hopcape.odo.core.navigation.OdoDestination.ServiceLog.Share]);
 * the [androidx.compose.material3.ModalBottomSheet] chrome comes from the navigation layer.
 * Stateless: it renders [state] and reports taps through [onEvent].
 *
 * Every target sends the same PDF. There is no link row: the Resale Passport that would
 * issue one is Phase 2, and a row offering a URL that resolves to nothing is worse than no
 * row at all.
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

        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
            ShareTargetButton(
                label = stringResource(Res.string.sl_share_whatsapp),
                background = OdoTheme.colors.success,
                iconTint = OdoTheme.colors.onAccent,
                icon = IcChatOutlined,
                state = state,
                target = ShareTarget.WHATSAPP,
                onEvent = onEvent,
            )
            ShareTargetButton(
                label = stringResource(Res.string.sl_share_email),
                background = OdoTheme.colors.surfaceRaised,
                iconTint = OdoTheme.colors.text,
                icon = IcEnvelope,
                state = state,
                target = ShareTarget.EMAIL,
                onEvent = onEvent,
            )
            ShareTargetButton(
                label = stringResource(Res.string.sl_share_more),
                background = OdoTheme.colors.surfaceRaised,
                iconTint = OdoTheme.colors.text,
                icon = IcShare,
                state = state,
                target = ShareTarget.MORE,
                onEvent = onEvent,
            )
        }

        OdoButton(
            text = stringResource(
                if (state.export is ExportUiState.Rendering) Res.string.sl_share_preparing else Res.string.sl_share_download_pdf,
            ),
            onClick = { onEvent(ShareRecordEvent.ShareViaClicked(ShareTarget.DOWNLOAD)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isBusy,
            leadingIcon = { OdoIcon(IcPdf, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )

        if (state.export is ExportUiState.Failed) {
            OdoText(
                text = stringResource(Res.string.sl_share_failed),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.danger,
            )
        }
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
            when (content) {
                ShareRecordUiState.Content.Loading -> Unit

                is ShareRecordUiState.Content.Loaded -> OdoText(
                    stringResource(
                        Res.string.sl_share_subtitle,
                        content.carName ?: EMPTY_FIELD,
                        content.verifiedCount,
                        content.serviceCount,
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )

                // One bill going out, so the line names the bill: car, day, amount.
                is ShareRecordUiState.Content.LoadedBill -> OdoText(
                    stringResource(
                        Res.string.sl_share_bill_subtitle,
                        content.carName ?: EMPTY_FIELD,
                        formatDate(content.serviceDate),
                        content.amount.formatRupees(),
                    ),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

/**
 * One target. Shows a spinner in place of its icon while the document it asked for is being
 * produced, and every target goes flat and untappable for the duration — the same file is on
 * its way regardless of which one is tapped next.
 */
@Composable
private fun ShareTargetButton(
    label: String,
    background: Color,
    iconTint: Color,
    icon: ImageVector,
    state: ShareRecordUiState,
    target: ShareTarget,
    onEvent: (ShareRecordEvent) -> Unit,
) {
    val isRendering = (state.export as? ExportUiState.Rendering)?.target == target

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        modifier = Modifier.alpha(if (state.isBusy && !isRendering) DISABLED_ALPHA else 1f),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(OdoTheme.shapes.card)
                .background(background)
                .clickable(enabled = !state.isBusy) { onEvent(ShareRecordEvent.ShareViaClicked(target)) },
            contentAlignment = Alignment.Center,
        ) {
            if (isRendering) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = iconTint, strokeWidth = 2.dp)
            } else {
                OdoIcon(icon, contentDescription = null, tint = iconTint, size = OdoTheme.iconSizes.medium)
            }
        }
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
    }
}
