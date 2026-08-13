package com.hopcape.odo.feature.documentvault.presentation.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcCardFilled
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcIdCard
import com.hopcape.odo.core.designsystem.icons.IcLeafFilled
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentId
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.presentation.DocumentVaultTestTags
import com.hopcape.odo.feature.documentvault.presentation.state.Loadable
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_action_add
import com.hopcape.odo.feature.documentvault.resources.dv_add_document
import com.hopcape.odo.feature.documentvault.resources.dv_doc_insurance
import com.hopcape.odo.feature.documentvault.resources.dv_doc_licence
import com.hopcape.odo.feature.documentvault.resources.dv_doc_loan
import com.hopcape.odo.feature.documentvault.resources.dv_doc_other
import com.hopcape.odo.feature.documentvault.resources.dv_doc_puc
import com.hopcape.odo.feature.documentvault.resources.dv_doc_rc
import com.hopcape.odo.feature.documentvault.resources.dv_header_add_body
import com.hopcape.odo.feature.documentvault.resources.dv_header_add_title
import com.hopcape.odo.feature.documentvault.resources.dv_header_attention_body
import com.hopcape.odo.feature.documentvault.resources.dv_header_attention_title_many
import com.hopcape.odo.feature.documentvault.resources.dv_header_attention_title_one
import com.hopcape.odo.feature.documentvault.resources.dv_header_covered_body
import com.hopcape.odo.feature.documentvault.resources.dv_header_covered_title
import com.hopcape.odo.feature.documentvault.resources.dv_pill_expired
import com.hopcape.odo.feature.documentvault.resources.dv_pill_expires_soon
import com.hopcape.odo.feature.documentvault.resources.dv_pill_not_added
import com.hopcape.odo.feature.documentvault.resources.dv_pill_valid
import com.hopcape.odo.feature.documentvault.resources.dv_reminder
import com.hopcape.odo.feature.documentvault.resources.dv_status_expired
import com.hopcape.odo.feature.documentvault.resources.dv_status_expires_in
import com.hopcape.odo.feature.documentvault.resources.dv_status_lifetime
import com.hopcape.odo.feature.documentvault.resources.dv_status_not_added
import com.hopcape.odo.feature.documentvault.resources.dv_status_valid_till
import com.hopcape.odo.feature.documentvault.resources.dv_cd_back
import com.hopcape.odo.feature.documentvault.resources.dv_title
import org.jetbrains.compose.resources.stringResource

/**
 * The document vault overview — every paper Odo tracks, with its renewal status. Leads with
 * a status-toned summary, then one card per document (add, open or renew depending on
 * status), plus an "add a document" affordance.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun DocumentVaultScreen(
    state: DocumentVaultUiState,
    onAdd: (DocumentType) -> Unit,
    onOpen: (DocumentId) -> Unit,
    onAddDocument: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.dv_title),
        onBack = onBack,
        backContentDescription = stringResource(Res.string.dv_cd_back),
        bottomBar = { AddDocumentBar(onAddDocument) },
    ) { padding ->
        when (val content = state.content) {
            Loadable.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { OdoLoadingIndicator() }

            is Loadable.Failed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(OdoTheme.spacing.screenEdge),
                contentAlignment = Alignment.Center,
            ) {
                OdoText(
                    content.message.asString(),
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
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            ) {
                VaultHeader(content.value.header)
                content.value.rows.forEach { row ->
                    DocumentCard(row = row, onAdd = onAdd, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun VaultHeader(header: VaultHeader) = when (header) {
    is VaultHeader.NeedsAttention -> HeaderCard(
        tone = OdoTheme.colors.warning,
        title = attentionTitle(header.count),
        body = stringResource(Res.string.dv_header_attention_body, docName(header.first).lowercase()),
    )

    is VaultHeader.Covered -> HeaderCard(
        tone = OdoTheme.colors.success,
        title = stringResource(Res.string.dv_header_covered_title),
        body = stringResource(Res.string.dv_header_covered_body, header.count),
    )

    VaultHeader.AddPrompt -> HeaderCard(
        tone = OdoTheme.colors.accent,
        title = stringResource(Res.string.dv_header_add_title),
        body = stringResource(Res.string.dv_header_add_body),
    )
}

@Composable
private fun HeaderCard(tone: Color, title: String, body: String) {
    OdoCard(
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(IcShieldFilled, tone)
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(body, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
    }
}

@Composable
private fun DocumentCard(
    row: DocumentRow,
    onAdd: (DocumentType) -> Unit,
    onOpen: (DocumentId) -> Unit,
) {
    val tone = rowTone(row)
    val openable = row as? DocumentRow.OnFile
    OdoCard(
        modifier = Modifier.testTag(DocumentVaultTestTags.row(row.type)),
        onClick = openable?.let { { onOpen(it.id) } },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(typeIcon(row.type), tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(openable?.title ?: docName(row.type), style = OdoTheme.typography.heading)
                OdoText(rowSubtitle(row), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            StatusEnd(row = row, onAdd = onAdd)
        }
        val reminderDays = openable?.takeIf { it.needsAttention }?.reminderDaysBefore
        if (reminderDays != null) {
            HorizontalDivider(color = OdoTheme.colors.border)
            ReminderRow(days = reminderDays)
        }
    }
}

@Composable
private fun StatusEnd(row: DocumentRow, onAdd: (DocumentType) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        StatusPill(row)
        val actionTag = Modifier.testTag(DocumentVaultTestTags.rowAction(row.type))
        when {
            row is DocumentRow.Missing ->
                DocActionButton(stringResource(Res.string.dv_action_add), actionTag) { onAdd(row.type) }

            else -> OdoIcon(
                IcChevronRight,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
            )
        }
    }
}

@Composable
private fun StatusPill(row: DocumentRow) {
    val (labelRes, tone) = when (row) {
        is DocumentRow.Missing -> Res.string.dv_pill_not_added to OdoBadgeTone.Neutral
        is DocumentRow.OnFile -> when (row.validity) {
            is DocumentValidity.ExpiringSoon -> Res.string.dv_pill_expires_soon to OdoBadgeTone.Warning
            is DocumentValidity.Expired -> Res.string.dv_pill_expired to OdoBadgeTone.Danger
            else -> Res.string.dv_pill_valid to OdoBadgeTone.Success
        }
    }
    OdoBadge(text = stringResource(labelRes), tone = tone)
}

/** Small accent-outlined pill action ("Add" / "Renew"). */
@Composable
private fun DocActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(OdoTheme.shapes.pill)
            .border(1.dp, OdoTheme.colors.accent, OdoTheme.shapes.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        OdoText(label, style = OdoTheme.typography.label, color = OdoTheme.colors.accent)
    }
}

@Composable
private fun ReminderRow(days: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OdoIcon(IcBellFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
        OdoText(stringResource(Res.string.dv_reminder, days), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
    }
}

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
private fun AddDocumentBar(onClick: () -> Unit) {
    val border = OdoTheme.colors.border
    Box(
        Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.sm, bottom = OdoTheme.spacing.md),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(OdoTheme.shapes.card)
                .drawBehind {
                    drawRoundRect(
                        color = border,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)),
                        cornerRadius = CornerRadius(16.dp.toPx()),
                    )
                }
                .clickable(onClick = onClick)
                .padding(vertical = OdoTheme.spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                OdoIcon(IcPlusLarge, contentDescription = null, size = OdoTheme.iconSizes.small)
                OdoText(stringResource(Res.string.dv_add_document), style = OdoTheme.typography.heading)
            }
        }
    }
}

@Composable
private fun attentionTitle(count: Int): String = stringResource(
    if (count == 1) Res.string.dv_header_attention_title_one else Res.string.dv_header_attention_title_many,
    count,
)

@Composable
private fun rowTone(row: DocumentRow): Color = when (row) {
    is DocumentRow.Missing -> OdoTheme.colors.textMuted
    is DocumentRow.OnFile -> when (row.validity) {
        is DocumentValidity.ExpiringSoon -> OdoTheme.colors.warning
        is DocumentValidity.Expired -> OdoTheme.colors.danger
        else -> OdoTheme.colors.success
    }
}

private fun typeIcon(type: DocumentType): ImageVector = when (type) {
    DocumentType.INSURANCE -> IcShieldCheck
    DocumentType.PUC -> IcLeafFilled
    DocumentType.RC -> IcCardFilled
    DocumentType.LICENCE -> IcIdCard
    DocumentType.LOAN, DocumentType.OTHER -> IcFileFilled
}

@Composable
internal fun docName(type: DocumentType): String = stringResource(
    when (type) {
        DocumentType.INSURANCE -> Res.string.dv_doc_insurance
        DocumentType.PUC -> Res.string.dv_doc_puc
        DocumentType.RC -> Res.string.dv_doc_rc
        DocumentType.LICENCE -> Res.string.dv_doc_licence
        DocumentType.LOAN -> Res.string.dv_doc_loan
        DocumentType.OTHER -> Res.string.dv_doc_other
    },
)

@Composable
private fun rowSubtitle(row: DocumentRow): String = when (row) {
    is DocumentRow.Missing -> stringResource(Res.string.dv_status_not_added)
    is DocumentRow.OnFile -> when (val validity = row.validity) {
        DocumentValidity.NoExpiry -> stringResource(Res.string.dv_status_lifetime)
        is DocumentValidity.Valid -> stringResource(Res.string.dv_status_valid_till, formatDate(validity.until))
        is DocumentValidity.ExpiringSoon ->
            stringResource(Res.string.dv_status_expires_in, validity.daysLeft, formatDate(validity.until))
        is DocumentValidity.Expired -> stringResource(Res.string.dv_status_expired, formatDate(validity.since))
    }
}

@OdoThemePreviews
@Composable
private fun DocumentVaultEmptyPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultEmpty(), {}, {}, {}, {},)
}

@OdoThemePreviews
@Composable
private fun DocumentVaultCoveredPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultCovered(), {}, {}, {}, {},)
}

@OdoThemePreviews
@Composable
private fun DocumentVaultAttentionPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultAttention(), {}, {}, {}, {},)
}
