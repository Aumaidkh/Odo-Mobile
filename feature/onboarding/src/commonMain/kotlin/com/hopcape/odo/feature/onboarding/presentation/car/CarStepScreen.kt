package com.hopcape.odo.feature.onboarding.presentation.car

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.component.formatRegistrationNumber
import com.hopcape.odo.core.designsystem.icons.IcCar
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcRefresh
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.feature.onboarding.presentation.OnboardingEvent
import com.hopcape.odo.feature.onboarding.presentation.OnboardingTestTags
import com.hopcape.odo.core.designsystem.component.OdoIconTile
import com.hopcape.odo.feature.onboarding.presentation.components.InlineLinkRow
import com.hopcape.odo.feature.onboarding.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.onboarding.presentation.components.StepHeadline
import com.hopcape.odo.feature.onboarding.presentation.components.fuelLabel
import com.hopcape.odo.feature.onboarding.presentation.state.CarStepState
import com.hopcape.odo.feature.onboarding.presentation.state.FormField
import com.hopcape.odo.feature.onboarding.presentation.state.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookup
import com.hopcape.odo.feature.onboarding.presentation.state.PlateLookupError
import com.hopcape.odo.feature.onboarding.presentation.state.RtoMatch
import com.hopcape.odo.feature.onboarding.presentation.state.sampleCarStep
import com.hopcape.odo.feature.onboarding.presentation.state.text
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_car_enter_manually
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_loading
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_loading_note
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_not_found_body
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_not_found_title
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_offline_body
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_offline_title
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_retry
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_service_body
import com.hopcape.odo.feature.onboarding.resources.onb_car_lookup_service_title
import com.hopcape.odo.feature.onboarding.resources.onb_car_match_meta
import com.hopcape.odo.feature.onboarding.resources.onb_car_not_yours
import com.hopcape.odo.feature.onboarding.resources.onb_car_odometer_helper
import com.hopcape.odo.feature.onboarding.resources.onb_car_odometer_label
import com.hopcape.odo.feature.onboarding.resources.onb_car_plate_placeholder
import com.hopcape.odo.feature.onboarding.resources.onb_car_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_car_title
import com.hopcape.odo.feature.onboarding.resources.onb_cd_car_matched
import com.hopcape.odo.feature.onboarding.resources.onb_continue
import com.hopcape.odo.feature.onboarding.resources.onb_odometer_hint
import com.hopcape.odo.feature.onboarding.resources.onb_odometer_label
import com.hopcape.odo.feature.onboarding.resources.onb_odometer_save
import com.hopcape.odo.feature.onboarding.resources.onb_odometer_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_odometer_sheet_title
import com.hopcape.odo.feature.onboarding.resources.onb_unit_km
import com.hopcape.odo.feature.onboarding.resources.onb_unit_miles
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2 (plate route) — one field does the work: the number plate, which the RTO lookup
 * turns into a confirmed car. The owner types ten characters instead of answering four
 * dropdowns, and the escape hatch to [CarDetailsStepScreen] sits right under the match.
 *
 * The odometer is asked for here rather than later because it is the one number the whole
 * product hangs off (₹/km, health score, km-anomaly checks), so it belongs to car setup.
 *
 * Stateless: renders [car] + [odometer] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun CarStepScreen(
    car: CarStepState,
    odometer: FormField<Long>,
    canContinue: Boolean,
    onEvent: (OnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        step = OnboardingStep.CAR.position,
        primaryLabel = stringResource(Res.string.onb_continue),
        onPrimary = { onEvent(OnboardingEvent.ContinueClicked) },
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.BackClicked) },
        primaryEnabled = canContinue,
    ) {
        StepHeadline(
            title = stringResource(Res.string.onb_car_title),
            subtitle = stringResource(Res.string.onb_car_subtitle),
        )

        OdoRegistrationNumberField(
            modifier = Modifier.testTag(OnboardingTestTags.PLATE_FIELD),
            value = car.plate.text,
            onValueChange = { onEvent(OnboardingEvent.Car.PlateChanged(it)) },
            placeholder = stringResource(Res.string.onb_car_plate_placeholder),
        )

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            // The lookup answers in place, directly under the plate that triggered it: the
            // wait, the car, or the failure all occupy the same slot and the same card
            // silhouette, so the screen never jumps as the answer arrives.
            AnimatedVisibility(
                visible = car.lookup != PlateLookup.Idle,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                AnimatedContent(
                    targetState = car.lookup,
                    transitionSpec = { fadeIn(tween(LookupFadeMillis)) togetherWith fadeOut(tween(LookupFadeMillis)) },
                    label = "plateLookup",
                ) { lookup ->
                    when (lookup) {
                        PlateLookup.Idle -> Unit
                        PlateLookup.Loading -> LookupSkeletonCard()
                        is PlateLookup.Found -> MatchedCarCard(lookup.match)
                        is PlateLookup.Failed -> LookupFailedCard(
                            reason = lookup.reason,
                            plate = car.plate.text,
                            onRetry = { onEvent(OnboardingEvent.Car.LookupRetried) },
                        )
                    }
                }
            }
            // Always offered, match or not: it is both "that's the wrong car" and the way
            // forward when the lookup finds nothing at all.
            InlineLinkRow(
                prompt = stringResource(Res.string.onb_car_not_yours),
                action = stringResource(Res.string.onb_car_enter_manually),
                onClick = { onEvent(OnboardingEvent.Car.MatchRejected) },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            OdoText(
                text = stringResource(Res.string.onb_car_odometer_label),
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
            )
            val distance = LocalOdoDistanceFormat.current
            OdoOdometer(
                modifier = Modifier.testTag(OnboardingTestTags.ODOMETER_FIELD),
                // The drums show and take the owner's unit; the reading stored is always
                // kilometres, converted here at the one place the two meet.
                value = odometer.value?.let { distance.display(it.toInt()).toLong() },
                onValueChange = { dialled ->
                    val km = distance.store(dialled.toInt(), odometer.value?.toInt())
                    onEvent(OnboardingEvent.OdometerChanged(km.toLong()))
                },
                title = stringResource(Res.string.onb_odometer_sheet_title),
                subtitle = stringResource(Res.string.onb_odometer_sheet_subtitle),
                odometerLabel = stringResource(Res.string.onb_odometer_label),
                saveLabel = stringResource(Res.string.onb_odometer_save),
                kmLabel = stringResource(Res.string.onb_unit_km),
                milesLabel = stringResource(Res.string.onb_unit_miles),
                hint = stringResource(Res.string.onb_odometer_hint),
            )
            // Rendered here rather than through the component's `helper` slot: that slot is
            // the tracked-caps caption style, and this is a plain sentence.
            OdoText(
                text = stringResource(Res.string.onb_car_odometer_helper),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
            )
        }
    }
}

/**
 * The RTO's answer, as a card the owner confirms by reading rather than by tapping: it is
 * already selected (the check is state, not a control). Rejecting it is the link beneath.
 */
@Composable
private fun MatchedCarCard(match: RtoMatch) {
    val accent = OdoTheme.colors.accent
    OdoCard(
        color = accent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIconTile(IcCar, size = 48.dp)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OdoText(match.title, style = OdoTheme.typography.heading, color = OdoTheme.colors.text)
                OdoText(
                    text = stringResource(Res.string.onb_car_match_meta, match.year, fuelLabel(match.fuelType)),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoIcon(
                IcCheck,
                contentDescription = stringResource(Res.string.onb_cd_car_matched),
                tint = accent,
                size = OdoTheme.iconSizes.large,
            )
        }
    }
}

/**
 * The wait, in the shape of the answer. Same silhouette as [MatchedCarCard] — tile, two
 * lines, trailing mark — so when the real card arrives nothing shifts; only the content
 * resolves. The tile and the placeholder bars breathe, which reads as "working" without a
 * blocking spinner over the form.
 */
@Composable
private fun LookupSkeletonCard() {
    val colors = OdoTheme.colors
    val transition = rememberInfiniteTransition(label = "lookupPulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(PulseMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "lookupPulseAlpha",
    )

    OdoCard(color = colors.surface, border = BorderStroke(1.dp, colors.border)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIconTile(
                icon = IcCar,
                size = 48.dp,
                tint = colors.textMuted,
                modifier = Modifier.alpha(pulse),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoText(
                    text = stringResource(Res.string.onb_car_lookup_loading),
                    style = OdoTheme.typography.heading,
                    color = colors.text,
                )
                OdoText(
                    text = stringResource(Res.string.onb_car_lookup_loading_note),
                    style = OdoTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
            OdoLoadingIndicator(size = OdoTheme.iconSizes.large, strokeWidth = 2.dp)
        }
    }
}

/**
 * The lookup came back empty. Warning-toned, not danger — nothing is wrong with the owner's
 * car, only with our ability to name it — and the way forward (manual entry) is already on
 * screen right below. Retry is offered only where retrying can actually change the answer:
 * a plate the registry has never heard of will still be unknown a second later.
 */
@Composable
private fun LookupFailedCard(
    reason: PlateLookupError,
    plate: String,
    onRetry: () -> Unit,
) {
    val colors = OdoTheme.colors
    val tone = colors.warning
    OdoCard(
        color = tone.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.45f)),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            OdoIconTile(icon = IcWarning, size = 48.dp, tint = tone)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoText(lookupErrorTitle(reason), style = OdoTheme.typography.heading, color = colors.text)
                OdoText(
                    text = lookupErrorBody(reason, formatRegistrationNumber(plate)),
                    style = OdoTheme.typography.bodySmall,
                    color = colors.textDim,
                )
            }
        }
        if (reason != PlateLookupError.NOT_FOUND) {
            OdoButton(
                text = stringResource(Res.string.onb_car_lookup_retry),
                onClick = onRetry,
                variant = OdoButtonVariant.Secondary,
                leadingIcon = {
                    OdoIcon(IcRefresh, contentDescription = null, size = OdoTheme.iconSizes.medium)
                },
            )
        }
    }
}

@Composable
private fun lookupErrorTitle(reason: PlateLookupError): String = stringResource(
    when (reason) {
        PlateLookupError.NOT_FOUND -> Res.string.onb_car_lookup_not_found_title
        PlateLookupError.OFFLINE -> Res.string.onb_car_lookup_offline_title
        PlateLookupError.SERVICE -> Res.string.onb_car_lookup_service_title
    },
)

@Composable
private fun lookupErrorBody(reason: PlateLookupError, plate: String): String = when (reason) {
    // Only the "never heard of it" case names the plate — it is the one the owner should
    // re-read for a typo.
    PlateLookupError.NOT_FOUND -> stringResource(Res.string.onb_car_lookup_not_found_body, plate)
    PlateLookupError.OFFLINE -> stringResource(Res.string.onb_car_lookup_offline_body)
    PlateLookupError.SERVICE -> stringResource(Res.string.onb_car_lookup_service_body)
}

private const val PulseMillis = 900
private const val LookupFadeMillis = 200

@OdoThemePreviews
@Composable
private fun CarStepMatchedPreview() = OdoPreview(padded = false) {
    CarStepScreen(
        car = sampleCarStep(),
        odometer = FormField(54_000L),
        canContinue = true,
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun CarStepEmptyPreview() = OdoPreview(padded = false) {
    CarStepPreview(CarStepState())
}

@OdoThemePreviews
@Composable
private fun CarStepLoadingPreview() = OdoPreview(padded = false) {
    CarStepPreview(lookupPreviewState(PlateLookup.Loading))
}

@OdoThemePreviews
@Composable
private fun CarStepNotFoundPreview() = OdoPreview(padded = false) {
    CarStepPreview(lookupPreviewState(PlateLookup.Failed(PlateLookupError.NOT_FOUND)))
}

@OdoThemePreviews
@Composable
private fun CarStepServiceErrorPreview() = OdoPreview(padded = false) {
    CarStepPreview(lookupPreviewState(PlateLookup.Failed(PlateLookupError.SERVICE)))
}

/** The unanswered previews differ only in how the lookup came back. */
@Composable
private fun CarStepPreview(car: CarStepState) {
    CarStepScreen(car = car, odometer = FormField(), canContinue = false, onEvent = {})
}

private fun lookupPreviewState(lookup: PlateLookup): CarStepState =
    CarStepState(plate = FormField("MH12AB1234"), lookup = lookup)
