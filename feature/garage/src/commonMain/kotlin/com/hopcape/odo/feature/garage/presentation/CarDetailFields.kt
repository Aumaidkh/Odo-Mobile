package com.hopcape.odo.feature.garage.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hopcape.odo.core.designsystem.component.OdoCarMake
import com.hopcape.odo.core.designsystem.component.OdoCarMakeField
import com.hopcape.odo.core.designsystem.component.OdoCarModel
import com.hopcape.odo.core.designsystem.component.OdoCarModelField
import com.hopcape.odo.core.designsystem.component.OdoFuelKind
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeField
import com.hopcape.odo.core.designsystem.component.OdoFuelTypeOption
import com.hopcape.odo.core.designsystem.component.OdoModelYearField
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.feature.garage.resources.Res
import com.hopcape.odo.feature.garage.resources.gr_cancel
import com.hopcape.odo.feature.garage.resources.gr_cd_close
import com.hopcape.odo.feature.garage.resources.gr_choose
import com.hopcape.odo.feature.garage.resources.gr_done
import com.hopcape.odo.feature.garage.resources.gr_field_fuel
import com.hopcape.odo.feature.garage.resources.gr_field_make
import com.hopcape.odo.feature.garage.resources.gr_field_model
import com.hopcape.odo.feature.garage.resources.gr_field_year
import com.hopcape.odo.feature.garage.resources.gr_fuel_cng
import com.hopcape.odo.feature.garage.resources.gr_fuel_diesel
import com.hopcape.odo.feature.garage.resources.gr_fuel_electric
import com.hopcape.odo.feature.garage.resources.gr_fuel_petrol
import com.hopcape.odo.feature.garage.resources.gr_fuel_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_fuel_sheet_title
import com.hopcape.odo.feature.garage.resources.gr_make_all
import com.hopcape.odo.feature.garage.resources.gr_make_empty
import com.hopcape.odo.feature.garage.resources.gr_make_popular
import com.hopcape.odo.feature.garage.resources.gr_make_search
import com.hopcape.odo.feature.garage.resources.gr_make_sheet_subtitle
import com.hopcape.odo.feature.garage.resources.gr_make_sheet_title
import com.hopcape.odo.feature.garage.resources.gr_match_count
import com.hopcape.odo.feature.garage.resources.gr_model_all
import com.hopcape.odo.feature.garage.resources.gr_model_empty
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
 * Both garage screens that describe a car use this one block: [EditCarScreen] and the
 * manual half of [AddCarScreen]. It deliberately mirrors the layout onboarding's manual
 * car-details step uses (make, model, then year + fuel paired on one row) so the two
 * surfaces feel like the same form — but it is built from the shared `:core:designsystem`
 * fields, not imported from `:feature:onboarding`, which features may never do.
 *
 * Stateless: renders the current selections and forwards changes.
 */
@Composable
internal fun CarDetailFields(
    make: OdoCarMake?,
    onMakeChange: (OdoCarMake) -> Unit,
    model: OdoCarModel?,
    onModelChange: (OdoCarModel) -> Unit,
    year: Int?,
    onYearChange: (Int) -> Unit,
    fuel: OdoFuelKind?,
    onFuelChange: (OdoFuelKind) -> Unit,
    modifier: Modifier = Modifier,
    makes: List<OdoCarMake> = sampleMakes,
    popularMakes: List<OdoCarMake> = samplePopularMakes,
    models: List<OdoCarModel> = sampleModels(make),
    years: IntRange = sampleYears,
) {
    val choose = stringResource(Res.string.gr_choose)
    val close = stringResource(Res.string.gr_cd_close)
    val matchTemplate = stringResource(Res.string.gr_match_count)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.gr_field_make))
            OdoCarMakeField(
                selected = make,
                makes = makes,
                popular = popularMakes,
                onSelect = onMakeChange,
                title = stringResource(Res.string.gr_make_sheet_title),
                subtitle = stringResource(Res.string.gr_make_sheet_subtitle, makes.size),
                searchPlaceholder = stringResource(Res.string.gr_make_search),
                matchCountLabel = { matchTemplate.withCount(it) },
                popularSectionLabel = stringResource(Res.string.gr_make_popular),
                allSectionLabel = stringResource(Res.string.gr_make_all),
                emptyResultsText = stringResource(Res.string.gr_make_empty),
                closeContentDescription = close,
                placeholder = choose,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
            FieldLabel(stringResource(Res.string.gr_field_model))
            OdoCarModelField(
                selected = model,
                models = models,
                onSelect = onModelChange,
                title = stringResource(Res.string.gr_model_sheet_title),
                subtitle = stringResource(Res.string.gr_model_sheet_subtitle, make?.name ?: choose),
                searchPlaceholder = stringResource(Res.string.gr_model_search),
                matchCountLabel = { matchTemplate.withCount(it) },
                allSectionLabel = stringResource(Res.string.gr_model_all),
                emptyResultsText = stringResource(Res.string.gr_model_empty),
                closeContentDescription = close,
                placeholder = choose,
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
                    selected = year,
                    years = years,
                    onSelect = onYearChange,
                    title = stringResource(Res.string.gr_year_sheet_title),
                    cancelLabel = stringResource(Res.string.gr_cancel),
                    confirmLabel = stringResource(Res.string.gr_done),
                    subtitle = stringResource(Res.string.gr_year_sheet_subtitle),
                    placeholder = choose,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                FieldLabel(stringResource(Res.string.gr_field_fuel))
                OdoFuelTypeField(
                    selected = fuel,
                    options = fuelOptions(),
                    onSelect = onFuelChange,
                    title = stringResource(Res.string.gr_fuel_sheet_title),
                    subtitle = stringResource(Res.string.gr_fuel_sheet_subtitle),
                    closeContentDescription = close,
                    placeholder = choose,
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
 * Fuel rows without rate lines: the design system never invents a ₹/L figure, and the
 * garage has no fuel-price source wired yet, so the subtitle stays absent rather than
 * showing a guess.
 */
@Composable
internal fun fuelOptions(): List<OdoFuelTypeOption> = listOf(
    OdoFuelTypeOption(OdoFuelKind.PETROL, stringResource(Res.string.gr_fuel_petrol)),
    OdoFuelTypeOption(OdoFuelKind.DIESEL, stringResource(Res.string.gr_fuel_diesel)),
    OdoFuelTypeOption(OdoFuelKind.CNG, stringResource(Res.string.gr_fuel_cng)),
    OdoFuelTypeOption(OdoFuelKind.ELECTRIC, stringResource(Res.string.gr_fuel_electric)),
)

/** Fills the `%1$d` in a "N MATCHES" template — the picker hands us the count. */
private fun String.withCount(count: Int): String = replace("%1\$d", count.toString())

// --- Sample catalog --------------------------------------------------------------
// Stand-ins until the screens read the `:core:domain` VehicleCatalog port through a
// ViewModel; the ids match the names, exactly as onboarding's mapper does today.

private fun make(name: String) = OdoCarMake(id = name, name = name)

internal val sampleMakes: List<OdoCarMake> = listOf(
    "Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Honda",
    "Toyota", "Kia", "Volkswagen", "Renault", "Skoda",
).map(::make)

internal val samplePopularMakes: List<OdoCarMake> = sampleMakes.take(4)

internal val sampleYears: IntRange = 2005..2026

internal fun sampleModels(make: OdoCarMake?): List<OdoCarModel> = when (make?.name) {
    "Maruti Suzuki" -> listOf(
        OdoCarModel(id = "swift", name = "Swift", variant = "VXI"),
        OdoCarModel(id = "baleno", name = "Baleno", variant = "Zeta"),
        OdoCarModel(id = "brezza", name = "Brezza", variant = "ZXI"),
    )
    "Hyundai" -> listOf(
        OdoCarModel(id = "i20", name = "i20", variant = "Asta"),
        OdoCarModel(id = "creta", name = "Creta", variant = "SX"),
        OdoCarModel(id = "venue", name = "Venue", variant = "S"),
    )
    "Tata" -> listOf(
        OdoCarModel(id = "nexon", name = "Nexon", variant = "XZ+"),
        OdoCarModel(id = "punch", name = "Punch", variant = "Accomplished"),
    )
    null -> emptyList()
    else -> listOf(OdoCarModel(id = "other", name = "Other"))
}
