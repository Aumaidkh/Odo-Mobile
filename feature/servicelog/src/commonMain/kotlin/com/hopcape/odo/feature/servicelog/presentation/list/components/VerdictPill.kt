package com.hopcape.odo.feature.servicelog.presentation.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.servicelog.presentation.formatRupees
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogFairnessBadge
import com.hopcape.odo.feature.servicelog.presentation.list.sampleFairnessBadges
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_add_bill
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_checking
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_fair
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_over
import org.jetbrains.compose.resources.stringResource

/** The ledger card's fairness verdict — a tinted pill for a verdict, muted text otherwise. */
@Composable
internal fun VerdictPill(badge: ServiceLogFairnessBadge) {
    when (badge) {
        ServiceLogFairnessBadge.FairPrice -> OdoBadge(
            text = stringResource(Res.string.sl_verdict_fair),
            tone = OdoBadgeTone.Success,
            leadingIcon = { OdoIcon(IcCheck, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        is ServiceLogFairnessBadge.Overcharged -> OdoBadge(
            text = stringResource(Res.string.sl_verdict_over, formatRupees(badge.by.paise)),
            tone = OdoBadgeTone.Warning,
            leadingIcon = { OdoIcon(IcWarning, contentDescription = null, size = OdoTheme.iconSizes.small) },
        )
        ServiceLogFairnessBadge.AddBillToVerify ->
            OdoText(stringResource(Res.string.sl_verdict_add_bill), style = OdoTheme.typography.label, color = OdoTheme.colors.textMuted)
        ServiceLogFairnessBadge.NotYetChecked ->
            OdoText(stringResource(Res.string.sl_verdict_checking), style = OdoTheme.typography.label, color = OdoTheme.colors.textDim)
    }
}

@OdoThemePreviews
@Composable
private fun VerdictPillPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        sampleFairnessBadges.forEach { VerdictPill(it) }
    }
}
