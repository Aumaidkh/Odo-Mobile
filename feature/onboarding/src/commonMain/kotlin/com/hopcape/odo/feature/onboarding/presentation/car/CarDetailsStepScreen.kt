package com.hopcape.odo.feature.onboarding.presentation.car

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeField
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelField
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeField
import com.hopcape.odo.core.designsystem.component.OdoModelYearField
import com.hopcape.odo.core.designsystem.icons.IcRefresh
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.onboarding.presentation.CarForm
import com.hopcape.odo.feature.onboarding.presentation.CarModelOption
import com.hopcape.odo.feature.onboarding.presentation.OnboardingStep
import com.hopcape.odo.feature.onboarding.presentation.OnboardingUiState
import com.hopcape.odo.feature.onboarding.presentation.components.FieldLabel
import com.hopcape.odo.feature.onboarding.presentation.components.InlineLinkRow
import com.hopcape.odo.feature.onboarding.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.onboarding.presentation.components.StepHeadline
import com.hopcape.odo.feature.onboarding.presentation.components.fuelOptions
import com.hopcape.odo.feature.onboarding.presentation.components.toDomain
import com.hopcape.odo.feature.onboarding.presentation.components.toFuelKind
import com.hopcape.odo.feature.onboarding.presentation.components.withCount
import com.hopcape.odo.feature.onboarding.presentation.sampleOnboardingState
import com.hopcape.odo.feature.onboarding.resources.Res
import com.hopcape.odo.feature.onboarding.resources.onb_cd_close
import com.hopcape.odo.feature.onboarding.resources.onb_cancel
import com.hopcape.odo.feature.onboarding.resources.onb_choose
import com.hopcape.odo.feature.onboarding.resources.onb_continue
import com.hopcape.odo.feature.onboarding.resources.onb_details_autofill_action
import com.hopcape.odo.feature.onboarding.resources.onb_details_autofill_prompt
import com.hopcape.odo.feature.onboarding.resources.onb_details_fuel_label
import com.hopcape.odo.feature.onboarding.resources.onb_details_make_label
import com.hopcape.odo.feature.onboarding.resources.onb_details_model_label
import com.hopcape.odo.feature.onboarding.resources.onb_details_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_details_title
import com.hopcape.odo.feature.onboarding.resources.onb_details_year_label
import com.hopcape.odo.feature.onboarding.resources.onb_done
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_fuel_sheet_title
import com.hopcape.odo.feature.onboarding.resources.onb_make_all
import com.hopcape.odo.feature.onboarding.resources.onb_make_empty
import com.hopcape.odo.feature.onboarding.resources.onb_make_popular
import com.hopcape.odo.feature.onboarding.resources.onb_make_search
import com.hopcape.odo.feature.onboarding.resources.onb_make_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_make_sheet_title
import com.hopcape.odo.feature.onboarding.resources.onb_match_count
import com.hopcape.odo.feature.onboarding.resources.onb_model_all
import com.hopcape.odo.feature.onboarding.resources.onb_model_empty
import com.hopcape.odo.feature.onboarding.resources.onb_model_search
import com.hopcape.odo.feature.onboarding.resources.onb_model_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_model_sheet_title
import com.hopcape.odo.feature.onboarding.resources.onb_year_sheet_subtitle
import com.hopcape.odo.feature.onboarding.resources.onb_year_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * Step 2 (manual route) — the same step, answered by hand when the plate lookup misses or
 * the owner doesn't want to type a plate. Four pickers, no free text: every value has to
 * match the catalog, because the fairness benchmarks are keyed on make/model/year/fuel.
 *
 * Year and fuel share a row: they're one-tap answers, and pairing them keeps the whole form
 * above the fold. The link at the bottom returns to [CarStepScreen].
 *
 * Stateless: renders [state] and forwards intents.
 */
@Composable
internal fun CarDetailsStepScreen(
    state: OnboardingUiState,
    onMakeChange: (String) -> Unit,
    onModelChange: (CarModelOption) -> Unit,
    onYearChange: (Int) -> Unit,
    onFuelChange: (FuelType) -> Unit,
    onTryAutoFill: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        step = OnboardingStep.CAR.position,
        primaryLabel = stringResource(Res.string.onb_continue),
        onPrimary = onContinue,
        modifier = modifier,
        onBack = onBack,
        primaryEnabled = state.car.canContinue,
    ) {
        StepHeadline(
            title = stringResource(Res.string.onb_details_title),
            subtitle = stringResource(Res.string.onb_details_subtitle),
        )

        val choose = stringResource(Res.string.onb_choose)
        val close = stringResource(Res.string.onb_cd_close)
        val matchTemplate = stringResource(Res.string.onb_match_count)
        val makes = state.makes.map { it.toOdoCarMake() }
        val selectedMake = state.car.make?.toOdoCarMake()

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_make_label))
            OdoCarMakeField(
                selected = selectedMake,
                makes = makes,
                popular = state.popularMakes.map { it.toOdoCarMake() },
                onSelect = { onMakeChange(it.name) },
                title = stringResource(Res.string.onb_make_sheet_title),
                subtitle = stringResource(Res.string.onb_make_sheet_subtitle, makes.size),
                searchPlaceholder = stringResource(Res.string.onb_make_search),
                matchCountLabel = matchTemplate::withCount,
                popularSectionLabel = stringResource(Res.string.onb_make_popular),
                allSectionLabel = stringResource(Res.string.onb_make_all),
                emptyResultsText = stringResource(Res.string.onb_make_empty),
                closeContentDescription = close,
                placeholder = choose,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.onb_details_model_label))
            OdoCarModelField(
                selected = state.car.selectedModel(),
                models = state.models.map { it.toOdoCarModel() },
                onSelect = { onModelChange(CarModelOption(name = it.name, variant = it.variant)) },
                title = stringResource(Res.string.onb_model_sheet_title),
                subtitle = stringResource(
                    Res.string.onb_model_sheet_subtitle,
                    state.car.make ?: choose,
                ),
                searchPlaceholder = stringResource(Res.string.onb_model_search),
                matchCountLabel = matchTemplate::withCount,
                allSectionLabel = stringResource(Res.string.onb_model_all),
                emptyResultsText = stringResource(Res.string.onb_model_empty),
                closeContentDescription = close,
                placeholder = choose,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.onb_details_year_label))
                OdoModelYearField(
                    selected = state.car.year,
                    years = state.years,
                    onSelect = onYearChange,
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
                    selected = state.car.fuelType?.toFuelKind(),
                    options = fuelOptions(),
                    onSelect = { onFuelChange(it.toDomain()) },
                    title = stringResource(Res.string.onb_fuel_sheet_title),
                    subtitle = stringResource(Res.string.onb_fuel_sheet_subtitle),
                    closeContentDescription = close,
                    placeholder = choose,
                )
            }
        }

        InlineLinkRow(
            prompt = stringResource(Res.string.onb_details_autofill_prompt),
            action = stringResource(Res.string.onb_details_autofill_action),
            onClick = onTryAutoFill,
            leadingIcon = IcRefresh,
            boxed = true,
        )
    }
}

/**
 * Catalog make → picker row. The catalog is a flat list of brand names today, so the id is
 * the name; when the real `VehicleCatalog` lands with ids, only this mapper changes.
 */
private fun String.toOdoCarMake(): OdoCarMake = OdoCarMake(id = this, name = this)

private fun CarModelOption.toOdoCarModel(): OdoCarModel =
    OdoCarModel(id = id, name = name, variant = variant)

/** The chosen model as the picker's row type — `null` until a model is picked. */
private fun CarForm.selectedModel(): OdoCarModel? =
    model?.let { CarModelOption(name = it, variant = variant).toOdoCarModel() }

@OdoThemePreviews
@Composable
private fun CarDetailsStepPreview() = OdoPreview(padded = false) {
    CarDetailsStepScreen(
        state = sampleOnboardingState().copy(
            car = CarForm(
                manualEntry = true,
                make = "Honda",
                model = "City",
                variant = "VX CVT",
                year = 2026,
                fuelType = FuelType.PETROL,
            ),
        ),
        onMakeChange = {},
        onModelChange = {},
        onYearChange = {},
        onFuelChange = {},
        onTryAutoFill = {},
        onBack = {},
        onContinue = {},
    )
}

@OdoThemePreviews
@Composable
private fun CarDetailsStepEmptyPreview() = OdoPreview(padded = false) {
    CarDetailsStepScreen(
        state = sampleOnboardingState().copy(car = CarForm(manualEntry = true)),
        onMakeChange = {},
        onModelChange = {},
        onYearChange = {},
        onFuelChange = {},
        onTryAutoFill = {},
        onBack = {},
        onContinue = {},
    )
}
