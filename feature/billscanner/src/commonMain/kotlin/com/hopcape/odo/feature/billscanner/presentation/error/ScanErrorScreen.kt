package com.hopcape.odo.feature.billscanner.presentation.error

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_error_body
import com.hopcape.odo.feature.billscanner.resources.bs_error_manual
import com.hopcape.odo.feature.billscanner.resources.bs_error_retake
import com.hopcape.odo.feature.billscanner.resources.bs_error_tip_corners
import com.hopcape.odo.feature.billscanner.resources.bs_error_tip_flat
import com.hopcape.odo.feature.billscanner.resources.bs_error_tip_glare
import com.hopcape.odo.feature.billscanner.resources.bs_error_tips_label
import com.hopcape.odo.feature.billscanner.resources.bs_error_title
import com.hopcape.odo.feature.billscanner.resources.bs_scan_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Error state of the scan flow — the AI couldn't read the bill (e.g. too blurry).
 * Explains why, offers concrete tips for a better scan, and routes the owner to retry
 * or fall back to manual entry (never a silent failure, never a bad auto-fill).
 */
@Composable
internal fun ScanErrorScreen(
    onRetake: () -> Unit,
    onEnterManually: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.bs_scan_title),
        onBack = onBack,
        bottomBar = { ErrorActions(onRetake = onRetake, onEnterManually = onEnterManually) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = OdoTheme.spacing.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(72.dp).clip(OdoTheme.shapes.card).background(OdoTheme.colors.danger.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(IcWarning, contentDescription = null, tint = OdoTheme.colors.danger, size = OdoTheme.iconSizes.large)
            }
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoText(stringResource(Res.string.bs_error_title), style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoText(
                stringResource(Res.string.bs_error_body),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            TipsCard()
        }
    }
}

@Composable
private fun TipsCard() {
    OdoCard(color = OdoTheme.colors.surface) {
        OdoText(stringResource(Res.string.bs_error_tips_label), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        listOf(Res.string.bs_error_tip_flat, Res.string.bs_error_tip_glare, Res.string.bs_error_tip_corners).forEach { tip ->
            TipRow(tip)
        }
    }
}

@Composable
private fun TipRow(tip: StringResource) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.success, size = OdoTheme.iconSizes.small)
        OdoText(stringResource(tip), style = OdoTheme.typography.body)
    }
}

@Composable
private fun ErrorActions(onRetake: () -> Unit, onEnterManually: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoButton(stringResource(Res.string.bs_error_retake), onClick = onRetake, modifier = Modifier.fillMaxWidth())
        OdoButton(stringResource(Res.string.bs_error_manual), onClick = onEnterManually, modifier = Modifier.fillMaxWidth(), variant = OdoButtonVariant.Secondary)
    }
}

@OdoThemePreviews
@Composable
private fun ScanErrorScreenPreview() = OdoPreview(padded = false) {
    ScanErrorScreen(onRetake = {}, onEnterManually = {}, onBack = {})
}
