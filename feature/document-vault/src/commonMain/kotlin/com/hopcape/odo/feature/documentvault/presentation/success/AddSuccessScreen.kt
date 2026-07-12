package com.hopcape.odo.feature.documentvault.presentation.success

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcLightbulb
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_success_add_another
import com.hopcape.odo.feature.documentvault.resources.dv_success_back
import com.hopcape.odo.feature.documentvault.resources.dv_success_body_after
import com.hopcape.odo.feature.documentvault.resources.dv_success_body_before
import com.hopcape.odo.feature.documentvault.resources.dv_success_card_body
import com.hopcape.odo.feature.documentvault.resources.dv_success_card_title
import com.hopcape.odo.feature.documentvault.resources.dv_success_title
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * Terminal success after a document is saved to the vault — confirms it's stored and
 * that Odo will remind the owner before it expires, with the Health Score bump the
 * complete documentation earned. Full-screen (no top bar).
 */
@Composable
internal fun AddSuccessScreen(
    docName: String,
    reminderDate: LocalDate,
    remindDaysBefore: Int,
    onBackToDocuments: () -> Unit,
    onAddAnother: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        topBar = {},
        bottomBar = { SuccessActions(onBackToDocuments = onBackToDocuments, onAddAnother = onAddAnother) },
        containerColor = OdoTheme.colors.bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = OdoTheme.spacing.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlowBadge()
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoText(stringResource(Res.string.dv_success_title, docName), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoText(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.dv_success_body_before))
                    withStyle(SpanStyle(color = OdoTheme.colors.text, fontWeight = FontWeight.SemiBold)) {
                        append(formatDate(reminderDate))
                    }
                    append(stringResource(Res.string.dv_success_body_after, remindDaysBefore))
                },
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoCard(color = OdoTheme.colors.surface) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        OdoIcon(IcLightbulb, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                        OdoText(stringResource(Res.string.dv_success_card_title), style = OdoTheme.typography.heading)
                        OdoText(stringResource(Res.string.dv_success_card_body), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlowBadge() {
    val tone = OdoTheme.colors.success
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(tone.copy(alpha = 0.10f)))
        Box(Modifier.size(92.dp).clip(CircleShape).background(tone.copy(alpha = 0.20f)))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(tone),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.bg, size = OdoTheme.iconSizes.large)
        }
    }
}

@Composable
private fun SuccessActions(onBackToDocuments: () -> Unit, onAddAnother: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OdoButton(stringResource(Res.string.dv_success_back), onClick = onBackToDocuments, modifier = Modifier.fillMaxWidth())
        OdoText(
            stringResource(Res.string.dv_success_add_another),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
            modifier = Modifier
                .clip(OdoTheme.shapes.pill)
                .clickable(onClick = onAddAnother)
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        )
    }
}

@OdoThemePreviews
@Composable
private fun AddSuccessScreenPreview() = OdoPreview(padded = false) {
    AddSuccessScreen(
        docName = "Insurance",
        reminderDate = LocalDate(2026, 6, 26),
        remindDaysBefore = 7,
        onBackToDocuments = {},
        onAddAnother = {},
    )
}
