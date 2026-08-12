package com.hopcape.odo.feature.billscanner.presentation.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme

/**
 * Shared scaffold for the bill-scanner's terminal outcome screens (save success,
 * report success): a centred glow badge, headline + body, an optional info card, and
 * a bottom action stack. No top bar — these are full-screen confirmations.
 */
@Composable
internal fun ResultScreen(
    badgeIcon: ImageVector,
    badgeTone: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    infoCard: (@Composable () -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
) {
    OdoScreen(
        modifier = modifier,
        topBar = {},
        bottomBar = { ResultActions(actions) },
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
            ResultBadge(icon = badgeIcon, tone = badgeTone)
            Spacer(Modifier.height(OdoTheme.spacing.xl))
            OdoText(title, style = OdoTheme.typography.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(OdoTheme.spacing.sm))
            OdoText(body, style = OdoTheme.typography.body, color = OdoTheme.colors.textDim, textAlign = TextAlign.Center)
            if (infoCard != null) {
                Spacer(Modifier.height(OdoTheme.spacing.xl))
                infoCard()
            }
        }
    }
}

/** The tonal glow badge — concentric translucent rings around a solid icon disc. */
@Composable
private fun ResultBadge(icon: ImageVector, tone: Color) {
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(tone.copy(alpha = 0.10f)))
        Box(Modifier.size(92.dp).clip(CircleShape).background(tone.copy(alpha = 0.20f)))
        Box(
            Modifier.size(68.dp).clip(CircleShape).background(tone),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(icon, contentDescription = null, tint = OdoTheme.colors.bg, size = OdoTheme.iconSizes.large)
        }
    }
}

/** A compact icon + title + subtitle card used for the outcome's "what this means" note. */
@Composable
internal fun ResultInfoCard(icon: ImageVector, iconTone: Color, title: String, subtitle: String) {
    OdoCard(color = OdoTheme.colors.surface) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(OdoTheme.shapes.small).background(iconTone.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                OdoIcon(icon, contentDescription = null, tint = iconTone, size = OdoTheme.iconSizes.medium)
            }
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(title, style = OdoTheme.typography.heading)
                OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
            }
        }
    }
}

/** Bottom action stack shared by the result screens (accent primary + optional link). */
@Composable
private fun ResultActions(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(OdoTheme.colors.bg)
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        content = content,
    )
}
