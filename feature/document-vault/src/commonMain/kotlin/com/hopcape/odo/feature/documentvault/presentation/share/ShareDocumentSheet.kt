package com.hopcape.odo.feature.documentvault.presentation.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcChatOutlined
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcDotsVertical
import com.hopcape.odo.core.designsystem.icons.IcEnvelope
import com.hopcape.odo.core.designsystem.icons.IcLink
import com.hopcape.odo.core.designsystem.icons.IcShare
import com.hopcape.odo.core.designsystem.icons.IcShieldFilled
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.documentvault.resources.Res
import com.hopcape.odo.feature.documentvault.resources.dv_share_copy
import com.hopcape.odo.feature.documentvault.resources.dv_share_download
import com.hopcape.odo.feature.documentvault.resources.dv_share_email
import com.hopcape.odo.feature.documentvault.resources.dv_share_hide_policy
import com.hopcape.odo.feature.documentvault.resources.dv_share_more
import com.hopcape.odo.feature.documentvault.resources.dv_share_safer
import com.hopcape.odo.feature.documentvault.resources.dv_share_subtitle
import com.hopcape.odo.feature.documentvault.resources.dv_share_title
import com.hopcape.odo.feature.documentvault.resources.dv_share_whatsapp
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** Where a document can be shared to. */
internal enum class ShareTarget { WHATSAPP, EMAIL, COPY, MORE }

/** The document being shared + the privacy toggle (hide the policy number before sharing). */
internal data class ShareDocumentUiState(
    val docName: String,
    val provider: String,
    val validTill: LocalDate,
    val hidePolicyNumber: Boolean,
)

/**
 * The "share document" sheet **body** — the document summary, the hide-policy-number
 * privacy toggle, and the share targets. Shown as a bottom-sheet destination
 * ([OdoDestination.Documents.Share]); the [androidx.compose.material3.ModalBottomSheet]
 * chrome comes from the navigation layer. Holds the transient toggle state itself.
 */
@Composable
internal fun ShareDocumentSheetContent() {
    var hidePolicy by remember { mutableStateOf(true) }
    val state = ShareDocumentUiState(
        docName = "Insurance",
        provider = "SafeDrive",
        validTill = LocalDate(2026, 7, 3),
        hidePolicyNumber = hidePolicy,
    )
    val onShareVia: (ShareTarget) -> Unit = { /* TODO(M2): open the share target with the (optionally redacted) doc. */ }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.md)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        Header(state)
        HidePolicyRow(hidden = state.hidePolicyNumber, onToggle = { hidePolicy = !hidePolicy })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ShareTargetButton(stringResource(Res.string.dv_share_whatsapp), OdoTheme.colors.success, OdoTheme.colors.onAccent, IcChatOutlined
            ) { onShareVia(ShareTarget.WHATSAPP) }
            ShareTargetButton(stringResource(Res.string.dv_share_email), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcEnvelope) { onShareVia(ShareTarget.EMAIL) }
            ShareTargetButton(stringResource(Res.string.dv_share_copy), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcLink) { onShareVia(ShareTarget.COPY) }
            ShareTargetButton(stringResource(Res.string.dv_share_more), OdoTheme.colors.surfaceRaised, OdoTheme.colors.text, IcDotsVertical, iconRotation = 90f) { onShareVia(ShareTarget.MORE) }
        }
        OdoButton(
            text = stringResource(Res.string.dv_share_download),
            onClick = { /* TODO(M2): render + save the PDF. */ },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { OdoIcon(IcShare, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
    }
}

@Composable
private fun Header(state: ShareDocumentUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(OdoTheme.shapes.small).background(OdoTheme.colors.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(IcShieldFilled, contentDescription = null, tint = OdoTheme.colors.accent, size = OdoTheme.iconSizes.medium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(stringResource(Res.string.dv_share_title, state.docName.lowercase()), style = OdoTheme.typography.heading)
            OdoText(
                stringResource(Res.string.dv_share_subtitle, state.provider, formatDate(state.validTill)),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

@Composable
private fun HidePolicyRow(hidden: Boolean, onToggle: () -> Unit) {
    OdoCard(color = OdoTheme.colors.surfaceRaised, onClick = onToggle) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            CheckBox(checked = hidden)
            OdoText(stringResource(Res.string.dv_share_hide_policy), style = OdoTheme.typography.heading, modifier = Modifier.weight(1f))
            OdoText(stringResource(Res.string.dv_share_safer), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        }
    }
}

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .clip(OdoTheme.shapes.small)
            .then(if (checked) Modifier.background(OdoTheme.colors.accent) else Modifier.border(1.5.dp, OdoTheme.colors.border, OdoTheme.shapes.small)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) OdoIcon(IcCheck, contentDescription = null, tint = OdoTheme.colors.onAccent, size = OdoTheme.iconSizes.small)
    }
}

@Composable
private fun ShareTargetButton(
    label: String,
    background: Color,
    iconTint: Color,
    icon: ImageVector,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        Box(
            Modifier.size(56.dp).clip(OdoTheme.shapes.card).background(background).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            OdoIcon(icon, contentDescription = null, tint = iconTint, size = OdoTheme.iconSizes.medium, modifier = Modifier.rotate(iconRotation))
        }
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textDim)
    }
}
