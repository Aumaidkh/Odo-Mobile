package com.hopcape.odo.feature.questionnaire.firstrun.presentation.v2

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoModelYearSheet
import com.hopcape.odo.core.designsystem.component.OdoText
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
import com.hopcape.odo.feature.questionnaire.resources.onb_cd_close
import com.hopcape.odo.feature.questionnaire.resources.onb_done
import com.hopcape.odo.feature.questionnaire.resources.onb_fuel_label
import com.hopcape.odo.feature.questionnaire.resources.onb_match_count
import com.hopcape.odo.feature.questionnaire.resources.onb_model_all
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
    val motion = OdoTheme.motion
    AnimatedContent(
        targetState = state.step,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(motion.baseMillis, easing = motion.easeStandard)) togetherWith
                fadeOut(tween(motion.baseMillis / 2))
        },
        label = "setupV2Step",
    ) { step ->
        when (step) {
            SetupV2Step.MODEL -> ModelStep(state, onEvent)
            SetupV2Step.YEAR_FUEL -> YearFuelStep(state, onEvent)
        }
    }
}

/** S2: the make in the eyebrow, a grid of base models, and search over every trim. */
@Composable
private fun ModelStep(state: SetupV2UiState, onEvent: (SetupV2Event) -> Unit) {
    var searching by remember { mutableStateOf(false) }
    OnboardingStepScaffold(
        step = SetupV2Step.MODEL.position,
        totalSteps = SetupV2Step.TOTAL,
        primaryLabel = null,
        onPrimary = {},
        onBack = { onEvent(SetupV2Event.BackClicked) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OdoText(
                text = state.make.uppercase(),
                style = OdoTheme.typography.caption,
                color = OdoTheme.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            OdoButton(
                text = stringResource(Res.string.onb_model_change_make),
                onClick = { onEvent(SetupV2Event.ChangeMakeClicked) },
                variant = OdoButtonVariant.Tertiary,
                modifier = Modifier.testTag(OnboardingTestTags.CHANGE_MAKE),
            )
        }
        OdoText(
            text = stringResource(Res.string.onb_model_title),
            style = OdoTheme.typography.title,
            color = OdoTheme.colors.text,
        )
        when (val catalog = state.catalog) {
            Loadable.Loading -> CatalogLoadingCard()
            is Loadable.Failed -> CatalogFailedCard(catalog.message) { onEvent(SetupV2Event.CatalogRetried) }
            is Loadable.Ready -> {
                ModelGrid(catalog.value.baseModels) { onEvent(SetupV2Event.ModelChosen(it)) }
                SearchRow(
                    hint = stringResource(Res.string.onb_model_search_hint, state.make),
                    onClick = { searching = true },
                )
                if (searching) {
                    ModelSearchSheet(
                        make = state.make,
                        models = catalog.value.models,
                        onDismiss = { searching = false },
                        onSelect = { onEvent(SetupV2Event.ModelChosen(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelGrid(models: List<CarModel>, onChoose: (CarModel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        models.chunked(2).forEach { row ->
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

/** Looks like a search field; a tap opens the full searchable sheet. */
@Composable
private fun SearchRow(hint: String, onClick: () -> Unit) {
    OdoCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.MODEL_SEARCH),
        contentPadding = PaddingValues(OdoTheme.spacing.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OdoIcon(IcMagnifier, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
            OdoText(text = hint, style = OdoTheme.typography.body, color = OdoTheme.colors.textMuted)
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
    var olderOpen by remember { mutableStateOf(false) }
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
        StepHeadline(
            eyebrow = listOfNotNull(state.make, state.model?.displayName).joinToString(" ").uppercase(),
            title = stringResource(Res.string.onb_year_fuel_title),
            subtitle = null,
        )
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
                    onClick = { olderOpen = true },
                    modifier = Modifier.testTag(OnboardingTestTags.YEAR_OLDER),
                )
            }
        }
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
    if (olderOpen) {
        OdoModelYearSheet(
            onDismissRequest = { olderOpen = false },
            selected = state.year,
            years = catalog.yearRange,
            onSelect = { onEvent(SetupV2Event.YearChosen(it)) },
            title = stringResource(Res.string.onb_year_sheet_title),
            subtitle = stringResource(Res.string.onb_year_sheet_subtitle),
            cancelLabel = stringResource(Res.string.onb_cancel),
            confirmLabel = stringResource(Res.string.onb_done),
        )
    }
}

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
