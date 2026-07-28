package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import kotlinx.coroutines.launch

/**
 * The **model year** picker — a collapsed field showing the chosen year, which opens
 * a bottom sheet with an [OdoWheelPicker] spun over the model's production years. A
 * car's year is an ordered pick from a long run of values, so it gets an iOS-style
 * wheel (Cancel / Done, tinted centre band) rather than a cramped dropdown.
 *
 * Unlike the tap-to-commit fuel/make sheets, the wheel **stages** the choice: spinning
 * only moves a draft, [confirmLabel] commits it via [onSelect], and Cancel or a
 * swipe-down leaves [selected] untouched — a wheel has no discrete "tap" to commit on.
 *
 * The year set is the caller's: pass the make/model's production range as [years]
 * (e.g. `2014..2027`); it renders newest-first. Copy-free like the rest of the design
 * system — every string, including the range in [hint], is a parameter.
 *
 * ```
 * OdoModelYearField(
 *     selected = form.modelYear,
 *     years = state.modelYearRange,                       // e.g. 2014..2027
 *     onSelect = { onEvent(ModelYearChanged(it)) },
 *     title = stringResource(Res.string.onb_year_sheet_title),
 *     cancelLabel = stringResource(Res.string.onb_year_cancel),
 *     confirmLabel = stringResource(Res.string.onb_year_done),
 *     subtitle = state.selectedCarLabel,                  // "Honda City"
 *     hint = stringResource(Res.string.onb_year_hint, years.first, years.last),
 *     label = stringResource(Res.string.onb_label_year),
 *     placeholder = stringResource(Res.string.onb_year_placeholder),
 *     errorText = form.modelYear.error?.asString(),
 * )
 * ```
 *
 * @param selected the currently chosen year, or `null` when nothing is picked yet.
 * @param years the model's production years; rendered newest-first in the wheel.
 * @param onSelect fired only when [confirmLabel] is tapped, with the settled year.
 * @param title the sheet's centred header (e.g. "Model year").
 * @param cancelLabel the header's discard action, on the leading side.
 * @param confirmLabel the header's commit action, on the trailing side (accent).
 * @param subtitle a muted line under [title] — typically the selected car
 *   ("Honda City"); pass `null` to omit it.
 * @param hint the muted footnote under the wheel (e.g. "Swipe to change · 2014 –
 *   2027"); the range numbers are the caller's to format. `null` hides it.
 * @param label the field label above the collapsed field.
 * @param placeholder shown in the collapsed field until a year is chosen.
 * @param helperText muted support line under the field when there's no error.
 * @param errorText when non-null, the field turns danger-coloured and this shows
 *   below it, replacing [helperText] (same rule as [OdoInputField]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoModelYearField(
    selected: Int?,
    years: IntRange,
    onSelect: (Int) -> Unit,
    title: String,
    cancelLabel: String,
    confirmLabel: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    hint: String? = null,
    label: String? = null,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
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
        CollapsedYearField(
            text = selected?.toString(),
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

    if (open && !years.isEmpty()) {
        // Animate the sheet out before dropping it, so committing doesn't make the
        // sheet vanish mid-gesture.
        val dismiss = { scope.launch { sheetState.hide() }.invokeOnCompletion { open = false } }
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = OdoTheme.colors.surface,
        ) {
            ModelYearSheet(
                selected = selected,
                years = years,
                title = title,
                subtitle = subtitle,
                hint = hint,
                cancelLabel = cancelLabel,
                confirmLabel = confirmLabel,
                onConfirm = { year ->
                    onSelect(year)
                    dismiss()
                },
                onCancel = { dismiss() },
            )
        }
    }
}

/* ------------------------------ Collapsed field ------------------------------ */

@Composable
private fun CollapsedYearField(
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
        Chevron(pointsUp = false)
    }
}

/* ------------------------------ Sheet ------------------------------ */

@Composable
private fun ModelYearSheet(
    selected: Int?,
    years: IntRange,
    title: String,
    subtitle: String?,
    hint: String?,
    cancelLabel: String,
    confirmLabel: String,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    val colors = OdoTheme.colors
    // Newest first, so the latest model year sits at the top of the wheel.
    val items = remember(years) { years.reversed().toList() }
    val startIndex = items.indexOf(selected).coerceAtLeast(0)
    var pendingIndex by remember { mutableStateOf(startIndex) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        // Cancel · title/subtitle · Done — the iOS action-sheet header.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderAction(text = cancelLabel, color = colors.textDim, onClick = onCancel)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                OdoText(title, style = OdoTheme.typography.heading, color = colors.text, maxLines = 1)
                if (subtitle != null) {
                    OdoText(
                        subtitle,
                        style = OdoTheme.typography.bodySmall,
                        color = colors.textDim,
                        maxLines = 1,
                    )
                }
            }
            HeaderAction(text = confirmLabel, color = colors.accent, onClick = { onConfirm(items[pendingIndex]) })
        }

        OdoWheelPicker(
            items = items,
            selectedIndex = startIndex,
            onSelectedIndexChange = { pendingIndex = it },
            itemHeight = 52.dp,
            selectionColor = colors.accent.copy(alpha = SelectionFill),
            selectionBorder = BorderStroke(1.5.dp, colors.accent.copy(alpha = SelectionStroke)),
            textStyle = OdoTheme.typography.title,
            modifier = Modifier.fillMaxWidth(),
        )

        if (hint != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OdoText(hint, style = OdoTheme.typography.bodySmall, color = colors.textMuted, maxLines = 1)
                Chevron(pointsUp = false)
            }
        }
    }
}

/** A borderless text action in the sheet header, sized to a comfortable tap target. */
@Composable
private fun HeaderAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    OdoText(
        text = text,
        style = OdoTheme.typography.label,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(OdoTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OdoTheme.spacing.minTouchTarget)
            .padding(vertical = OdoTheme.spacing.sm),
    )
}

/** Small chevron drawn in-house (no Material-icons dep), matching the other fields. */
@Composable
private fun Chevron(pointsUp: Boolean) {
    val color = OdoTheme.colors.textDim
    Canvas(Modifier.size(OdoTheme.iconSizes.medium)) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val near = if (pointsUp) h * 0.58f else h * 0.42f
        val far = if (pointsUp) h * 0.40f else h * 0.60f
        drawLine(color, Offset(w * 0.32f, near), Offset(w * 0.5f, far), stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, far), Offset(w * 0.68f, near), stroke, cap = StrokeCap.Round)
    }
}

/** Accent tints for the wheel's centre band — a wash + soft ring, not a hard fill. */
private const val SelectionFill = 0.08f
private const val SelectionStroke = 0.45f

@OdoThemePreviews
@Composable
private fun OdoModelYearFieldPreview() = OdoPreview {
    var year by remember { mutableStateOf<Int?>(2026) }
    Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg)) {
        OdoModelYearField(
            selected = year,
            years = 2014..2027,
            onSelect = { year = it },
            title = "Model year",
            cancelLabel = "Cancel",
            confirmLabel = "Done",
            subtitle = "Honda City",
            hint = "Swipe to change · 2014 – 2027",
            label = "Model year",
            placeholder = "Choose",
        )
        OdoModelYearField(
            selected = null,
            years = 2014..2027,
            onSelect = {},
            title = "Model year",
            cancelLabel = "Cancel",
            confirmLabel = "Done",
            subtitle = "Honda City",
            placeholder = "Choose",
            errorText = "Model year select karein",
        )
    }
}

/** Previews the sheet body on its own — the real sheet can't render in a preview. */
@OdoThemePreviews
@Composable
private fun OdoModelYearSheetPreview() = OdoPreview(padded = false) {
    ModelYearSheet(
        selected = 2026,
        years = 2014..2027,
        title = "Model year",
        subtitle = "Honda City",
        hint = "Swipe to change · 2014 – 2027",
        cancelLabel = "Cancel",
        confirmLabel = "Done",
        onConfirm = {},
        onCancel = {},
    )
}
