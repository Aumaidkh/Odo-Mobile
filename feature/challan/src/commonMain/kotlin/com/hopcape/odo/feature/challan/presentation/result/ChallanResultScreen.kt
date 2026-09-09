package com.hopcape.odo.feature.challan.presentation.result

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcLockFilled
import com.hopcape.odo.core.designsystem.icons.IcMapPin
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.challan.presentation.ChallanTestTags
import com.hopcape.odo.feature.challan.presentation.list.ChallanRow
import com.hopcape.odo.feature.challan.presentation.state.Loadable
import com.hopcape.odo.feature.challan.resources.Res
import com.hopcape.odo.feature.challan.resources.ch_cd_back
import com.hopcape.odo.feature.challan.resources.ch_refresh
import com.hopcape.odo.feature.challan.resources.ch_result_check_another
import com.hopcape.odo.feature.challan.resources.ch_result_clean_body
import com.hopcape.odo.feature.challan.resources.ch_result_clean_title
import com.hopcape.odo.feature.challan.resources.ch_result_privacy_note
import com.hopcape.odo.feature.challan.resources.ch_result_transfer_body
import com.hopcape.odo.feature.challan.resources.ch_result_transfer_title
import com.hopcape.odo.feature.challan.resources.ch_section_pending
import com.hopcape.odo.feature.challan.resources.ch_status_checked
import org.jetbrains.compose.resources.stringResource

/**
 * A stranger's plate, read-only (mockup 8): the transfer warning, the pending list, and
 * no pay button anywhere — a buyer cannot settle someone else's challans, and a button
 * would promise otherwise.
 */
@Composable
internal fun ChallanResultScreen(
    state: ChallanResultUiState,
    onEvent: (ChallanResultEvent) -> Unit,
) {
    OdoScreen(
        title = state.regNo,
        onBack = { onEvent(ChallanResultEvent.BackTapped) },
        backContentDescription = stringResource(Res.string.ch_cd_back),
        bottomBar = { BottomBar(onEvent) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).testTag(ChallanTestTags.RESULT_SCREEN)) {
            when (val content = state.content) {
                Loadable.Loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = OdoTheme.colors.accent,
                )

                is Loadable.Failed -> OdoText(
                    content.message.asString(),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = OdoTheme.spacing.xl),
                )

                is Loadable.Ready -> ResultBody(content.value, state, onEvent)
            }
        }
    }
}

@Composable
private fun ResultBody(
    content: ChallanResultContent,
    state: ChallanResultUiState,
    onEvent: (ChallanResultEvent) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = OdoTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        StatusPill(content.checkedAgo.asString(), state.refreshing) { onEvent(ChallanResultEvent.RefreshTapped) }

        if (content.transfer == null) {
            CleanCard(state.regNo)
        } else {
            TransferCard(content.transfer)
            OdoText(
                stringResource(Res.string.ch_section_pending).uppercase(),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
                modifier = Modifier.padding(top = OdoTheme.spacing.sm),
            )
            content.rows.forEach { ResultChallanCard(it) }
        }

        Spacer(Modifier.weight(1f))
        PrivacyFootnote()
    }
}

@Composable
private fun StatusPill(checkedAgo: String, refreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .background(OdoTheme.colors.surfaceRaised)
            .clickable(enabled = !refreshing, onClick = onRefresh)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(OdoTheme.colors.success),
        )
        OdoText(
            stringResource(Res.string.ch_status_checked, checkedAgo),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = OdoTheme.colors.accent,
            )
        } else {
            OdoText(
                stringResource(Res.string.ch_refresh),
                style = OdoTheme.typography.label.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TransferCard(transfer: TransferWarning) {
    OdoCard(
        color = OdoTheme.colors.warning.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, OdoTheme.colors.warning.copy(alpha = 0.5f)),
        modifier = Modifier.testTag(ChallanTestTags.RESULT_TRANSFER_CARD),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.warning, size = OdoTheme.iconSizes.small)
            OdoText(
                transfer.badge.asString().uppercase(),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.warning,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        OdoText(stringResource(Res.string.ch_result_transfer_title), style = OdoTheme.typography.title)
        OdoText(
            stringResource(Res.string.ch_result_transfer_body),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun CleanCard(regNo: String) {
    OdoCard {
        OdoText(stringResource(Res.string.ch_result_clean_title), style = OdoTheme.typography.title)
        OdoText(
            stringResource(Res.string.ch_result_clean_body, regNo),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )
    }
}

@Composable
private fun ResultChallanCard(row: ChallanRow) {
    OdoCard(modifier = Modifier.testTag(ChallanTestTags.challanCard(row.id))) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoText(row.violation, style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
            OdoText(row.amount, style = OdoTheme.typography.heading, maxLines = 1)
        }
        OdoText(row.number, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted, maxLines = 1)
        OdoDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.location?.let { location ->
                OdoIcon(IcMapPin, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
                OdoText(
                    location,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(Modifier.weight(1f))
            OdoText(row.date, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textMuted, maxLines = 1)
        }
    }
}

@Composable
private fun PrivacyFootnote() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.field)
            .background(OdoTheme.colors.surface)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcLockFilled, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.small)
        OdoText(
            stringResource(Res.string.ch_result_privacy_note),
            style = OdoTheme.typography.bodySmall,
            color = OdoTheme.colors.textDim,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomBar(onEvent: (ChallanResultEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OdoTheme.spacing.screenEdge,
                end = OdoTheme.spacing.screenEdge,
                top = OdoTheme.spacing.sm,
                bottom = OdoTheme.spacing.sm,
            ),
    ) {
        OdoButton(
            text = stringResource(Res.string.ch_result_check_another),
            onClick = { onEvent(ChallanResultEvent.CheckAnotherTapped) },
            variant = OdoButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
