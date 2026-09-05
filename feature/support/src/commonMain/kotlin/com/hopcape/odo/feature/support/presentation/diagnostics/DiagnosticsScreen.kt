package com.hopcape.odo.feature.support.presentation.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoListItem
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoSwitch
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.support.presentation.formatBytes
import com.hopcape.odo.feature.support.resources.Res
import com.hopcape.odo.feature.support.resources.sp_diag_headline
import com.hopcape.odo.feature.support.resources.sp_diag_intro
import com.hopcape.odo.feature.support.resources.sp_diag_item_app
import com.hopcape.odo.feature.support.resources.sp_diag_item_email
import com.hopcape.odo.feature.support.resources.sp_diag_item_email_sub
import com.hopcape.odo.feature.support.resources.sp_diag_item_logs
import com.hopcape.odo.feature.support.resources.sp_diag_item_scans
import com.hopcape.odo.feature.support.resources.sp_diag_item_scans_sub
import com.hopcape.odo.feature.support.resources.sp_diag_never
import com.hopcape.odo.feature.support.resources.sp_diag_never_label
import com.hopcape.odo.feature.support.resources.sp_diag_retention
import com.hopcape.odo.feature.support.resources.sp_diag_send
import com.hopcape.odo.feature.support.resources.sp_diag_send_nothing
import com.hopcape.odo.feature.support.resources.sp_diag_send_unsized
import com.hopcape.odo.feature.support.resources.sp_diag_title
import org.jetbrains.compose.resources.stringResource

/**
 * Every line that would leave the device, with a switch on each.
 *
 * A screen rather than the one-question sheet it replaces. What an upload contains is not a
 * thing to summarise in a sentence, and asking yes-or-no to a summary is asking for consent
 * to something nobody was shown.
 *
 * **What is never collected is stated, not implied.** A list of what goes says nothing about
 * what does not, and the owner has no way to find out — so the second card says it outright.
 */
@Composable
internal fun DiagnosticsScreen(
    state: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.sp_diag_title),
        onBack = { onEvent(DiagnosticsEvent.BackClicked) },
        bottomBar = {
            OdoButton(
                // The size only when it is known. A button reading "Send · 0 B" states a
                // fact that is false, on the one screen whose whole job is stating true ones.
                text = when {
                    !state.canSend -> stringResource(Res.string.sp_diag_send_nothing)
                    state.sizeBytes > 0L ->
                        stringResource(Res.string.sp_diag_send, formatBytes(state.sizeBytes))
                    else -> stringResource(Res.string.sp_diag_send_unsized)
                },
                onClick = { onEvent(DiagnosticsEvent.SendClicked) },
                enabled = state.canSend && !state.sending,
                loading = state.sending,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(OdoTheme.spacing.screenEdge),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoText(
                text = stringResource(Res.string.sp_diag_headline),
                style = OdoTheme.typography.display,
            )
            OdoText(
                text = stringResource(Res.string.sp_diag_intro),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            // No padding of its own: the dividers between the rows run edge to edge, and
            // each row carries its own inset instead.
            OdoCard(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.Top) {
                PartRow(
                    part = DiagnosticPart.APP_AND_DEVICE,
                    title = stringResource(Res.string.sp_diag_item_app),
                    // The line itself, not a description of it: this is what would be sent.
                    subtitle = state.appLine,
                    state = state,
                    onEvent = onEvent,
                )
                OdoDivider()
                PartRow(
                    part = DiagnosticPart.LOGS,
                    title = stringResource(Res.string.sp_diag_item_logs),
                    subtitle = state.logLine,
                    state = state,
                    onEvent = onEvent,
                )
                OdoDivider()
                PartRow(
                    part = DiagnosticPart.BILL_SCANS,
                    title = stringResource(Res.string.sp_diag_item_scans),
                    subtitle = stringResource(Res.string.sp_diag_item_scans_sub),
                    state = state,
                    onEvent = onEvent,
                )
                // Only when there is one. A switch for an address the account does not have
                // promises a reply that cannot arrive.
                if (state.emailLine.isNotBlank()) {
                    OdoDivider()
                    PartRow(
                        part = DiagnosticPart.ACCOUNT_EMAIL,
                        title = stringResource(Res.string.sp_diag_item_email),
                        subtitle = stringResource(Res.string.sp_diag_item_email_sub),
                        state = state,
                        onEvent = onEvent,
                    )
                }
            }

            OdoCard {
                OdoText(
                    text = stringResource(Res.string.sp_diag_never_label),
                    style = OdoTheme.typography.label,
                    color = OdoTheme.colors.textMuted,
                )
                OdoText(
                    text = stringResource(Res.string.sp_diag_never),
                    style = OdoTheme.typography.body,
                )
            }

            OdoText(
                text = stringResource(Res.string.sp_diag_retention, state.retentionDays),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

@Composable
private fun PartRow(
    part: DiagnosticPart,
    title: String,
    subtitle: String,
    state: DiagnosticsUiState,
    onEvent: (DiagnosticsEvent) -> Unit,
) {
    val on = state.isOn(part)
    OdoListItem(
        headline = title,
        supporting = subtitle.takeIf { it.isNotBlank() },
        // The whole row, not just the switch. A 48dp target beside a paragraph of consent
        // copy is the wrong half of the row to make tappable.
        onClick = { onEvent(DiagnosticsEvent.PartToggled(part, !on)) },
        modifier = Modifier.padding(horizontal = OdoTheme.spacing.cardPadding),
        trailing = {
            OdoSwitch(
                checked = on,
                onCheckedChange = { onEvent(DiagnosticsEvent.PartToggled(part, it)) },
            )
        },
    )
}

@OdoThemePreviews
@Composable
private fun DiagnosticsPreview() = OdoPreview(padded = false) {
    DiagnosticsScreen(
        state = DiagnosticsUiState(
            appLine = "v1.3.3.3 · Pixel 7a · Android 14",
            logLine = "last 7 days · 3 entries",
            emailLine = "r•••@gmail.com",
            sizeBytes = 245_760L,
        ),
        onEvent = {},
    )
}
