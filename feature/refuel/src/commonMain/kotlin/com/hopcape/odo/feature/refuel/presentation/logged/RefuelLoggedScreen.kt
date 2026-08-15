package com.hopcape.odo.feature.refuel.presentation.logged

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoBadge
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.cost.model.FillEntrySource
import com.hopcape.odo.feature.refuel.presentation.label
import com.hopcape.odo.feature.refuel.resources.Res
import com.hopcape.odo.feature.refuel.resources.rf_insight_better
import com.hopcape.odo.feature.refuel.resources.rf_insight_mileage
import com.hopcape.odo.feature.refuel.resources.rf_km
import com.hopcape.odo.feature.refuel.resources.rf_logged_body
import com.hopcape.odo.feature.refuel.resources.rf_logged_body_no_station
import com.hopcape.odo.feature.refuel.resources.rf_logged_done
import com.hopcape.odo.feature.refuel.resources.rf_logged_fuel_added
import com.hopcape.odo.feature.refuel.resources.rf_logged_odometer
import com.hopcape.odo.feature.refuel.resources.rf_logged_rate_line
import com.hopcape.odo.feature.refuel.resources.rf_logged_source
import com.hopcape.odo.feature.refuel.resources.rf_logged_title
import com.hopcape.odo.feature.refuel.resources.rf_logged_title_one_tap
import com.hopcape.odo.feature.refuel.resources.rf_logged_view_timeline
import org.jetbrains.compose.resources.stringResource

/**
 * The success screen: what was logged, and what the tank returned.
 *
 * The headline changes with the channel. A detected fill really was logged without the owner
 * opening the app, and saying so is the moment the feature proves itself; a typed one says
 * the plain thing, because claiming otherwise would be a lie the owner can see through.
 */
@Composable
internal fun RefuelLoggedScreen(
    state: RefuelLoggedUiState,
    onEvent: (RefuelLoggedEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoScreen(modifier = modifier) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                OdoLoadingIndicator()
            }
            return@OdoScreen
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OdoText(
                text = stringResource(
                    if (state.source == FillEntrySource.DETECTED) {
                        Res.string.rf_logged_title_one_tap
                    } else {
                        Res.string.rf_logged_title
                    },
                ),
                style = OdoTheme.typography.title,
                textAlign = TextAlign.Center,
            )
            OdoText(
                text = state.stationName
                    ?.let { stringResource(Res.string.rf_logged_body, state.quantityLabel, it) }
                    ?: stringResource(Res.string.rf_logged_body_no_station, state.quantityLabel),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
                textAlign = TextAlign.Center,
            )

            OdoCard(modifier = Modifier.fillMaxWidth()) {
                SummaryRow(
                    label = stringResource(Res.string.rf_logged_fuel_added),
                    value = if (state.rateLabel.isEmpty()) {
                        state.quantityLabel
                    } else {
                        stringResource(Res.string.rf_logged_rate_line, state.quantityLabel, state.rateLabel)
                    },
                )
                state.odometerKm?.let { km ->
                    SummaryRow(
                        label = stringResource(Res.string.rf_logged_odometer),
                        // Grouped, like every other odometer in the app. "45026" beside the
                        // header's "45,001 km" reads as two different scales at a glance.
                        value = "${groupDigits(km.toLong())} ${stringResource(Res.string.rf_km)}",
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OdoText(
                        stringResource(Res.string.rf_logged_source),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                    OdoBadge(text = state.source.label())
                }
            }

            // Omitted rather than hedged: the first fill has nothing to measure from, and a
            // figure from two fills a few kilometres apart would swing on how full each was.
            state.mileage?.let { mileage ->
                OdoCard(modifier = Modifier.fillMaxWidth()) {
                    OdoText(mileage.asString(), style = OdoTheme.typography.label)
                    state.mileageComparison?.let { comparison ->
                        OdoText(
                            comparison.asString(),
                            style = OdoTheme.typography.caption,
                            color = OdoTheme.colors.textDim,
                        )
                    }
                }
            }

            OdoButton(
                text = stringResource(Res.string.rf_logged_done),
                onClick = { onEvent(RefuelLoggedEvent.DoneTapped) },
                modifier = Modifier.fillMaxWidth(),
            )
            OdoButton(
                text = stringResource(Res.string.rf_logged_view_timeline),
                onClick = { onEvent(RefuelLoggedEvent.ViewTimelineTapped) },
                variant = OdoButtonVariant.Tertiary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Indian digit grouping — 45026 reads as "45,026". The last three digits, then pairs.
 *
 * `Amount.formatRupees` does the same for money; an odometer is not money, so it cannot
 * borrow it.
 */
private fun groupDigits(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val head = digits.dropLast(3)
    val tail = digits.takeLast(3)
    return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoText(label, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim)
        OdoText(value, style = OdoTheme.typography.label)
    }
}

@OdoThemePreviews
@Composable
private fun RefuelLoggedPreview() = OdoPreview {
    RefuelLoggedScreen(
        state = RefuelLoggedUiState(
            loading = false,
            source = FillEntrySource.DETECTED,
            stationName = "Bharat Petroleum, Karol Bagh",
            quantityLabel = "21.11 L",
            rateLabel = "94.70/L",
            odometerKm = 34_612,
            mileage = UiText(Res.string.rf_insight_mileage, listOf("16.4 km/L")),
            mileageComparison = UiText(Res.string.rf_insight_better),
        ),
        onEvent = {},
    )
}
