package com.hopcape.odo.feature.servicelog.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_badge_self_reported
import com.hopcape.odo.feature.servicelog.resources.sl_badge_verified
import org.jetbrains.compose.resources.stringResource

/** The trust badge — a green "Verified" pill, or a muted "• Self-reported" mark. */
@Composable
internal fun VerificationBadge(status: VerificationStatus) {
    when (status) {
        VerificationStatus.VERIFIED -> OdoBadge(
            text = stringResource(Res.string.sl_badge_verified),
            tone = OdoBadgeTone.Success,
            leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        VerificationStatus.SELF_REPORTED ->
            DotLabel(stringResource(Res.string.sl_badge_self_reported), OdoTheme.colors.textMuted, OdoTheme.typography.caption)
    }
}

/** An icon + tinted label — a status/verdict line ("✓ Verified · Fair"). */
@Composable
internal fun IconLabel(icon: ImageVector, text: String, tint: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        OdoIcon(icon, contentDescription = null, tint = tint, size = OdoTheme.iconSizes.small)
        OdoText(text = text, style = OdoTheme.typography.label, color = tint)
    }
}

/** A small dot + tinted label — the muted "• …" marks (self-reported / add-bill). */
@Composable
internal fun DotLabel(text: String, tint: Color, style: TextStyle) {
    Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        OdoText(text = text, style = style, color = tint)
    }
}

@OdoThemePreviews
@Composable
private fun ServiceStatusLabelsPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        VerificationBadge(VerificationStatus.VERIFIED)
        VerificationBadge(VerificationStatus.SELF_REPORTED)
        IconLabel(IcCheck, "Verified · Fair", OdoTheme.colors.success)
        IconLabel(IcWarning, "Verified · Rs. 1,100 over", OdoTheme.colors.warning)
        DotLabel("Add bill to verify", OdoTheme.colors.textMuted, OdoTheme.typography.label)
    }
}
