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
import androidx.compose.ui.platform.testTag
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
import com.hopcape.odo.feature.advisory.resources.adv_value_empty_action
import com.hopcape.odo.feature.advisory.resources.adv_value_empty_body
import com.hopcape.odo.feature.advisory.resources.adv_value_empty_title
import com.hopcape.odo.feature.advisory.resources.adv_value_odometer_action
import com.hopcape.odo.feature.advisory.resources.adv_value_odometer_body
import com.hopcape.odo.feature.advisory.resources.adv_value_odometer_title
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoDivider
import com.hopcape.odo.core.designsystem.component.OdoIconButton
import com.hopcape.odo.core.designsystem.component.OdoSegmentedProgress
import com.hopcape.odo.core.designsystem.icons.IcArrowLeft
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_band_note
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_basis
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_bills_many
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_bills_none
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_bills_one
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_cd_progress
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_fuel_cng
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_fuel_diesel
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_fuel_electric
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_fuel_petrol
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_record_body
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_record_label
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_scan
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_hatchback
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_muv
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_sedan
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_suv
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_skip
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_title
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_today_label
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_today_label_partial
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
            fuelLabel = stringResource(valued.car.fuelType.label()),
            segmentLabel = stringResource(SegmentCatalog.segmentOf(valued.car.model).label()),
        )
    }

    // First run owns its own chrome: the flow is still running, so the progress carries on
    // and the two ways out are the scan and "not now" rather than a title bar and share.
    if (state.firstRun && display != null) {
        FirstRunEstimate(display, onEvent, modifier)
        return
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

            // Before the no-car case: the car is there, and only the reading is missing.
            // Kilometres are the second biggest term in the estimate after age, so there is
            // nothing honest to show — but "no car yet" would be the wrong thing to say.
            state.odometerPending -> Centred(padding) {
                OdoEmptyState(
                    title = stringResource(Res.string.adv_value_odometer_title),
                    message = stringResource(Res.string.adv_value_odometer_body),
                    action = {
                        OdoButton(
                            text = stringResource(Res.string.adv_value_odometer_action),
                            onClick = { onEvent(CarValueEvent.AddOdometerClicked) },
                        )
                    },
                )
            }

            display == null -> Centred(padding) {
                OdoEmptyState(
                    title = stringResource(Res.string.adv_value_empty_title),
                    message = stringResource(Res.string.adv_value_empty_body),
                    // The copy asks for a car, so the screen has to offer the way to add one.
                    // Without this it named the one thing to do and left no way to do it.
                    action = {
                        OdoButton(
                            text = stringResource(Res.string.adv_value_empty_action),
                            onClick = { onEvent(CarValueEvent.AddCarClicked) },
                            modifier = Modifier.testTag(CarValueTestTags.ADD_CAR),
                        )
                    },
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

        // The only value figure on the screen, so it carries full weight. It used to be the
        // dimmer of two, until the second one turned out to contradict it: the model's own
        // uncertainty is wider than the record premium, so a band for each said the owner
        // might already be worth more than a full record would fetch.
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoText(
                text = display.today,
                style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = OdoTheme.colors.text,
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

        // What the record adds, as one figure on top of the band above — not a second band
        // beside it. Two bands contradicted each other: the premium is 4-9% and the model's
        // uncertainty is wider than that, so the aspirational band sat inside today's.
        //
        // Dropped once the record is complete: the gap is zero, and a "+Rs. 0" row reads as a
        // bug rather than as an achievement.
        if (!display.isRecordComplete) OdoCard(
            color = OdoTheme.colors.surfaceRaised,
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            OdoText(
                text = stringResource(Res.string.adv_value_full_record_label),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textDim,
            )
            OdoText(
                text = display.recordWorth,
                style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                color = OdoTheme.colors.text,
            )
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
            shortName = "Swift",
            basis = "2019 Swift · Petrol · hatchback segment",
            provenServices = 0,
            carSummary = "2022 Baleno Zeta · 38,400 km · Srinagar",
            today = "Rs. 5.8L–6.4L",
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
            shortName = "Swift",
            basis = "2019 Swift · Petrol · hatchback segment",
            provenServices = 0,
            carSummary = "2019 Creta SX · 71,200 km · Pune",
            today = "Rs. 9.4L–10.2L",
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

/* ------------------------------ First run · step 3 of 4 ------------------------------ */

/**
 * The value screen as first run shows it.
 *
 * Setup has just taken four answers and given nothing back. This screen is the payment: a
 * figure the owner does not know, and under it the one number they can change. The gap is
 * the argument, and "Scan your first bill" is the way to act on it.
 *
 * The band, the note under it and the badge all say the same thing in three registers,
 * deliberately — an estimate read off a segment average must not be mistaken for a
 * valuation of this car.
 */
@Composable
private fun FirstRunEstimate(
    display: CarValueDisplay,
    onEvent: (CarValueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge),
    ) {
        OdoIconButton(
            imageVector = IcArrowLeft,
            contentDescription = stringResource(Res.string.adv_value_cd_back),
            onClick = { onEvent(CarValueEvent.BackClicked) },
        )
        OdoSegmentedProgress(
            current = FIRST_RUN_STEP,
            total = FIRST_RUN_TOTAL,
            contentDescription = stringResource(
                Res.string.adv_value_fr_cd_progress,
                FIRST_RUN_STEP,
                FIRST_RUN_TOTAL,
            ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            Spacer(Modifier.height(OdoTheme.spacing.xl))

            OdoText(
                text = stringResource(Res.string.adv_value_fr_title, display.shortName),
                style = OdoTheme.typography.display,
                color = OdoTheme.colors.text,
            )

            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    text = stringResource(
                        if (display.hasNoRecord) Res.string.adv_value_fr_today_label
                        else Res.string.adv_value_fr_today_label_partial,
                    ),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
                OdoText(
                    text = display.today,
                    style = OdoTheme.typography.display.copy(fontSize = 44.sp, lineHeight = 48.sp),
                    color = OdoTheme.colors.text,
                )
                OdoText(
                    text = display.basis,
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.textDim,
                )
            }

            OdoDivider()

            // Dropped once the record is complete: the gap is zero, and "+Rs. 0" reads as a
            // bug rather than as an achievement.
            if (!display.isRecordComplete) OdoCard(
                color = OdoTheme.colors.surfaceRaised,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                OdoText(
                    text = stringResource(Res.string.adv_value_fr_record_label),
                    style = OdoTheme.typography.caption,
                    color = OdoTheme.colors.textDim,
                )
                OdoText(
                    text = display.recordWorth,
                    style = OdoTheme.typography.display.copy(fontSize = 36.sp, lineHeight = 40.sp),
                    color = OdoTheme.colors.text,
                )
                OdoDivider()
                OdoText(
                    text = stringResource(
                        Res.string.adv_value_fr_record_body,
                        stringResource(display.billsLogged(), display.provenServices),
                    ),
                    style = OdoTheme.typography.body,
                    color = OdoTheme.colors.text,
                )
            }

            OdoText(
                text = stringResource(Res.string.adv_value_fr_band_note),
                style = OdoTheme.typography.body,
                color = OdoTheme.colors.textDim,
            )

            OdoBadge(text = stringResource(Res.string.adv_value_fr_basis), tone = OdoBadgeTone.Neutral)

            Spacer(Modifier.height(OdoTheme.spacing.lg))
        }

        OdoButton(
            text = stringResource(Res.string.adv_value_fr_scan),
            onClick = { onEvent(CarValueEvent.ScanClicked) },
            modifier = Modifier.fillMaxWidth().accentGlow(),
        )
        OdoButton(
            text = stringResource(Res.string.adv_value_fr_skip),
            onClick = { onEvent(CarValueEvent.SkipClicked) },
            modifier = Modifier.fillMaxWidth().padding(vertical = OdoTheme.spacing.sm),
            variant = OdoButtonVariant.Tertiary,
        )
    }
}

/** "0 bills logged" reads better than "no bills", and the plural has to agree. */
private fun CarValueDisplay.billsLogged() = when (provenServices) {
    0 -> Res.string.adv_value_fr_bills_none
    1 -> Res.string.adv_value_fr_bills_one
    else -> Res.string.adv_value_fr_bills_many
}

private fun FuelType.label() = when (this) {
    FuelType.PETROL -> Res.string.adv_value_fr_fuel_petrol
    FuelType.DIESEL -> Res.string.adv_value_fr_fuel_diesel
    FuelType.CNG -> Res.string.adv_value_fr_fuel_cng
    FuelType.ELECTRIC -> Res.string.adv_value_fr_fuel_electric
}

private fun VehicleSegment.label() = when (this) {
    VehicleSegment.HATCHBACK -> Res.string.adv_value_fr_segment_hatchback
    VehicleSegment.SEDAN -> Res.string.adv_value_fr_segment_sedan
    VehicleSegment.SUV -> Res.string.adv_value_fr_segment_suv
    VehicleSegment.MUV -> Res.string.adv_value_fr_segment_muv
}

/** First run is Welcome, the car step, this screen, then the scan. */
private const val FIRST_RUN_STEP = 3
private const val FIRST_RUN_TOTAL = 4
