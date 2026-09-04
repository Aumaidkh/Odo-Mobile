package com.hopcape.odo.feature.advisory.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoBadgeTone
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoEmptyState
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.modifier.accentGlow
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.feature.advisory.resources.Res
import com.hopcape.odo.feature.advisory.resources.adv_value_basis
import com.hopcape.odo.feature.advisory.resources.adv_value_cd_back
import com.hopcape.odo.feature.advisory.resources.adv_value_empty_body
import com.hopcape.odo.feature.advisory.resources.adv_value_empty_title
import com.hopcape.odo.feature.advisory.resources.adv_value_full_record_label
import com.hopcape.odo.feature.advisory.resources.adv_value_pitch_complete
import com.hopcape.odo.feature.advisory.resources.adv_value_pitch_empty
import com.hopcape.odo.feature.advisory.resources.adv_value_pitch_partial
import com.hopcape.odo.feature.advisory.resources.adv_value_scan
import com.hopcape.odo.feature.advisory.resources.adv_value_scan_next
import com.hopcape.odo.feature.advisory.resources.adv_value_separator
import com.hopcape.odo.feature.advisory.resources.adv_value_share
import com.hopcape.odo.feature.advisory.resources.adv_value_share_text
import com.hopcape.odo.feature.advisory.resources.adv_value_title
import com.hopcape.odo.feature.advisory.resources.adv_value_today_complete
import com.hopcape.odo.feature.advisory.resources.adv_value_today_no_record
import com.hopcape.odo.feature.advisory.resources.adv_value_today_with_record
import com.hopcape.odo.feature.advisory.resources.adv_value_worth_label
import org.jetbrains.compose.resources.stringResource

/**
 * "My car's value" — what the car is worth, and the rupee gap a proven record would close.
 *
 * The gap is the screen. One figure alone is a fact the owner can do nothing about; two
 * figures and the distance between them is an argument, and the button under it is the way
 * to act on the argument. That is why "scan a bill" is the primary action on a screen that
 * looks like a valuation.
 *
 * Every number here is modelled from segment averages, and the badge says so out loud. The
 * PRD forbids implying a precision the estimate does not have.
 *
 * Stateless: renders [state] and forwards [CarValueEvent]s.
 */
@Composable
internal fun CarValueScreen(
    state: CarValueUiState,
    onEvent: (CarValueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val distance = LocalOdoDistanceFormat.current
    val separator = stringResource(Res.string.adv_value_separator)
    val display = state.valued?.let { valued ->
        valued.toDisplay(
            odometer = distance.format(valued.car.odometer.km),
            separator = separator,
        )
    }

    OdoScreen(
        modifier = modifier,
        title = stringResource(Res.string.adv_value_title),
        onBack = { onEvent(CarValueEvent.BackClicked) },
        backContentDescription = stringResource(Res.string.adv_value_cd_back),
        bottomBar = { if (display != null) Actions(display, onEvent) },
    ) { padding ->
        when {
            state.isLoading -> Centred(padding) { OdoLoadingIndicator() }

            display == null -> Centred(padding) {
                OdoEmptyState(
                    title = stringResource(Res.string.adv_value_empty_title),
                    message = stringResource(Res.string.adv_value_empty_body),
                )
            }

            else -> Estimate(display, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun Estimate(display: CarValueDisplay, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        OdoText(
            text = display.carSummary,
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )

        // The dimmer of the two figures on purpose: it is the number the owner has today,
        // and the card below is the one they can still change.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoText(
                text = display.today,
                style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = OdoTheme.colors.textMuted,
            )
            OdoText(
                text = stringResource(
                    when {
                        display.hasNoRecord -> Res.string.adv_value_today_no_record
                        display.isRecordComplete -> Res.string.adv_value_today_complete
                        else -> Res.string.adv_value_today_with_record
                    },
                ),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
                modifier = Modifier.padding(bottom = OdoTheme.spacing.sm),
            )
        }

        OdoCard(
            color = OdoTheme.colors.surfaceRaised,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        ) {
            OdoText(
                text = stringResource(Res.string.adv_value_full_record_label),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textDim,
            )
            OdoText(
                text = display.withFullRecord,
                style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = OdoTheme.colors.text,
            )
            // Dropped once the record is complete: the gap is zero, and a "+Rs. 0" row
            // reads as a bug rather than as an achievement.
            if (!display.isRecordComplete) {
                OdoDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
                ) {
                    OdoText(
                        text = stringResource(Res.string.adv_value_worth_label),
                        style = OdoTheme.typography.body,
                        color = OdoTheme.colors.textDim,
                        modifier = Modifier.weight(1f),
                    )
                    OdoText(
                        text = display.recordWorth,
                        style = OdoTheme.typography.heading.copy(fontSize = 22.sp),
                        color = OdoTheme.colors.text,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }

        OdoText(
            text = stringResource(
                when {
                    display.hasNoRecord -> Res.string.adv_value_pitch_empty
                    display.isRecordComplete -> Res.string.adv_value_pitch_complete
                    else -> Res.string.adv_value_pitch_partial
                },
            ),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.text,
        )

        // The honesty label. It is not decoration: an estimate built from segment averages
        // shown without it reads as a valuation of this car.
        OdoBadge(text = stringResource(Res.string.adv_value_basis), tone = OdoBadgeTone.Neutral)
    }
}

@Composable
private fun Actions(display: CarValueDisplay, onEvent: (CarValueEvent) -> Unit) {
    val shareText = stringResource(
        Res.string.adv_value_share_text,
        display.carSummary,
        display.today,
        display.withFullRecord,
        display.recordWorth,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
    ) {
        OdoButton(
            text = stringResource(
                if (display.hasNoRecord) Res.string.adv_value_scan else Res.string.adv_value_scan_next,
            ),
            onClick = { onEvent(CarValueEvent.ScanClicked) },
            modifier = Modifier.weight(1f).accentGlow(),
        )
        OdoButton(
            text = stringResource(Res.string.adv_value_share),
            onClick = { onEvent(CarValueEvent.ShareClicked(shareText)) },
            modifier = Modifier.weight(SHARE_WEIGHT),
            variant = OdoButtonVariant.Secondary,
        )
    }
}

/** Share takes the narrower share of the row; the scan is what the screen is arguing for. */
private const val SHARE_WEIGHT = 0.6f

/** The full content area with one thing in the middle of it — loading, or nothing to show. */
@Composable
private fun Centred(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OdoThemePreviews
@Composable
private fun CarValueNoRecordPreview() = OdoPreview(padded = false) {
    PreviewScreen(
        CarValueDisplay(
            carSummary = "2022 Baleno Zeta · 38,400 km · Srinagar",
            today = "Rs. 6.1L",
            withFullRecord = "Rs. 6.4L–6.9L",
            recordWorth = "+Rs. 35,000",
            hasNoRecord = true,
            isRecordComplete = false,
        ),
    )
}

@OdoThemePreviews
@Composable
private fun CarValueWithRecordPreview() = OdoPreview(padded = false) {
    PreviewScreen(
        CarValueDisplay(
            carSummary = "2019 Creta SX · 71,200 km · Pune",
            today = "Rs. 9.8L",
            withFullRecord = "Rs. 9.9L–10.3L",
            recordWorth = "+Rs. 27,000",
            hasNoRecord = false,
            isRecordComplete = false,
        ),
    )
}

/** Renders the body directly, since the previewable states are display-level. */
@Composable
private fun PreviewScreen(display: CarValueDisplay) {
    OdoScreen(
        title = stringResource(Res.string.adv_value_title),
        onBack = {},
        bottomBar = { Actions(display) {} },
    ) { padding ->
        Estimate(display, modifier = Modifier.padding(padding))
    }
}
