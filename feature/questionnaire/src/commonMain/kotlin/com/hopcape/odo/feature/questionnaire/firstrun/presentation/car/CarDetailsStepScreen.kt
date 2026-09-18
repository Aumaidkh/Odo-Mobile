package com.hopcape.odo.feature.questionnaire.firstrun.presentation.car

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeField
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelField
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeChips
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoLoadingIndicator
import com.hopcape.odo.core.designsystem.component.OdoModelYearField
import com.hopcape.odo.core.designsystem.component.OdoOdometer
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
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_close
import com.hopcape.odo.feature.questionnaire.resources.onb_choose
import com.hopcape.odo.feature.questionnaire.resources.onb_continue
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_body
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_error_title
import com.hopcape.odo.feature.questionnaire.resources.onb_details_catalog_loading
import com.hopcape.odo.feature.questionnaire.resources.onb_details_fuel_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_fuel_other
import com.hopcape.odo.feature.questionnaire.resources.onb_details_model_waiting
import com.hopcape.odo.feature.questionnaire.resources.onb_details_waiting
import com.hopcape.odo.feature.questionnaire.resources.onb_details_make_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_model_label
import com.hopcape.odo.feature.questionnaire.resources.onb_details_odometer_label
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
import com.hopcape.odo.feature.questionnaire.resources.onb_model_search
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_hint
import com.hopcape.odo.feature.questionnaire.resources.onb_odometer_optional
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
 * Step 1 — the car, in four taps and no typing.
 *
 * Pickers keyed to the catalog, because the fairness benchmarks are keyed on
 * make/model/year/fuel — except make and model also offer "not listed" free text for a car
 * the catalog doesn't have. A free-typed car has no peer group to benchmark against until
 * enough other owners report the same one (UnlistedVehicleReporter).
 *
 * No registration number. It used to open this form and gate the step, which asked a
 * stranger for the most guarded thing they have before the app had given them anything;
 * it is now taken later, by the features that can say why they need it.
 *
 * Year and fuel share a row: they're one-tap answers, and pairing them keeps the whole form
 * above the fold. The odometer closes the form and may be left empty — a car saved without
 * a reading is stored pending and asked again where it matters.
 *
 * Stateless: renders [details] + [odometer] and forwards [OnboardingEvent]s.
 */
@Composable
internal fun CarDetailsStepScreen(
    details: CarDetailsState,
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
                odometer = odometer,
                options = catalog.value,
                onEvent = onEvent,
            )
        }
    }
}

/** The four pickers plus the odometer, once the catalog they choose from is in hand. */
@Composable
private fun CarDetailsForm(
    details: CarDetailsState,
    odometer: FormField<Long>,
    options: CatalogOptions,
    onEvent: (OnboardingEvent) -> Unit,
) {
    val choose = stringResource(Res.string.onb_choose)
    val close = stringResource(Res.string.onb_cd_close)
    val matchTemplate = stringResource(Res.string.onb_match_count)
    val makes = options.makes.map { it.toOdoCarMake() }
    // Nothing below the model opens until there is one. A field whose turn has not come is
    // shown waiting rather than left falsely tappable.
    val answered = details.model.value != null

    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
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
                enabled = details.make.value != null,
                placeholder = if (details.make.value == null) {
                    stringResource(Res.string.onb_details_model_waiting)
                } else {
                    choose
                },
                notListedLabel = stringResource(Res.string.onb_model_not_listed),
                notListedNamePlaceholder = stringResource(Res.string.onb_model_not_listed_name_placeholder),
                notListedConfirmLabel = stringResource(Res.string.onb_model_not_listed_confirm),
            )
        }

        // Year keeps the field; fuel does not. Year has thirty-odd answers and needs the
        // wheel, and a full-width row of two would waste the space the chips want.
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
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
                    enabled = answered,
                    placeholder = if (answered) choose else stringResource(Res.string.onb_details_waiting),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                FieldLabel(stringResource(Res.string.onb_details_fuel_label))
                OdoFuelTypeChips(
                    modifier = Modifier.testTag(OnboardingTestTags.FUEL_FIELD),
                    selected = details.fuel.value?.toFuelKind(),
                    options = fuelOptions(options.fuelTypes),
                    primary = PRIMARY_FUELS,
                    onSelect = { onEvent(OnboardingEvent.Details.FuelSelected(it.toDomain())) },
                    otherLabel = stringResource(Res.string.onb_details_fuel_other),
                    title = stringResource(Res.string.onb_fuel_sheet_title),
                    subtitle = stringResource(Res.string.onb_fuel_sheet_subtitle),
                    closeContentDescription = close,
                    enabled = answered,
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
            // A label, not a button. Continue is live without a reading, so a second control
            // that only did what Continue already does was one decision too many — this just
            // says the reading can wait.
            OdoText(
                text = stringResource(Res.string.onb_odometer_optional),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
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
 * owner's car — and it offers the way out: try again.
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

/** Answered — the shape the form is in once all four pickers have been used. */
@OdoThemePreviews
@Composable
private fun CarDetailsStepPreview() = OdoPreview(padded = false) {
    CarDetailsStepScreen(
        details = sampleCarDetails(),
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
        odometer = FormField(),
        canContinue = false,
        onEvent = {},
    )
}

/** The two fuels nearly every car in the catalogue runs on; the rest wait behind "Other". */
private val PRIMARY_FUELS = listOf(OdoFuelKind.PETROL, OdoFuelKind.DIESEL)
