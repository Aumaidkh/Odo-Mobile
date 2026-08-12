package com.hopcape.odo.feature.documentvault.presentation.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcFileFilled
import com.hopcape.odo.core.designsystem.icons.IcWindow
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.platform.file.rememberFilePicker
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_add_camera_body
import com.hopcape.odo.feature.documentvault.resources.dv_add_camera_title
import com.hopcape.odo.feature.documentvault.resources.dv_add_digilocker_body
import com.hopcape.odo.feature.documentvault.resources.dv_add_digilocker_title
import com.hopcape.odo.feature.documentvault.resources.dv_add_fastest
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_insurance
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_licence
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_loan
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_other
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_puc
import com.hopcape.odo.feature.documentvault.resources.dv_add_kind_rc
import com.hopcape.odo.feature.documentvault.resources.dv_add_method_label
import com.hopcape.odo.feature.documentvault.resources.dv_add_privacy
import com.hopcape.odo.feature.documentvault.resources.dv_add_title
import com.hopcape.odo.feature.documentvault.resources.dv_add_type_label
import com.hopcape.odo.feature.documentvault.resources.dv_add_upload_body
import com.hopcape.odo.feature.documentvault.resources.dv_add_upload_title
import com.hopcape.odo.feature.documentvault.resources.dv_cd_close
import org.jetbrains.compose.resources.stringResource

/** DigiLocker's brand-ish blue — no semantic blue token exists, so it lives here. */
private val DigiLockerBlue = Color(0xFF4E86E8)

/**
 * The "Add document" screen — the entry point to adding a paper. The owner picks a
 * document type, then a capture method: scan (fastest — Odo reads the expiry), upload a
 * file, or import from DigiLocker. Each method routes into its own next step
 * (review & confirm) in M2.
 *
 * State-free: renders [state] and forwards intents.
 */
@Composable
internal fun AddDocumentScreen(
    state: AddDocumentUiState,
    onTypeSelect: (DocumentType) -> Unit,
    onScan: () -> Unit,
    onFilePicked: (String?) -> Unit,
    onImportDigiLocker: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val launchFilePicker = rememberFilePicker(onPicked = onFilePicked)
    OdoScreen(modifier = modifier, topBar = { AddTopBar(onClose) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                SectionLabel(stringResource(Res.string.dv_add_type_label))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    AddDocumentUiState.OFFERED_TYPES.forEach { type ->
                        OdoChip(
                            label = typeLabel(type),
                            selected = type == state.selectedType,
                            onClick = { onTypeSelect(type) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                SectionLabel(stringResource(Res.string.dv_add_method_label))
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                    MethodCard(
                        icon = IcCamera,
                        iconTone = OdoTheme.colors.accent,
                        title = stringResource(Res.string.dv_add_camera_title),
                        body = stringResource(Res.string.dv_add_camera_body),
                        highlighted = true,
                        fastest = true,
                        onClick = onScan,
                    )
                    MethodCard(
                        icon = IcFileFilled,
                        iconTone = OdoTheme.colors.text,
                        title = stringResource(Res.string.dv_add_upload_title),
                        body = stringResource(Res.string.dv_add_upload_body),
                        onClick = launchFilePicker,
                    )
                    MethodCard(
                        icon = IcWindow,
                        iconTone = DigiLockerBlue,
                        title = stringResource(Res.string.dv_add_digilocker_title),
                        body = stringResource(Res.string.dv_add_digilocker_body),
                        onClick = onImportDigiLocker,
                    )
                }
            }

            state.submission.error?.let { message ->
                OdoText(
                    message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.lg),
                )
            }

            OdoText(
                stringResource(Res.string.dv_add_privacy),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = OdoTheme.spacing.lg),
            )
        }
    }
}

@Composable
private fun AddTopBar(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(OdoTheme.colors.surfaceRaised).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcClose, contentDescription = stringResource(Res.string.dv_cd_close), tint = OdoTheme.colors.text, size = OdoTheme.iconSizes.medium)
        }
        OdoText(stringResource(Res.string.dv_add_title), style = OdoTheme.typography.title)
    }
}

@Composable
private fun MethodCard(
    icon: ImageVector,
    iconTone: Color,
    title: String,
    body: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    fastest: Boolean = false,
) {
    OdoCard(
        onClick = onClick,
        color = if (highlighted) OdoTheme.colors.accent.copy(alpha = 0.10f) else OdoTheme.colors.surface,
        border = if (highlighted) BorderStroke(1.dp, OdoTheme.colors.accent.copy(alpha = 0.45f)) else null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(iconTone.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(icon, contentDescription = null, tint = iconTone, size = OdoTheme.iconSizes.medium)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    OdoText(title, style = OdoTheme.typography.heading)
                    if (fastest) OdoBadge(text = stringResource(Res.string.dv_add_fastest), tone = OdoBadgeTone.Accent)
                }
                OdoText(body, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    OdoText(text, style = OdoTheme.typography.label, color = OdoTheme.colors.text)
}

@Composable
private fun typeLabel(type: DocumentType): String = stringResource(
    when (type) {
        DocumentType.INSURANCE -> Res.string.dv_add_kind_insurance
        DocumentType.PUC -> Res.string.dv_add_kind_puc
        DocumentType.RC -> Res.string.dv_add_kind_rc
        DocumentType.LICENCE -> Res.string.dv_add_kind_licence
        DocumentType.LOAN -> Res.string.dv_add_kind_loan
        DocumentType.OTHER -> Res.string.dv_add_kind_other
    },
)

@OdoThemePreviews
@Composable
private fun AddDocumentScreenPreview() = OdoPreview(padded = false) {
    AddDocumentScreen(
        state = AddDocumentUiState(),
        onTypeSelect = {},
        onScan = {},
        onFilePicked = {},
        onImportDigiLocker = {},
        onClose = {},
    )
}
