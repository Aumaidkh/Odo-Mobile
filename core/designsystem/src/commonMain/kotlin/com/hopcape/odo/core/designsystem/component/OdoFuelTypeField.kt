package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.icons.IcCheck
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcFuelPump
import com.hopcape.odo.core.designsystem.icons.IcFuelPumpDiesel
import com.hopcape.odo.core.designsystem.icons.IcInfinity
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcLightning
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.coroutines.launch

/**
 * The fuels [OdoFuelTypeField] can draw. This is a **visual** vocabulary, not a
 * domain enum — it only decides which glyph an option gets. Features map their own
 * `FuelType` onto it, which also keeps a UI-only kind (a future hybrid, say) from
 * leaking into what gets persisted.
 */
enum class OdoFuelKind { PETROL, DIESEL, ELECTRIC, CNG }

/**
 * One row in the [OdoFuelTypeField] sheet: what it is, what it's called, and an
 * optional rate line under the name.
 *
 * @param label the fuel's name, in the owner's language ("Petrol", "CNG").
 * @param subtitle the running-cost hint shown beneath it — e.g. "≈ ₹106 / L in
 *   Pune". Copy **and** the number come from the caller (the design system never
 *   invents a rate); pass `null` until a real fuel price is known rather than
 *   showing a guess.
 */
@Immutable
data class OdoFuelTypeOption(
    val kind: OdoFuelKind,
    val label: String,
    val subtitle: String? = null,
)

/**
 * The **fuel type** picker — a collapsed field showing the chosen fuel, which opens
 * a bottom sheet of large, tappable fuel cards. Fuel is one of onboarding's few
 * required answers and it sets the owner's ₹/km estimate, so it gets a sheet with
 * per-fuel rate lines rather than a cramped dropdown.
 *
 * Selecting a card commits immediately and closes the sheet — there is no confirm
 * button, so a wrong tap costs one more tap, not a trip through a dialog.
 *
 * Copy-free like the rest of the design system: every string is a parameter.
 *
 * ```
 * OdoFuelTypeField(
 *     selected = form.fuelType?.toFuelKind(),
 *     options = state.fuelOptions,          // label + optional "≈ ₹106 / L in Pune"
 *     onSelect = { onEvent(FuelTypeChanged(it.toDomain())) },
 *     title = stringResource(Res.string.onb_fuel_sheet_title),
 *     subtitle = stringResource(Res.string.onb_fuel_sheet_subtitle),
 *     closeContentDescription = stringResource(Res.string.onb_fuel_sheet_close),
 *     label = stringResource(Res.string.onb_label_fuel),
 *     placeholder = stringResource(Res.string.onb_fuel_placeholder),
 *     note = stringResource(Res.string.onb_fuel_sheet_note),
 *     errorText = form.fuelType.error?.asString(),
 * )
 * ```
 *
 * @param title / [subtitle] the sheet's header.
 * @param closeContentDescription screen-reader label for the sheet's close button.
 * @param label the field label above the collapsed field.
 * @param placeholder shown in the collapsed field until a fuel is chosen.
 * @param note the muted footnote under the options (e.g. that rates refresh
 *   themselves and the choice can be changed later).
 * @param errorText when non-null, the field turns danger-coloured and this shows
 *   below it, replacing [helperText] (same rule as [OdoInputField]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoFuelTypeField(
    selected: OdoFuelKind?,
    options: List<OdoFuelTypeOption>,
    onSelect: (OdoFuelKind) -> Unit,
    title: String,
    subtitle: String,
    closeContentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    note: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val isError = errorText != null

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        if (label != null) {
            OdoText(
                text = label,
                style = OdoTheme.typography.label,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
        CollapsedFuelField(
            text = options.firstOrNull { it.kind == selected }?.label,
            placeholder = placeholder,
            isError = isError,
            enabled = enabled,
            onClick = { open = true },
        )
        // Error replaces helper — never both, matching OdoInputField.
        val supporting = errorText ?: helperText
        if (supporting != null) {
            OdoText(
                text = supporting,
                style = OdoTheme.typography.bodySmall,
                color = if (isError) OdoTheme.colors.danger else OdoTheme.colors.textDim,
            )
        }
    }

    if (open) {
        // Animate the sheet out before dropping it, so a selection doesn't make the
        // sheet vanish mid-gesture.
        val dismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { open = false } }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = OdoTheme.colors.surface,
        ) {
            FuelTypeSheet(
                selected = selected,
                options = options,
                title = title,
                subtitle = subtitle,
                note = note,
                closeContentDescription = closeContentDescription,
                onSelect = { kind ->
                    onSelect(kind)
                    dismiss()
                },
                onClose = { dismiss() },
            )
        }
    }
}

/* ------------------------------ Collapsed field ------------------------------ */

@Composable
private fun CollapsedFuelField(
    text: String?,
    placeholder: String?,
    isError: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .clip(OdoTheme.shapes.field)
            .background(colors.surface)
            .border(1.dp, if (isError) colors.danger else colors.border, OdoTheme.shapes.field)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.lg, vertical = OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        OdoText(
            text = text ?: placeholder.orEmpty(),
            style = OdoTheme.typography.heading,
            color = when {
                text == null -> colors.textMuted
                enabled -> colors.text
                else -> colors.textMuted
            },
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        DownChevron()
    }
}

/** Down-chevron drawn in-house (no Material-icons dep), matching [OdoDropdownField]. */
@Composable
private fun DownChevron() {
    val color = OdoTheme.colors.textDim
    Canvas(Modifier.size(OdoTheme.iconSizes.medium)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(w * 0.32f, h * 0.42f), Offset(w * 0.5f, h * 0.60f), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.60f), Offset(w * 0.68f, h * 0.42f), stroke, cap = StrokeCap.Round)
    }
}

/* ------------------------------ Sheet ------------------------------ */

@Composable
private fun FuelTypeSheet(
    selected: OdoFuelKind?,
    options: List<OdoFuelTypeOption>,
    title: String,
    subtitle: String,
    note: String?,
    closeContentDescription: String,
    onSelect: (OdoFuelKind) -> Unit,
    onClose: () -> Unit,
) {
    val colors = OdoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
            ) {
                OdoText(title, style = OdoTheme.typography.title, color = colors.text)
                OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = colors.textDim)
            }
            OdoIconButton(
                imageVector = IcClose,
                contentDescription = closeContentDescription,
                onClick = onClose,
                tint = colors.textDim,
                size = OdoTheme.iconSizes.medium,
            )
        }

        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
        ) {
            options.forEach { option ->
                FuelOptionRow(
                    option = option,
                    selected = option.kind == selected,
                    onClick = { onSelect(option.kind) },
                )
            }
        }

        if (note != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoIcon(IcInfo, contentDescription = null, tint = colors.textMuted, size = OdoTheme.iconSizes.small)
                OdoText(note, style = OdoTheme.typography.bodySmall, color = colors.textMuted)
            }
        }
    }
}

/** One fuel card: glyph tile, name + rate line, and a check once it's the chosen one. */
@Composable
private fun FuelOptionRow(
    option: OdoFuelTypeOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    val shape = OdoTheme.shapes.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) colors.accent.copy(alpha = SelectedFill) else colors.surfaceRaised)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.accent else colors.border,
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(OdoTheme.spacing.md),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FuelGlyphTile(kind = option.kind, selected = selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            OdoText(option.label, style = OdoTheme.typography.heading, color = colors.text, maxLines = 1)
            if (option.subtitle != null) {
                OdoText(
                    option.subtitle,
                    style = OdoTheme.typography.bodySmall,
                    color = colors.textDim,
                    maxLines = 1,
                )
            }
        }
        if (selected) {
            OdoIcon(
                IcCheck,
                contentDescription = null,
                tint = colors.accent,
                size = OdoTheme.iconSizes.large,
            )
        }
    }
}

/** The rounded glyph tile at the head of a fuel card; fills with accent when chosen. */
@Composable
private fun FuelGlyphTile(kind: OdoFuelKind, selected: Boolean) {
    val colors = OdoTheme.colors
    Box(
        modifier = Modifier
            .size(GlyphTileSize)
            .clip(OdoTheme.shapes.small)
            .background(if (selected) colors.accent.copy(alpha = SelectedGlyphFill) else colors.surface)
            .border(1.dp, if (selected) colors.accent.copy(alpha = SelectedFill) else colors.border, OdoTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        OdoIcon(
            imageVector = kind.glyph(),
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textDim,
            size = OdoTheme.iconSizes.large,
        )
    }
}

/** The design system's glyph for each fuel — the one place the mapping lives. */
private fun OdoFuelKind.glyph(): ImageVector = when (this) {
    OdoFuelKind.PETROL -> IcFuelPump
    OdoFuelKind.DIESEL -> IcFuelPumpDiesel
    OdoFuelKind.ELECTRIC -> IcLightning
    OdoFuelKind.CNG -> IcInfinity
}

private val GlyphTileSize = 44.dp

/** Accent tints for the selected card and its glyph tile — a wash, not a fill. */
private const val SelectedFill = 0.10f
private const val SelectedGlyphFill = 0.18f

@OdoThemePreviews
@Composable
private fun OdoFuelTypeFieldPreview() = OdoPreview {
    var fuel by remember { mutableStateOf<OdoFuelKind?>(OdoFuelKind.PETROL) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoFuelTypeField(
            selected = fuel,
            options = previewFuelOptions,
            onSelect = { fuel = it },
            title = "Fuel type",
            subtitle = "Sets your ₹/km fuel estimate",
            closeContentDescription = "Close",
            label = "Fuel type",
            placeholder = "Choose",
        )
        OdoFuelTypeField(
            selected = null,
            options = previewFuelOptions,
            onSelect = {},
            title = "Fuel type",
            subtitle = "Sets your ₹/km fuel estimate",
            closeContentDescription = "Close",
            placeholder = "Choose",
            errorText = "Fuel type select karein",
        )
    }
}

/** Previews the sheet body on its own — the real sheet can't render in a preview. */
@OdoThemePreviews
@Composable
private fun OdoFuelTypeSheetPreview() = OdoPreview(padded = false) {
    FuelTypeSheet(
        selected = OdoFuelKind.PETROL,
        options = previewFuelOptions,
        title = "Fuel type",
        subtitle = "Sets your ₹/km fuel estimate",
        note = "Rates update automatically — you can change this later.",
        closeContentDescription = "Close",
        onSelect = {},
        onClose = {},
    )
}

private val previewFuelOptions = listOf(
    OdoFuelTypeOption(OdoFuelKind.PETROL, "Petrol", "≈ ₹106 / L in Pune"),
    OdoFuelTypeOption(OdoFuelKind.DIESEL, "Diesel", "≈ ₹93 / L in Pune"),
    OdoFuelTypeOption(OdoFuelKind.ELECTRIC, "Electric", "≈ ₹8 / kWh home charging"),
    OdoFuelTypeOption(OdoFuelKind.CNG, "CNG", "≈ ₹89 / kg in Pune"),
)
