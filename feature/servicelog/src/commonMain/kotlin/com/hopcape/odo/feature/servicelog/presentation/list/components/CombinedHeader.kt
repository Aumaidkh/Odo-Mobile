package com.hopcape.odo.feature.servicelog.presentation.list.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoHealthDial
import com.hopcape.odo.core.designsystem.component.OdoProgressBar
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.servicelog.model.RecordStrength
import com.hopcape.odo.feature.servicelog.presentation.formatRupees
import com.hopcape.odo.feature.servicelog.presentation.formatRupeesCompact
import com.hopcape.odo.feature.servicelog.presentation.list.ServiceLogListUiState
import com.hopcape.odo.feature.servicelog.presentation.list.sampleLoadedContent
import com.hopcape.odo.feature.servicelog.resources.Res
import com.hopcape.odo.feature.servicelog.resources.sl_record_line
import com.hopcape.odo.feature.servicelog.resources.sl_record_resale_hint
import com.hopcape.odo.feature.servicelog.resources.sl_record_verified_ratio
import com.hopcape.odo.feature.servicelog.resources.sl_stat_saved
import com.hopcape.odo.feature.servicelog.resources.sl_stat_total_spent
import com.hopcape.odo.feature.servicelog.resources.sl_strength_empty
import com.hopcape.odo.feature.servicelog.resources.sl_strength_fair
import com.hopcape.odo.feature.servicelog.resources.sl_strength_strong
import com.hopcape.odo.feature.servicelog.resources.sl_strength_weak
import org.jetbrains.compose.resources.stringResource

/**
 * The header both directions share: a spend/savings row over the record ring, score
 * band, verified progress and resale uplift. One card, drawn identically for Ledger
 * and Timeline — only the list beneath the toggle changes.
 */
@Composable
internal fun CombinedHeader(content: ServiceLogListUiState.Content.Loaded) {
    val s = content.summary
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.sl_stat_total_spent),
                value = formatRupees(s.totalSpent.paise),
            )
            StatColumn(
                modifier = Modifier.weight(1f),
                label = stringResource(Res.string.sl_stat_saved),
                value = formatRupees(content.savings.overchargeTotal.paise),
                valueColor = OdoTheme.colors.success,
            )
        }
        OdoDivider()
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoHealthDial(score = s.score.value, dialSize = 52.dp, strokeWidth = 5.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoText(
                        text = stringResource(Res.string.sl_record_line, strengthLabel(s.strength)),
                        style = OdoTheme.typography.heading,
                    )
                    OdoText(
                        text = stringResource(Res.string.sl_record_verified_ratio, s.verifiedCount, s.serviceCount),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.success,
                    )
                }
                OdoProgressBar(progress = s.verifiedRatio, color = OdoTheme.colors.success)
                s.resaleUplift?.let { uplift ->
                    OdoText(
                        text = stringResource(Res.string.sl_record_resale_hint, formatRupeesCompact(uplift.high.paise)),
                        style = OdoTheme.typography.caption,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = OdoTheme.colors.text,
) {
    Column(modifier) {
        OdoText(text = label.uppercase(), style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        OdoText(text = value, style = OdoTheme.typography.title, color = valueColor)
    }
}

@Composable
private fun strengthLabel(strength: RecordStrength): String = stringResource(
    when (strength) {
        RecordStrength.STRONG -> Res.string.sl_strength_strong
        RecordStrength.FAIR -> Res.string.sl_strength_fair
        RecordStrength.WEAK -> Res.string.sl_strength_weak
        RecordStrength.EMPTY -> Res.string.sl_strength_empty
    },
)

@OdoThemePreviews
@Composable
private fun CombinedHeaderPreview() = OdoPreview {
    CombinedHeader(sampleLoadedContent())
}
