package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeField
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelField
import com.hopcape.odo.core.designsystem.component.OdoModelYearField
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.car.catalog.CarModel
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.feature.garage.presentation.components.fuelOptions
import com.hopcape.odo.feature.garage.presentation.components.toDomain
import com.hopcape.odo.feature.garage.presentation.components.toFuelKind
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_cancel
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_choose
import com.hopcape.odo.feature.garage.resources.gr_done
import com.hopcape.odo.feature.garage.resources.gr_field_fuel
import com.hopcape.odo.feature.garage.resources.gr_field_make
import com.hopcape.odo.feature.garage.resources.gr_field_model
import com.hopcape.odo.feature.garage.resources.gr_field_year
import com.hopcape.odo.feature.garage.resources.gr_fuel_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_fuel_sheet_title
import com.hopcape.odo.feature.garage.resources.gr_make_all
import com.hopcape.odo.feature.garage.resources.gr_make_empty
import com.hopcape.odo.feature.garage.resources.gr_make_not_listed
import com.hopcape.odo.feature.garage.resources.gr_make_not_listed_confirm
import com.hopcape.odo.feature.garage.resources.gr_make_not_listed_placeholder
import com.hopcape.odo.feature.garage.resources.gr_make_popular
import com.hopcape.odo.feature.garage.resources.gr_make_search
import com.hopcape.odo.feature.garage.resources.gr_make_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_make_sheet_title
import com.hopcape.odo.feature.garage.resources.gr_match_count
import com.hopcape.odo.feature.garage.resources.gr_model_all
import com.hopcape.odo.feature.garage.resources.gr_model_empty
import com.hopcape.odo.feature.garage.resources.gr_model_not_listed
import com.hopcape.odo.feature.garage.resources.gr_model_not_listed_confirm
import com.hopcape.odo.feature.garage.resources.gr_model_not_listed_name_placeholder
import com.hopcape.odo.feature.garage.resources.gr_model_not_listed_variant_placeholder
import com.hopcape.odo.feature.garage.resources.gr_model_search
import com.hopcape.odo.feature.garage.resources.gr_model_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_model_sheet_title
import com.hopcape.odo.feature.garage.resources.gr_year_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_year_sheet_title
import org.jetbrains.compose.resources.stringResource

/**
 * The four answers that describe a car — make, model, year, fuel — as the design system's
 * pickers rather than free-text fields or dropdowns.
 *
 * Both garage screens that describe a car use this one block: the edit screen and the
 * add-car screen. It deliberately mirrors the layout onboarding's car-details step uses
 * (make, model, then year and fuel paired on one row) so the two surfaces feel like the
 * same form — but it is built from the shared `:core:designsystem` fields, not imported
 * from `:feature:onboarding`, which features may never do.
 *
 * Stateless: it renders [fields] against what [options] offers, and forwards changes.
 */
@Composable
internal fun CarDetailFields(
    fields: CarFormFields,
    options: CarFormOptions,
    onMakeSelected: (String) -> Unit,
    onModelSelected: (CarModel) -> Unit,
    onYearSelected: (Int) -> Unit,
    onFuelSelected: (FuelType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val choose = stringResource(Res.string.gr_choose)
    val close = stringResource(Res.string.gr_cd_close)
    val matchTemplate = stringResource(Res.string.gr_match_count)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.gr_field_make))
            OdoCarMakeField(
                modifier = Modifier.testTag(GarageTestTags.MAKE_FIELD),
                selected = fields.make.value?.toOdoCarMake(),
                makes = options.makes.map { it.toOdoCarMake() },
                popular = options.popularMakes.map { it.toOdoCarMake() },
                onSelect = { onMakeSelected(it.name) },
                title = stringResource(Res.string.gr_make_sheet_title),
                subtitle = stringResource(Res.string.gr_make_sheet_subtitle, options.makes.size),
                searchPlaceholder = stringResource(Res.string.gr_make_search),
                matchCountLabel = { matchTemplate.withCount(it) },
                popularSectionLabel = stringResource(Res.string.gr_make_popular),
                allSectionLabel = stringResource(Res.string.gr_make_all),
                emptyResultsText = stringResource(Res.string.gr_make_empty),
                closeContentDescription = close,
                placeholder = choose,
                errorText = fields.make.error?.asString(),
                notListedLabel = stringResource(Res.string.gr_make_not_listed),
                notListedPlaceholder = stringResource(Res.string.gr_make_not_listed_placeholder),
                notListedConfirmLabel = stringResource(Res.string.gr_make_not_listed_confirm),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.gr_field_model))
            OdoCarModelField(
                modifier = Modifier.testTag(GarageTestTags.MODEL_FIELD),
                selected = fields.model.value?.toOdoCarModel(),
                models = options.models.map { it.toOdoCarModel() },
                onSelect = { onModelSelected(it.toDomainModel()) },
                title = stringResource(Res.string.gr_model_sheet_title),
                subtitle = stringResource(Res.string.gr_model_sheet_subtitle, fields.make.value ?: choose),
                searchPlaceholder = stringResource(Res.string.gr_model_search),
                matchCountLabel = { matchTemplate.withCount(it) },
                allSectionLabel = stringResource(Res.string.gr_model_all),
                emptyResultsText = stringResource(Res.string.gr_model_empty),
                closeContentDescription = close,
                placeholder = choose,
                errorText = fields.model.error?.asString(),
                notListedLabel = stringResource(Res.string.gr_model_not_listed),
                notListedNamePlaceholder = stringResource(Res.string.gr_model_not_listed_name_placeholder),
                notListedVariantPlaceholder = stringResource(Res.string.gr_model_not_listed_variant_placeholder),
                notListedConfirmLabel = stringResource(Res.string.gr_model_not_listed_confirm),
            )
        }

        // Year and fuel are both one-tap answers, so they share a row.
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.gr_field_year))
                OdoModelYearField(
                    modifier = Modifier.testTag(GarageTestTags.YEAR_FIELD),
                    selected = fields.year.value,
                    years = options.years.asRange(),
                    onSelect = onYearSelected,
                    title = stringResource(Res.string.gr_year_sheet_title),
                    cancelLabel = stringResource(Res.string.gr_cancel),
                    confirmLabel = stringResource(Res.string.gr_done),
                    subtitle = stringResource(Res.string.gr_year_sheet_subtitle),
                    placeholder = choose,
                    errorText = fields.year.error?.asString(),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.gr_field_fuel))
                OdoFuelTypeField(
                    modifier = Modifier.testTag(GarageTestTags.FUEL_FIELD),
                    selected = fields.fuel.value?.toFuelKind(),
                    options = fuelOptions(options.fuelTypes),
                    onSelect = { onFuelSelected(it.toDomain()) },
                    title = stringResource(Res.string.gr_fuel_sheet_title),
                    subtitle = stringResource(Res.string.gr_fuel_sheet_subtitle),
                    closeContentDescription = close,
                    placeholder = choose,
                    errorText = fields.fuel.error?.asString(),
                )
            }
        }
    }
}

/** The small caption above a field. */
@Composable
internal fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    OdoText(text, style = OdoTheme.typography.label, color = OdoTheme.colors.textDim, modifier = modifier)
}

/**
 * Catalog make → picker row. The catalog is a flat list of brand names, so the id is the
 * name; when it grows real ids, only this mapper changes.
 */
private fun String.toOdoCarMake(): OdoCarMake = OdoCarMake(id = this, name = this)

/**
 * Domain model ⇄ picker row. The display name doubles as the row id: a model is offered
 * both with and without a trim ("Swift", "Swift VXI"), and those read differently, so they
 * key differently too.
 */
private fun CarModel.toOdoCarModel(): OdoCarModel =
    OdoCarModel(id = displayName, name = name, variant = variant)

private fun OdoCarModel.toDomainModel(): CarModel = CarModel(name = name, variant = variant)

/**
 * The catalog's years as the wheel picker's range. Empty until the catalog has loaded, and
 * the picker needs *some* range to lay out, so it falls back to a single year rather than
 * an invalid one.
 */
private fun List<Int>.asRange(): IntRange {
    val min = minOrNull() ?: return FALLBACK_YEAR..FALLBACK_YEAR
    return min..(maxOrNull() ?: min)
}

/** Stands in only while the catalog is still loading; nothing is saved from it. */
private const val FALLBACK_YEAR = 2000

/** Fills the `%1$d` in a "N MATCHES" template — the picker hands us the count. */
private fun String.withCount(count: Int): String = replace("%1\$d", count.toString())
