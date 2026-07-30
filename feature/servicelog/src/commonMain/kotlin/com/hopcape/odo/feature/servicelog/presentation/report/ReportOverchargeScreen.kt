package com.hopcape.odo.feature.servicelog.presentation.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoRadioButton
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.servicelog.presentation.ui.components.asString
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_not_found
import com.hopcape.odo.feature.servicelog.resources.sl_optional
import com.hopcape.odo.feature.servicelog.resources.sl_report_intro
import com.hopcape.odo.feature.servicelog.resources.sl_report_note_hint
import com.hopcape.odo.feature.servicelog.resources.sl_report_not_flagged
import com.hopcape.odo.feature.servicelog.resources.sl_report_note_label
import com.hopcape.odo.feature.servicelog.resources.sl_report_question
import com.hopcape.odo.feature.servicelog.resources.sl_report_reason_above_market
import com.hopcape.odo.feature.servicelog.resources.sl_report_reason_unnecessary_parts
import com.hopcape.odo.feature.servicelog.resources.sl_report_reason_work_not_done
import com.hopcape.odo.feature.servicelog.resources.sl_report_done
import com.hopcape.odo.feature.servicelog.resources.sl_report_submit
import com.hopcape.odo.feature.servicelog.resources.sl_report_success_body
import com.hopcape.odo.feature.servicelog.resources.sl_report_success_title
import com.hopcape.odo.feature.servicelog.resources.sl_report_title
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_over
import org.jetbrains.compose.resources.stringResource

/**
 * The report-overcharge flow — cite what went wrong on a flagged entry (single-select
 * reason + an optional note) and submit. Stateless: the route owns state + navigation.
 */
@Composable
internal fun ReportOverchargeScreen(
    state: ReportOverchargeUiState,
    onEvent: (ReportOverchargeEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = state.content
    OdoScreen(
        title = stringResource(Res.string.sl_report_title),
        onBack = { onEvent(ReportOverchargeEvent.BackClicked) },
        modifier = modifier,
        bottomBar = {
            when {
                state.isSubmitted -> DoneBar(onDone = { onEvent(ReportOverchargeEvent.DoneClicked) })
                content is ReportOverchargeUiState.Content.Loaded ->
                    SubmitBar(state, onSubmit = { onEvent(ReportOverchargeEvent.SubmitClicked) })
            }
        },
    ) { padding ->
        when {
            state.isSubmitted -> ReportSuccess(padding)

            content == ReportOverchargeUiState.Content.Loading ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { OdoLoadingIndicator() }

            content == ReportOverchargeUiState.Content.NotFound ->
                Message(stringResource(Res.string.sl_not_found), padding)

            content == ReportOverchargeUiState.Content.NotFlagged ->
                Message(stringResource(Res.string.sl_report_not_flagged), padding)

            content is ReportOverchargeUiState.Content.Loaded -> ReportContent(content.header, state, onEvent, padding)
        }
    }
}

/** A centered explanation where there is nothing to report on. */
@Composable
private fun Message(text: String, padding: androidx.compose.foundation.layout.PaddingValues) {
    Box(
        Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(text, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim)
    }
}

/** Shown once the report is filed — a centered confirmation with a Done action. */
@Composable
private fun ReportSuccess(padding: androidx.compose.foundation.layout.PaddingValues) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(OdoTheme.colors.success.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.large)
        }
        OdoText(stringResource(Res.string.sl_report_success_title), style = OdoTheme.typography.title)
        OdoText(
            stringResource(Res.string.sl_report_success_body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DoneBar(onDone: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
    ) {
        OdoButton(text = stringResource(Res.string.sl_report_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ReportContent(
    header: ReportHeaderUiState,
    state: ReportOverchargeUiState,
    onEvent: (ReportOverchargeEvent) -> Unit,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        HeaderCard(header)
        OdoText(stringResource(Res.string.sl_report_intro), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)

        OdoText(stringResource(Res.string.sl_report_question), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OverchargeReason.entries.forEach { reason ->
                ReasonOption(
                    reason = reason,
                    selected = reason == state.reason,
                    onSelect = { onEvent(ReportOverchargeEvent.ReasonSelected(reason)) },
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(stringResource(Res.string.sl_report_note_label), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
                OdoText(stringResource(Res.string.sl_optional), style = OdoTheme.typography.label, color = OdoTheme.colors.textMuted)
            }
            OdoInputField(
                value = state.note,
                onValueChange = { onEvent(ReportOverchargeEvent.NoteChanged(it)) },
                placeholder = stringResource(Res.string.sl_report_note_hint),
                singleLine = false,
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )
        }
    }
}

/** The flagged entry strip — amber, warning icon, "workshop · Rs. X over" + work/date. */
@Composable
private fun HeaderCard(header: ReportHeaderUiState) {
    OdoCard(
        color = OdoTheme.colors.warning.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, OdoTheme.colors.warning.copy(alpha = 0.45f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.warning.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.medium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    text = "${header.workshopName ?: "—"} · ${stringResource(Res.string.sl_verdict_over, header.amountOver.formatRupees())}",
                    style = OdoTheme.typography.heading,
                )
                OdoText(
                    text = buildString {
                        header.workDone.asString()?.let { append(it).append(" · ") }
                        append(formatDate(header.serviceDate))
                    },
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
    }
}

@Composable
private fun ReasonOption(reason: OverchargeReason, selected: Boolean, onSelect: () -> Unit) {
    OdoCard(
        onClick = onSelect,
        border = BorderStroke(1.dp, if (selected) OdoTheme.colors.accent else OdoTheme.colors.border),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            OdoRadioButton(selected = selected, onClick = null)
            OdoText(reasonLabel(reason), style = OdoTheme.typography.body)
        }
    }
}

@Composable
private fun SubmitBar(state: ReportOverchargeUiState, onSubmit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        state.submission.error?.let {
            OdoText(it.asString(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
        }
        OdoButton(
            text = stringResource(Res.string.sl_report_submit),
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.canSubmit,
            loading = state.submission.isInFlight,
        )
    }
}

@Composable
private fun reasonLabel(reason: OverchargeReason): String = stringResource(
    when (reason) {
        OverchargeReason.ABOVE_MARKET_RATE -> Res.string.sl_report_reason_above_market
        OverchargeReason.WORK_NOT_DONE -> Res.string.sl_report_reason_work_not_done
        OverchargeReason.UNNECESSARY_PARTS -> Res.string.sl_report_reason_unnecessary_parts
    },
)
