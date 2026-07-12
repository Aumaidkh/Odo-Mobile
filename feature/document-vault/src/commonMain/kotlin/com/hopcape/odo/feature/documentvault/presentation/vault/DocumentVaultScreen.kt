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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.designsystem.icons.IcBell
import com.hopcape.odo.core.designsystem.icons.IcCard
import com.hopcape.odo.core.designsystem.icons.IcIdCard
import com.hopcape.odo.core.designsystem.icons.IcLeaf
import com.hopcape.odo.core.designsystem.icons.IcPlusLarge
import com.hopcape.odo.core.designsystem.icons.IcShield
import com.hopcape.odo.core.designsystem.icons.IcShieldCheck
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_action_add
import com.hopcape.odo.feature.documentvault.resources.dv_action_renew
import com.hopcape.odo.feature.documentvault.resources.dv_add_document
import com.hopcape.odo.feature.documentvault.resources.dv_doc_insurance
import com.hopcape.odo.feature.documentvault.resources.dv_doc_licence
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
import com.hopcape.odo.feature.documentvault.resources.dv_title
import org.jetbrains.compose.resources.stringResource

/**
 * The document vault overview — every paper Odo tracks (insurance, PUC, RC, licence)
 * with its renewal status. Leads with a status-toned summary, then one card per
 * document (add / open / renew depending on status), plus an "add a document" affordance.
 *
 * State-free: renders [state] and forwards intents. Real documents + the reminder
 * engine land in M2.
 */
@Composable
internal fun DocumentVaultScreen(
    state: DocumentVaultUiState,
    onAdd: (DocumentType) -> Unit,
    onRenew: (DocumentType) -> Unit,
    onOpen: (DocumentType) -> Unit,
    onAddDocument: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.dv_title),
        onBack = onBack,
        bottomBar = { AddDocumentBar(onAddDocument) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            VaultHeader(state)
            state.documents.forEach { row ->
                DocumentCard(row = row, onAdd = onAdd, onRenew = onRenew, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun VaultHeader(state: DocumentVaultUiState) {
    val attention = state.attention
    when {
        attention.isNotEmpty() -> HeaderCard(
            tone = OdoTheme.colors.warning,
            title = attentionTitle(attention.size),
            body = stringResource(Res.string.dv_header_attention_body, docName(attention.first().type).lowercase()),
        )
        state.allValid -> HeaderCard(
            tone = OdoTheme.colors.success,
            title = stringResource(Res.string.dv_header_covered_title),
            body = stringResource(Res.string.dv_header_covered_body, state.documents.size),
        )
        else -> HeaderCard(
            tone = OdoTheme.colors.accent,
            title = stringResource(Res.string.dv_header_add_title),
            body = stringResource(Res.string.dv_header_add_body),
        )
    }
}

@Composable
private fun HeaderCard(tone: Color, title: String, body: String) {
    OdoCard(
        color = tone.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(IcShield, tone)
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
    onRenew: (DocumentType) -> Unit,
    onOpen: (DocumentType) -> Unit,
) {
    val tone = statusTone(row.status)
    val openable = row.status is DocStatus.Valid
    OdoCard(onClick = if (openable) ({ onOpen(row.type) }) else null) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconChip(typeIcon(row.type), tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(docName(row.type), style = OdoTheme.typography.heading)
                OdoText(statusSubtitle(row.status), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
            StatusEnd(row = row, onAdd = onAdd, onRenew = onRenew)
        }
        if (row.status is DocStatus.ExpiresSoon) {
            HorizontalDivider(color = OdoTheme.colors.border)
            ReminderRow(days = row.status.days)
        }
    }
}

@Composable
private fun StatusEnd(row: DocumentRow, onAdd: (DocumentType) -> Unit, onRenew: (DocumentType) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        StatusPill(row.status)
        when (row.status) {
            is DocStatus.NotAdded -> DocActionButton(stringResource(Res.string.dv_action_add)) { onAdd(row.type) }
            is DocStatus.ExpiresSoon, is DocStatus.Expired -> DocActionButton(stringResource(Res.string.dv_action_renew)) { onRenew(row.type) }
            is DocStatus.Valid -> OdoIcon(
                IcArrowLeft,
                contentDescription = null,
                tint = OdoTheme.colors.textMuted,
                size = OdoTheme.iconSizes.small,
                modifier = Modifier.rotate(180f),
            )
        }
    }
}

@Composable
private fun StatusPill(status: DocStatus) {
    val (labelRes, tone) = when (status) {
        is DocStatus.NotAdded -> Res.string.dv_pill_not_added to OdoBadgeTone.Neutral
        is DocStatus.Valid -> Res.string.dv_pill_valid to OdoBadgeTone.Success
        is DocStatus.ExpiresSoon -> Res.string.dv_pill_expires_soon to OdoBadgeTone.Warning
        is DocStatus.Expired -> Res.string.dv_pill_expired to OdoBadgeTone.Danger
    }
    OdoBadge(text = stringResource(labelRes), tone = tone)
}

/** Small accent-outlined pill action ("Add" / "Renew"). */
@Composable
private fun DocActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
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
        OdoIcon(IcBell, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.small)
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
private fun statusTone(status: DocStatus): Color = when (status) {
    is DocStatus.NotAdded -> OdoTheme.colors.textMuted
    is DocStatus.Valid -> OdoTheme.colors.success
    is DocStatus.ExpiresSoon -> OdoTheme.colors.warning
    is DocStatus.Expired -> OdoTheme.colors.danger
}

private fun typeIcon(type: DocumentType): ImageVector = when (type) {
    DocumentType.INSURANCE -> IcShieldCheck
    DocumentType.PUC -> IcLeaf
    DocumentType.RC -> IcCard
    DocumentType.LICENCE -> IcIdCard
}

@Composable
private fun docName(type: DocumentType): String = stringResource(
    when (type) {
        DocumentType.INSURANCE -> Res.string.dv_doc_insurance
        DocumentType.PUC -> Res.string.dv_doc_puc
        DocumentType.RC -> Res.string.dv_doc_rc
        DocumentType.LICENCE -> Res.string.dv_doc_licence
    },
)

@Composable
private fun statusSubtitle(status: DocStatus): String = when (status) {
    is DocStatus.NotAdded -> stringResource(Res.string.dv_status_not_added)
    is DocStatus.Valid -> status.validTill
        ?.let { stringResource(Res.string.dv_status_valid_till, formatDate(it)) }
        ?: stringResource(Res.string.dv_status_lifetime)
    is DocStatus.ExpiresSoon -> stringResource(Res.string.dv_status_expires_in, status.days, formatDate(status.on))
    is DocStatus.Expired -> stringResource(Res.string.dv_status_expired, formatDate(status.on))
}

@OdoThemePreviews
@Composable
private fun DocumentVaultEmptyPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultEmpty(), {}, {}, {}, {}, {})
}

@OdoThemePreviews
@Composable
private fun DocumentVaultCoveredPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultCovered(), {}, {}, {}, {}, {})
}

@OdoThemePreviews
@Composable
private fun DocumentVaultAttentionPreview() = OdoPreview(padded = false) {
    DocumentVaultScreen(sampleVaultAttention(), {}, {}, {}, {}, {})
}
