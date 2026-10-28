package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import kotlinx.datetime.LocalDate
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelSheet
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoEntrance
import com.hopcape.odo.core.designsystem.component.OdoStepTransition
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoIconTile
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoMakeLogo
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoModelYearSheet
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcMagnifier
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.OnboardingTestTags
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.car.CatalogFailedCard
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.car.CatalogLoadingCard
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.OnboardingStepScaffold
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.StepHeadline
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.FieldLabel
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.fuelLabel
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.components.withCount
import com.hopcape.odo.feature.questionnaire.firstrun.presentation.state.Loadable
import com.hopcape.odo.feature.questionnaire.resources.Res
import com.hopcape.odo.feature.questionnaire.resources.onb_cancel
import com.hopcape.odo.feature.questionnaire.resources.onb_car_summary_pending
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_close
import com.hopcape.odo.feature.questionnaire.resources.onb_done
import com.hopcape.odo.feature.questionnaire.resources.onb_insurance_confirm
import com.hopcape.odo.feature.questionnaire.resources.onb_insurance_helper
import com.hopcape.odo.feature.questionnaire.resources.onb_insurance_skip
import com.hopcape.odo.feature.questionnaire.resources.onb_insurance_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_insurance_title
import com.hopcape.odo.feature.questionnaire.resources.onb_fuel_label
import com.hopcape.odo.feature.questionnaire.resources.onb_match_count
import com.hopcape.odo.feature.questionnaire.resources.onb_model_all
import com.hopcape.odo.feature.questionnaire.resources.onb_model_cant_find
import com.hopcape.odo.feature.questionnaire.resources.onb_model_cant_find_body
import com.hopcape.odo.feature.questionnaire.resources.onb_model_change_make
import com.hopcape.odo.feature.questionnaire.resources.onb_model_empty
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed_confirm
import com.hopcape.odo.feature.questionnaire.resources.onb_model_not_listed_name_placeholder
import com.hopcape.odo.feature.questionnaire.resources.onb_model_search_hint
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_model_sheet_title
import com.hopcape.odo.feature.questionnaire.resources.onb_model_title
import com.hopcape.odo.feature.questionnaire.resources.onb_save_car
import com.hopcape.odo.feature.questionnaire.resources.onb_save_car_helper
import com.hopcape.odo.feature.questionnaire.resources.onb_year_fuel_title
import com.hopcape.odo.feature.questionnaire.resources.onb_year_label
import com.hopcape.odo.feature.questionnaire.resources.onb_year_older
import com.hopcape.odo.feature.questionnaire.resources.onb_year_sheet_subtitle
import com.hopcape.odo.feature.questionnaire.resources.onb_year_sheet_title
import org.jetbrains.compose.resources.stringResource

/** The two v2 setup steps behind one destination, so back moves between them. */
@Composable
internal fun SetupV2Screen(
    state: SetupV2UiState,
    onEvent: (SetupV2Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    OdoStepTransition(target = state.step, position = state.step.ordinal, modifier = modifier, label = "setupV2Step") { step ->
        when (step) {
            SetupV2Step.MODEL -> ModelStep(state, onEvent)
            SetupV2Step.YEAR_FUEL -> YearFuelStep(state, onEvent)
            SetupV2Step.INSURANCE -> InsuranceStep(state, onEvent)
        }
    }
}

/** S2: the make in the eyebrow, a grid of base models, and search over every trim. */
@Composable
private fun ModelStep(state: SetupV2UiState, onEvent: (SetupV2Event) -> Unit) {
    OnboardingStepScaffold(
        step = SetupV2Step.MODEL.position,
        totalSteps = SetupV2Step.TOTAL,
        primaryLabel = null,
        onPrimary = {},
        onBack = { onEvent(SetupV2Event.BackClicked) },
    ) {
        OdoEntrance(0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            ) {
                OdoMakeLogo(make = state.makeBadge(), width = BADGE_WIDTH, height = BADGE_HEIGHT)
                OdoText(
                    text = state.make,
                    style = OdoTheme.typography.heading,
                    color = OdoTheme.colors.text,
                    modifier = Modifier.weight(1f),
                )
                OdoButton(
                    text = stringResource(Res.string.onb_model_change_make),
                    onClick = { onEvent(SetupV2Event.ChangeMakeClicked) },
                    variant = OdoButtonVariant.Tertiary,
                    modifier = Modifier.testTag(OnboardingTestTags.CHANGE_MAKE),
                )
            }
        }
        OdoEntrance(1) {
            OdoText(
                text = stringResource(Res.string.onb_model_title),
                style = OdoTheme.typography.title,
                color = OdoTheme.colors.text,
            )
        }
        when (val catalog = state.catalog) {
            Loadable.Loading -> CatalogLoadingCard()
            is Loadable.Failed -> CatalogFailedCard(catalog.message) { onEvent(SetupV2Event.CatalogRetried) }
            is Loadable.Ready -> {
                val models = catalog.value.baseModels
                ModelGrid(models) { onEvent(SetupV2Event.ModelChosen(it)) }
                OdoEntrance(2 + (models.size + 1) / 2) {
                    CantFindCard(make = state.make, onClick = { onEvent(SetupV2Event.ModelSearchOpened) })
                }
                if (state.searchOpen) {
                    ModelSearchSheet(
                        make = state.make,
                        models = catalog.value.models,
                        onDismiss = { onEvent(SetupV2Event.ModelSearchDismissed) },
                        onSelect = { onEvent(SetupV2Event.ModelChosen(it, fromSearch = true)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelGrid(models: List<CarModel>, onChoose: (CarModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        models.chunked(2).forEachIndexed { rowIndex, row ->
            OdoEntrance(2 + rowIndex) {
                Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                    row.forEach { model ->
                        OdoCard(
                            onClick = { onChoose(model) },
                            modifier = Modifier.weight(1f).testTag(OnboardingTestTags.modelTile(model.name)),
                            contentPadding = PaddingValues(OdoTheme.spacing.md),
                        ) {
                            OdoText(
                                text = model.displayName,
                                style = OdoTheme.typography.heading,
                                color = OdoTheme.colors.text,
                                maxLines = 2,
                            )
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** The way to every other model: opens the full list, where a missing one can be typed. */
@Composable
private fun CantFindCard(make: String, onClick: () -> Unit) {
    OdoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.MODEL_SEARCH),
        contentPadding = PaddingValues(OdoTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIconTile(IcMagnifier)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    text = stringResource(Res.string.onb_model_cant_find),
                    style = OdoTheme.typography.heading,
                    color = OdoTheme.colors.text,
                )
                OdoText(
                    text = stringResource(Res.string.onb_model_cant_find_body, make),
                    style = OdoTheme.typography.bodySmall,
                    color = OdoTheme.colors.textDim,
                )
            }
            OdoIcon(IcChevronRight, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
        }
    }
}

@Composable
private fun ModelSearchSheet(
    make: String,
    models: List<CarModel>,
    onDismiss: () -> Unit,
    onSelect: (CarModel) -> Unit,
) {
    val matchTemplate = stringResource(Res.string.onb_match_count)
    OdoCarModelSheet(
        onDismissRequest = onDismiss,
        models = models.map { OdoCarModel(id = it.displayName, name = it.name, variant = it.variant) },
        onSelect = { onSelect(CarModel(name = it.name, variant = it.variant)) },
        title = stringResource(Res.string.onb_model_sheet_title),
        subtitle = stringResource(Res.string.onb_model_sheet_subtitle, make),
        searchPlaceholder = stringResource(Res.string.onb_model_search_hint, make),
        matchCountLabel = matchTemplate::withCount,
        allSectionLabel = stringResource(Res.string.onb_model_all),
        emptyResultsText = stringResource(Res.string.onb_model_empty),
        closeContentDescription = stringResource(Res.string.onb_cd_close),
        notListedLabel = stringResource(Res.string.onb_model_not_listed),
        notListedNamePlaceholder = stringResource(Res.string.onb_model_not_listed_name_placeholder),
        notListedConfirmLabel = stringResource(Res.string.onb_model_not_listed_confirm),
    )
}

/** S3: year chips with "Older", fuel chips, and "Save my car". Nothing is preselected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun YearFuelStep(state: SetupV2UiState, onEvent: (SetupV2Event) -> Unit) {
    val catalog = state.catalog.let { (it as? Loadable.Ready)?.value } ?: return
    val olderChosen = state.year != null && state.year !in catalog.recentYears
    OnboardingStepScaffold(
        step = SetupV2Step.YEAR_FUEL.position,
        totalSteps = SetupV2Step.TOTAL,
        primaryLabel = stringResource(Res.string.onb_save_car),
        onPrimary = { onEvent(SetupV2Event.SaveClicked) },
        primaryEnabled = state.canSave,
        primaryTag = OnboardingTestTags.SAVE_CAR,
        onBack = { onEvent(SetupV2Event.BackClicked) },
        footer = {
            OdoText(
                text = stringResource(Res.string.onb_save_car_helper),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        OdoEntrance(0) { CarSummaryCard(state) }
        OdoEntrance(1) { StepHeadline(title = stringResource(Res.string.onb_year_fuel_title), subtitle = null) }
        OdoEntrance(2) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                FieldLabel(stringResource(Res.string.onb_year_label).uppercase())
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    catalog.recentYears.forEach { year ->
                        OdoChip(
                            label = year.toString(),
                            selected = state.year == year,
                            onClick = { onEvent(SetupV2Event.YearChosen(year)) },
                            modifier = Modifier.testTag(OnboardingTestTags.yearChip(year)),
                        )
                    }
                    OdoChip(
                        label = if (olderChosen) state.year.toString() else stringResource(Res.string.onb_year_older),
                        selected = olderChosen,
                        onClick = { onEvent(SetupV2Event.OlderYearsOpened) },
                        modifier = Modifier.testTag(OnboardingTestTags.YEAR_OLDER),
                    )
                }
            }
        }
        OdoEntrance(3) {
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
                FieldLabel(stringResource(Res.string.onb_fuel_label).uppercase())
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    catalog.fuels.forEach { fuel ->
                        OdoChip(
                            label = fuelLabel(fuel),
                            selected = state.fuel == fuel,
                            onClick = { onEvent(SetupV2Event.FuelChosen(fuel)) },
                            modifier = Modifier.testTag(OnboardingTestTags.fuelChip(fuel.name)),
                        )
                    }
                }
            }
        }
    }
    if (state.olderOpen) {
        OdoModelYearSheet(
            onDismissRequest = { onEvent(SetupV2Event.OlderYearsDismissed) },
            selected = state.year,
            years = catalog.yearRange,
            onSelect = { onEvent(SetupV2Event.YearChosen(it, fromOlder = true)) },
            title = stringResource(Res.string.onb_year_sheet_title),
            subtitle = stringResource(Res.string.onb_year_sheet_subtitle),
            cancelLabel = stringResource(Res.string.onb_cancel),
            confirmLabel = stringResource(Res.string.onb_done),
        )
    }
}

/** After save: the insurance expiry month as chips over the next 12 months. Skippable. */
@Composable
private fun InsuranceStep(state: SetupV2UiState, onEvent: (SetupV2Event) -> Unit) {
    OnboardingStepScaffold(
        step = SetupV2Step.INSURANCE.position,
        totalSteps = SetupV2Step.TOTAL,
        primaryLabel = stringResource(Res.string.onb_insurance_confirm),
        onPrimary = { onEvent(SetupV2Event.InsuranceConfirmed) },
        primaryEnabled = state.insuranceMonth != null,
        primaryTag = OnboardingTestTags.INSURANCE_CONFIRM,
        onBack = { onEvent(SetupV2Event.BackClicked) },
        skipLabel = stringResource(Res.string.onb_insurance_skip),
        onSkip = { onEvent(SetupV2Event.InsuranceSkipped) },
        footer = {
            OdoText(
                text = stringResource(Res.string.onb_insurance_helper),
                style = OdoTheme.typography.bodySmall,
                color = OdoTheme.colors.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        OdoEntrance(0) { CarSummaryCard(state) }
        OdoEntrance(1) {
            StepHeadline(
                title = stringResource(Res.string.onb_insurance_title),
                subtitle = stringResource(Res.string.onb_insurance_subtitle),
            )
        }
        OdoEntrance(2) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                state.insuranceMonths.forEach { month ->
                    OdoChip(
                        label = monthLabel(month),
                        selected = state.insuranceMonth == month,
                        onClick = { onEvent(SetupV2Event.InsuranceMonthChosen(month)) },
                        modifier = Modifier.testTag(OnboardingTestTags.insuranceMonth(month.toString())),
                    )
                }
            }
        }
    }
}

/** "Nov 2026", in English like the rest of the app. */
private fun monthLabel(month: LocalDate): String =
    month.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } + " " + month.year

/** The car being built, filling in as the year and fuel are tapped. */
@Composable
private fun CarSummaryCard(state: SetupV2UiState) {
    val details = listOfNotNull(state.year?.toString(), state.fuel?.let { fuelLabel(it) })
    OdoCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(OdoTheme.spacing.md)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoMakeLogo(make = state.makeBadge(), width = BADGE_WIDTH, height = BADGE_HEIGHT)
            Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
                OdoText(
                    text = listOfNotNull(state.make, state.model?.displayName).joinToString(" "),
                    style = OdoTheme.typography.heading,
                    color = OdoTheme.colors.text,
                )
                val pending = stringResource(Res.string.onb_car_summary_pending)
                // Each answer rolls the line up into place, so the card visibly fills in.
                AnimatedContent(
                    targetState = details.joinToString(" · "),
                    transitionSpec = {
                        (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut())
                    },
                    label = "carSummary",
                ) { line ->
                    OdoText(
                        text = line.ifEmpty { pending },
                        style = OdoTheme.typography.bodySmall,
                        color = if (line.isEmpty()) OdoTheme.colors.textMuted else OdoTheme.colors.textDim,
                    )
                }
            }
        }
    }
}

/** The make as the design system's logo plate takes it. */
private fun SetupV2UiState.makeBadge() = OdoCarMake(id = make, name = make, logoUrl = makeLogoUrl)

private val BADGE_WIDTH = 64.dp
private val BADGE_HEIGHT = 40.dp

private val previewCatalog = SetupV2Catalog(
    makes = listOf("Hyundai"),
    models = listOf(
        CarModel("Creta"), CarModel("Creta", "SX"), CarModel("Venue"), CarModel("i20"),
        CarModel("Verna"), CarModel("Exter"),
    ),
    years = (2026 downTo 2005).toList(),
    fuels = FuelType.entries,
)

@OdoThemePreviews
@Composable
private fun ModelStepPreview() = OdoPreview(padded = false) {
    SetupV2Screen(SetupV2UiState(make = "Hyundai", catalog = Loadable.Ready(previewCatalog)), onEvent = {})
}

@OdoThemePreviews
@Composable
private fun YearFuelStepPreview() = OdoPreview(padded = false) {
    SetupV2Screen(
        SetupV2UiState(
            make = "Hyundai",
            step = SetupV2Step.YEAR_FUEL,
            catalog = Loadable.Ready(previewCatalog),
            model = CarModel("Creta"),
            year = 2022,
        ),
        onEvent = {},
    )
}
