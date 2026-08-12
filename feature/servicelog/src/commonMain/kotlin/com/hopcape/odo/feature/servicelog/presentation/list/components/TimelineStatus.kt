package com.hopcape.odo.feature.servicelog.presentation.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.shared.formatRupees
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogFairnessBadge
import com.hopcape.odo.feature.servicelog.presentation.list.sampleFairnessBadges
import com.hopcape.odo.feature.servicelog.presentation.ui.components.DotLabel
import com.hopcape.odo.feature.servicelog.presentation.ui.components.IconLabel
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_badge_verified
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_add_bill
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_fair
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_low_confidence
import com.hopcape.odo.feature.servicelog.resources.sl_verdict_over
import org.jetbrains.compose.resources.stringResource

/** The timeline card's status line — verification combined with the fairness verdict. */
@Composable
internal fun TimelineStatus(badge: ServiceLogFairnessBadge) {
    val verified = stringResource(Res.string.sl_badge_verified)
    when (badge) {
        ServiceLogFairnessBadge.FairPrice ->
            IconLabel(IcCheck, "$verified · ${stringResource(Res.string.sl_verdict_fair)}", OdoTheme.colors.success)
        is ServiceLogFairnessBadge.Overcharged ->
            IconLabel(IcWarning, "$verified · ${stringResource(Res.string.sl_verdict_over, badge.by.formatRupees())}", OdoTheme.colors.warning)
        ServiceLogFairnessBadge.NotYetChecked ->
            IconLabel(IcClock, verified, OdoTheme.colors.success)
        // Verified, but the city pool is too thin to say anything about the price — so the
        // line says only what is proven (PRD: never false precision).
        is ServiceLogFairnessBadge.NotEnoughData ->
            IconLabel(IcCheck, "$verified · ${stringResource(Res.string.sl_verdict_low_confidence)}", OdoTheme.colors.success)
        ServiceLogFairnessBadge.AddBillToVerify ->
            DotLabel(stringResource(Res.string.sl_verdict_add_bill), OdoTheme.colors.textMuted, OdoTheme.typography.label)
    }
}

@OdoThemePreviews
@Composable
private fun TimelineStatusPreview() = OdoPreview {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        sampleFairnessBadges.forEach { TimelineStatus(it) }
    }
}
