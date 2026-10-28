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
import com.hopcape.odo.feature.advisory.resources.adv_value_assumed_reading
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
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.shared.VehicleSegment
import com.hopcape.odo.core.domain.car.catalog.SegmentCatalog
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_band_note
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
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_odo_cta
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_odo_body
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_odo_title
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_hatchback
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_muv
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_sedan
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_segment_suv
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_skip
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_title
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_today_label
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_today_label_partial
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import com.hopcape.odo.core.designsystem.component.OdoEntrance
import com.hopcape.odo.core.designsystem.component.OdoExpandableRow
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoMakeLogo
import com.hopcape.odo.core.designsystem.component.OdoGaugeRange
import com.hopcape.odo.core.designsystem.component.OdoStepHeader
import com.hopcape.odo.core.designsystem.component.OdoTickGauge
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_why
import com.hopcape.odo.feature.advisory.resources.adv_value_fr_basis
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
        FirstRunEstimate(display, state, onEvent, modifier)
        return
    }
    // Only the first-run chrome while the estimate loads, so the garage title never flashes.
    if (state.firstRun && state.isLoading) {
        Column(modifier.fillMaxSize().background(OdoTheme.colors.bg).statusBarsPadding()) {
            OdoStepHeader(
                current = FIRST_RUN_STEP,
                total = state.firstRunSteps,
                onBack = { onEvent(CarValueEvent.BackClicked) },
                backContentDescription = stringResource(Res.string.adv_value_cd_back),
                progressContentDescription = stringResource(
                    Res.string.adv_value_fr_cd_progress,
                    FIRST_RUN_STEP,
                    state.firstRunSteps,
                ),
            )
        }
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

            else -> Estimate(display, state.odometerAssumed, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun Estimate(
    display: CarValueDisplay,
    odometerAssumed: Boolean,
    modifier: Modifier = Modifier,
) {
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
        if (odometerAssumed) OdoText(
            text = stringResource(Res.string.adv_value_assumed_reading),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.textDim,
        )

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
            year = 2019,
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
            year = 2019,
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

/** First run in v2: three steps, the new title, and the auto-odometer card. */
@OdoThemePreviews
@Composable
private fun CarValueFirstRunPreview() = OdoPreview(padded = false) {
    FirstRunEstimate(
        display = CarValueDisplay(
            shortName = "Creta",
            year = 2022,
            basis = "2022 Creta · Petrol · SUV segment",
            provenServices = 0,
            carSummary = "2022 Creta",
            today = "Rs. 11.2L–12.6L",
            recordWorth = "+Rs. 62,000",
            hasNoRecord = true,
            isRecordComplete = false,
        ),
        state = CarValueUiState(firstRun = true, offerAutoOdometer = true, firstRunSteps = 3),
        onEvent = {},
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
        Estimate(display, odometerAssumed = false, modifier = Modifier.padding(padding))
    }
}

/* ------------------------------ First run · step 3 ------------------------------ */

/**
 * The value screen as first run shows it: a dial with today's band and the record premium,
 * then the figures. Only the scan and Skip are pinned; everything else scrolls.
 */
@Composable
private fun FirstRunEstimate(
    display: CarValueDisplay,
    state: CarValueUiState,
    onEvent: (CarValueEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OdoTheme.colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        OdoStepHeader(
            current = FIRST_RUN_STEP,
            total = state.firstRunSteps,
            onBack = { onEvent(CarValueEvent.BackClicked) },
            backContentDescription = stringResource(Res.string.adv_value_cd_back),
            progressContentDescription = stringResource(
                Res.string.adv_value_fr_cd_progress,
                FIRST_RUN_STEP,
                state.firstRunSteps,
            ),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fadingEdges()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OdoTheme.spacing.screenEdge)
                .padding(top = OdoTheme.spacing.md, bottom = OdoTheme.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
        ) {
            OdoEntrance(0) {
                Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                    if (display.make.isNotBlank()) BrandRow(make = display.make, logoUrl = state.makeLogoUrl)
                    OdoText(
                        text = stringResource(Res.string.adv_value_fr_title, display.year, display.shortName),
                        style = OdoTheme.typography.title.copy(fontSize = 28.sp, lineHeight = 34.sp),
                        color = OdoTheme.colors.text,
                    )
                }
            }
            OdoEntrance(1) { ValueDial(display) }
            // The rest arrives once the needle has settled, so the eye follows it down.
            if (!display.isRecordComplete) OdoEntrance(AFTER_NEEDLE) { RecordCard(display) }
            OdoEntrance(AFTER_NEEDLE + 1) {
                WhyARange(
                    odometerAssumed = state.odometerAssumed,
                    onToggled = { onEvent(CarValueEvent.WhyRangeToggled(it)) },
                )
            }
            // Absent, not hidden, when the feature is off, so the layout has no gap.
            if (state.offerAutoOdometer) OdoEntrance(AFTER_NEEDLE + 2) {
                AutoOdometerCard(
                    onTurnOn = { onEvent(CarValueEvent.TurnOnAutoOdometerClicked) },
                    onSeen = { onEvent(CarValueEvent.AutoOdometerCardSeen) },
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = OdoTheme.spacing.screenEdge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
}

/** The dial sweeps to today's band, lights it, then lights the premium in green. */
@Composable
private fun ValueDial(display: CarValueDisplay) {
    val gauge = display.gauge
    val needle = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current
    val easing = OdoTheme.motion.easeFlow
    LaunchedEffect(gauge) {
        needle.animateTo((gauge.bandStart + gauge.bandEnd) / 2, tween(NEEDLE_MILLIS, easing = easing))
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        reveal.animateTo(1f, tween(REVEAL_MILLIS))
    }
    val idle = OdoTheme.colors.border
    val band = lerp(idle, OdoTheme.colors.text, reveal.value)
    val premium = lerp(idle, OdoTheme.colors.success, reveal.value)
    // The figures start inside the dial's open bottom, where no ticks are drawn.
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        OdoTickGauge(
            size = DIAL_SIZE,
            tickColor = idle,
            ranges = buildList {
                add(OdoGaugeRange(gauge.bandStart, gauge.bandEnd, band))
                if (!display.isRecordComplete) add(OdoGaugeRange(gauge.bandEnd, gauge.premiumEnd, premium))
            },
            needle = needle.value,
        )
        OdoEntrance(FIGURES, modifier = Modifier.padding(top = DIAL_SIZE * DIAL_TEXT_TOP)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
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
                    style = OdoTheme.typography.display.copy(fontSize = 40.sp, lineHeight = 44.sp),
                    color = OdoTheme.colors.text,
                )
                OdoText(
                    text = display.basis,
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                    textAlign = TextAlign.Center,
                )
                // The honesty label stays in view: without it the band reads as a valuation of this car.
                OdoText(
                    text = stringResource(Res.string.adv_value_fr_basis),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The car's logo above the headline; the headline already names the model. */
@Composable
private fun BrandRow(make: String, logoUrl: String?) {
    OdoMakeLogo(
        make = OdoCarMake(id = make, name = make, logoUrl = logoUrl),
        width = BRAND_LOGO_WIDTH,
        height = BRAND_LOGO_HEIGHT,
    )
}

/** What a proven record adds, in the dial's green, with the count of bills so far. */
@Composable
private fun RecordCard(display: CarValueDisplay) {
    val success = OdoTheme.colors.success
    OdoCard(
        color = success.copy(alpha = 0.10f),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        OdoText(
            text = stringResource(Res.string.adv_value_fr_record_label),
            style = OdoTheme.typography.caption,
            color = OdoTheme.colors.textDim,
        )
        OdoText(
            text = display.recordWorth,
            style = OdoTheme.typography.display.copy(fontSize = 32.sp, lineHeight = 36.sp),
            color = success,
        )
        OdoText(
            text = stringResource(
                Res.string.adv_value_fr_record_body,
                stringResource(display.billsLogged(), display.provenServices),
            ),
            style = OdoTheme.typography.body,
            color = OdoTheme.colors.text,
        )
    }
}

/** The honesty note, folded away: the estimate is a segment average, not this car. */
@Composable
private fun WhyARange(odometerAssumed: Boolean, onToggled: (Boolean) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    OdoExpandableRow(
        title = stringResource(Res.string.adv_value_fr_why),
        expanded = open,
        onToggle = {
            open = !open
            onToggled(open)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoText(
                text = stringResource(Res.string.adv_value_fr_band_note),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
            if (odometerAssumed) OdoText(
                text = stringResource(Res.string.adv_value_assumed_reading),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/** Fades the scrolling body out at its top and bottom edges instead of cutting it. */
@Composable
private fun Modifier.fadingEdges(): Modifier {
    val edge = OdoTheme.spacing.lg
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val px = edge.toPx()
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), endY = px), blendMode = BlendMode.DstIn)
            drawRect(
                Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = size.height - px, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
        }
}

/** The first-run offer to log drives automatically. */
@Composable
private fun AutoOdometerCard(onTurnOn: () -> Unit, onSeen: () -> Unit) {
    OdoCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(CarValueTestTags.AUTO_ODO_CARD)
            // Seen once at least half of it is inside the scrolling viewport.
            .onGloballyPositioned { coords ->
                if (coords.boundsInWindow().height >= coords.size.height / 2f) onSeen()
            },
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    text = stringResource(Res.string.adv_value_fr_odo_title),
                    style = OdoTheme.typography.heading,
                    color = OdoTheme.colors.text,
                )
                OdoText(
                    text = stringResource(Res.string.adv_value_fr_odo_body),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoButton(
                text = stringResource(Res.string.adv_value_fr_odo_cta),
                onClick = onTurnOn,
                variant = OdoButtonVariant.Secondary,
            )
        }
    }
}

/** The bill count under the premium; none gets a nudge instead of a zero. */
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

/** This screen's position in first run, in both variants. */
private const val FIRST_RUN_STEP = 3

private val DIAL_SIZE = 200.dp
private val BRAND_LOGO_WIDTH = 72.dp
private val BRAND_LOGO_HEIGHT = 40.dp
private const val DIAL_TEXT_TOP = 0.86f
private const val NEEDLE_MILLIS = 900
private const val REVEAL_MILLIS = 400

/** Entrance steps: the figures follow the dial, the cards follow the needle. */
private const val FIGURES = 4
private const val AFTER_NEEDLE = 12
