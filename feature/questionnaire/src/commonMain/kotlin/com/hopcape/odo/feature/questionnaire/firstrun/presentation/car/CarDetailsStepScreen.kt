package com.hopcape.odo.feature.questionnaire.firstrun.presentation.car

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeField
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelField
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeField
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoModelYearField
import com.hopcape.odo.core.designsystem.component.OdoOdometer
import com.hopcape.odo.core.designsystem.component.OdoRegistrationNumberField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcRefresh
import com.hopcape.odo.core.designsystem.icons.IcWarning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingEvent
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.FieldLabel
import com.hopcape.odo.core.designsystem.component.OdoIconTile
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.InlineLinkRow
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.fuelOptions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.toDomain
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.toFuelKind
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.withCount
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CarDetailsState
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.CatalogOptions
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.FormField
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.OnboardingStep
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.sampleCarDetails
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.sampleCatalog
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.text
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_cancel
import com.hopcape.odo.feature.questionnaire.resources.onb_car_plate_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_close
import com.hopcape.odo.feature.questionnaire.resources.onb_choose
import com.hopcape.odo.feature.questionnaire.resources.onb_continue
import com.hopcape.odo.feature.questionnaire.resources.onb_details_autofill_action
import com.hopcape.odo.feature.questionnaire.resources.onb_details_autofill_prompt
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_body
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_title
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_loading
import com.hopcape.odo.feature.questionnaire.resources.onb_details_fuel_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_make_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_model_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_odometer_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_plate_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_details_title
import com.hopcape.odo.feature.questionnaire.resources.onb_details_year_label
import com.hopcape.odo.feature.questionnaire.resources.onb_done
import com.hopcape.odo.feature.questionnaire.resources.onb_fuel_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_fuel_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_make_all
import com.hopcape.odo.feature.questionnaire.resources.onb_make_empty
import com.hopcape.odo.feature.questionnaire.resources.onb_make_not_listed
import com.hopcape.odo.feature.questionnaire.resources.onb_make_not_listed_confirm
import com.hopcape.odo.feature.questionnaire.resources.onb_make_not_listed_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_make_popular
import com.hopcape.odo.feature.questionnaire.resources.onb_make_search
import com.hopcape.odo.feature.questionnaire.resources.onb_make_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_make_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_match_count
import com.hopcape.odo.feature.questionnaire.resources.onb_model_all
import com.hopcape.odo.feature.questionnaire.resources.onb_model_empty
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed_confirm
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed_name_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed_variant_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_model_search
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_hint
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_label
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_save
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_retry
import com.hopcape.odo.feature.questionnaire.resources.onb_unit_km
import com.hopcape.odo.feature.questionnaire.resources.onb_unit_miles
import com.hopcape.odo.feature.questionnaire.resources.onb_year_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_year_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2 (manual route) — the same step, answered by hand when the plate lookup misses or
 * the owner doesn't want the registry to name their car. Four pickers keyed to the catalog,
 * because the fairness benchmarks are keyed on make/model/year/fuel — except make and model
 * also offer "not listed" free text for a car the catalog doesn't have. A free-typed car
 * simply has no peer group to benchmark against yet, which is honest: it has none until
 * enough other owners report the same one (UnlistedVehicleReporter).
 *
 * The registration number opens the form, and it is required here exactly as it is on the
 * other route. It is the same field, holding the same value — somebody who typed a plate and
 * then came here finds it already filled in — but on this route it names the car rather than
 * looking it up, so nothing is fetched when it changes.
 *
 * Year and fuel share a row: they're one-tap answers, and pairing them keeps the whole form
 * above the fold. The odometer closes the form — this route reaches the same `Car` the plate
 * route does, and no car exists without a reading.
 *
 * The pickers can only render once the catalog has loaded, so the form waits behind that
 * state; the link back to [CarStepScreen] does not, because a broken catalog is exactly when
 * the owner most needs the other route.
 *
 * When the plate lookup found a car, the pickers arrive already answered from it — rejecting
 * a match is nearly always about one wrong field, not all four.
 *
 * Stateless: renders [details] + [odometer] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun CarDetailsStepScreen(
    details: CarDetailsState,
    plate: FormField<String>,
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
            title = stringResource(Res.string.onb_details_title),
            subtitle = stringResource(Res.string.onb_details_subtitle),
        )

        when (val catalog = details.catalog) {
            Loadable.Loading -> CatalogLoadingCard()
            is Loadable.Failed -> CatalogFailedCard(
                message = catalog.message,
                onRetry = { onEvent(OnboardingEvent.Details.CatalogRetried) },
            )

            is Loadable.Ready -> CarDetailsForm(
                details = details,
                plate = plate,
                odometer = odometer,
                options = catalog.value,
                onEvent = onEvent,
            )
        }

        InlineLinkRow(
            prompt = stringResource(Res.string.onb_details_autofill_prompt),
            action = stringResource(Res.string.onb_details_autofill_action),
            onClick = { onEvent(OnboardingEvent.Details.TryAutoFillClicked) },
            leadingIcon = IcRefresh,
            boxed = true,
        )
    }
}

/**
 * The plate and the four pickers plus the odometer, once the catalog they choose from is in
 * hand.
 */
@Composable
private fun CarDetailsForm(
    details: CarDetailsState,
    plate: FormField<String>,
    odometer: FormField<Long>,
    options: CatalogOptions,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val choose = stringResource(Res.string.onb_choose)
    val close = stringResource(Res.string.onb_cd_close)
    val matchTemplate = stringResource(Res.string.onb_match_count)
    val makes = options.makes.map { it.toOdoCarMake() }

    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_plate_label))
            OdoRegistrationNumberField(
                modifier = Modifier.testTag(OnboardingTestTags.PLATE_FIELD),
                value = plate.text,
                onValueChange = { onEvent(OnboardingEvent.Car.PlateChanged(it)) },
                placeholder = stringResource(Res.string.onb_car_plate_placeholder),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_make_label))
            OdoCarMakeField(
                selected = details.make.value?.toOdoCarMake(),
                modifier = Modifier.testTag(OnboardingTestTags.MAKE_FIELD),
                makes = makes,
                popular = options.popularMakes.map { it.toOdoCarMake() },
                onSelect = { onEvent(OnboardingEvent.Details.MakeSelected(it.name)) },
                title = stringResource(Res.string.onb_make_sheet_title),
                subtitle = stringResource(Res.string.onb_make_sheet_subtitle, makes.size),
                searchPlaceholder = stringResource(Res.string.onb_make_search),
                matchCountLabel = matchTemplate::withCount,
                popularSectionLabel = stringResource(Res.string.onb_make_popular),
                allSectionLabel = stringResource(Res.string.onb_make_all),
                emptyResultsText = stringResource(Res.string.onb_make_empty),
                closeContentDescription = close,
                placeholder = choose,
                notListedLabel = stringResource(Res.string.onb_make_not_listed),
                notListedPlaceholder = stringResource(Res.string.onb_make_not_listed_placeholder),
                notListedConfirmLabel = stringResource(Res.string.onb_make_not_listed_confirm),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_model_label))
            OdoCarModelField(
                modifier = Modifier.testTag(OnboardingTestTags.MODEL_FIELD),
                selected = details.model.value?.toOdoCarModel(),
                models = details.models.map { it.toOdoCarModel() },
                onSelect = { onEvent(OnboardingEvent.Details.ModelSelected(it.toDomainModel())) },
                title = stringResource(Res.string.onb_model_sheet_title),
                subtitle = stringResource(
                    Res.string.onb_model_sheet_subtitle,
                    details.make.value ?: choose,
                ),
                searchPlaceholder = stringResource(Res.string.onb_model_search),
                matchCountLabel = matchTemplate::withCount,
                allSectionLabel = stringResource(Res.string.onb_model_all),
                emptyResultsText = stringResource(Res.string.onb_model_empty),
                closeContentDescription = close,
                placeholder = choose,
                notListedLabel = stringResource(Res.string.onb_model_not_listed),
                notListedNamePlaceholder = stringResource(Res.string.onb_model_not_listed_name_placeholder),
                notListedVariantPlaceholder = stringResource(Res.string.onb_model_not_listed_variant_placeholder),
                notListedConfirmLabel = stringResource(Res.string.onb_model_not_listed_confirm),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.onb_details_year_label))
                OdoModelYearField(
                    modifier = Modifier.testTag(OnboardingTestTags.YEAR_FIELD),
                    selected = details.year.value,
                    years = options.years,
                    onSelect = { onEvent(OnboardingEvent.Details.YearSelected(it)) },
                    title = stringResource(Res.string.onb_year_sheet_title),
                    cancelLabel = stringResource(Res.string.onb_cancel),
                    confirmLabel = stringResource(Res.string.onb_done),
                    subtitle = stringResource(Res.string.onb_year_sheet_subtitle),
                    placeholder = choose,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.onb_details_fuel_label))
                OdoFuelTypeField(
                    modifier = Modifier.testTag(OnboardingTestTags.FUEL_FIELD),
                    selected = details.fuel.value?.toFuelKind(),
                    options = fuelOptions(options.fuelTypes),
                    onSelect = { onEvent(OnboardingEvent.Details.FuelSelected(it.toDomain())) },
                    title = stringResource(Res.string.onb_fuel_sheet_title),
                    subtitle = stringResource(Res.string.onb_fuel_sheet_subtitle),
                    closeContentDescription = close,
                    placeholder = choose,
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_odometer_label))
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
        }
    }
}

/** The wait for the brand/year/fuel lists — a local read, so this is usually a blink. */
@Composable
private fun CatalogLoadingCard() {
    OdoCard(color = OdoTheme.colors.surface, border = BorderStroke(1.dp, OdoTheme.colors.border)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoLoadingIndicator(size = OdoTheme.iconSizes.large, strokeWidth = 2.dp)
            OdoText(
                text = stringResource(Res.string.onb_details_catalog_loading),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textDim,
            )
        }
    }
}

/**
 * The catalog didn't load. Warning-toned rather than danger — nothing is wrong with the
 * owner's car — and it offers the two ways out: try again, or the plate route in the link
 * below it.
 */
@Composable
private fun CatalogFailedCard(message: UiText, onRetry: () -> Unit) {
    val tone = OdoTheme.colors.warning
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
                OdoText(
                    text = stringResource(Res.string.onb_details_catalog_error_title),
                    style = OdoTheme.typography.heading,
                    color = OdoTheme.colors.text,
                )
                OdoText(
                    text = message.asString(),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
        }
        OdoButton(
            text = stringResource(Res.string.onb_retry),
            onClick = onRetry,
            variant = OdoButtonVariant.Secondary,
            leadingIcon = {
                OdoIcon(IcRefresh, contentDescription = null, size = OdoTheme.iconSizes.medium)
            },
        )
    }
}

/**
 * Catalog make → picker row. The catalog is a flat list of brand names today, so the id is
 * the name; when the real `VehicleCatalog` lands with ids, only this mapper changes.
 */
private fun String.toOdoCarMake(): OdoCarMake = OdoCarMake(id = this, name = this)

/**
 * Domain model ⇄ picker row. The display name doubles as the row id: a model is offered both
 * with and without a trim ("City", "City VX CVT"), and those read differently, so they key
 * differently too.
 */
private fun CarModel.toOdoCarModel(): OdoCarModel =
    OdoCarModel(id = displayName, name = name, variant = variant)

private fun OdoCarModel.toDomainModel(): CarModel = CarModel(name = name, variant = variant)

/** Answered — the shape the form arrives in when a plate lookup prefilled it. */
@OdoThemePreviews
@Composable
private fun CarDetailsStepPreview() = OdoPreview(padded = false) {
    CarDetailsStepScreen(
        details = sampleCarDetails(),
        plate = FormField("MH12AB1234"),
        odometer = FormField(54_000L),
        canContinue = true,
        onEvent = {},
    )
}

@OdoThemePreviews
@Composable
private fun CarDetailsStepEmptyPreview() = OdoPreview(padded = false) {
    CarDetailsStepPreview(CarDetailsState(catalog = Loadable.Ready(sampleCatalog())))
}

@OdoThemePreviews
@Composable
private fun CarDetailsStepLoadingPreview() = OdoPreview(padded = false) {
    CarDetailsStepPreview(CarDetailsState())
}

@OdoThemePreviews
@Composable
private fun CarDetailsStepCatalogFailedPreview() = OdoPreview(padded = false) {
    CarDetailsStepPreview(
        CarDetailsState(catalog = Loadable.Failed(UiText(Res.string.onb_details_catalog_error_body))),
    )
}

/** The unanswered previews differ only in the state of the catalog behind the pickers. */
@Composable
private fun CarDetailsStepPreview(details: CarDetailsState) {
    CarDetailsStepScreen(
        details = details,
        plate = FormField(""),
        odometer = FormField(),
        canContinue = false,
        onEvent = {},
    )
}
