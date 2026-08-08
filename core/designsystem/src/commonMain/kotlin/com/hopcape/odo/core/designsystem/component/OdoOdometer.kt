package com.hopcape.odo.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopcape.odo.core.designsystem.icons.IcCamera
import com.hopcape.odo.core.designsystem.icons.IcChevronRight
import com.hopcape.odo.core.designsystem.icons.IcInfo
import com.hopcape.odo.core.designsystem.icons.IcSpeedometer
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import kotlinx.coroutines.launch

/**
 * The **odometer** input — a split-flap reading typed on the phone's number pad, not a
 * bare number field. A collapsed surface shows the current reading as mechanical digit
 * drums; tapping opens a bottom-sheet editor with the big drums and quick-add chips, and
 * the system numeric keyboard comes up for typing.
 * Odometer is Odo's single most important number (it powers per-km cost, the health score,
 * and km-anomaly checks), so it earns a first-class control.
 *
 * The unit is the owner's setting, not a control on this component: it defaults to whatever
 * the app provided, and a screen only passes one when it has a reason to.
 *
 * Distinct from [OdoOdometerField] (the compact inline text input the service-log form
 * uses) — this is the richer, hero capture surface for onboarding and quick edits.
 *
 * Copy-free like the rest of the design system: every label is a parameter so
 * localisation stays in the feature. [value] and [onValueChange] are in whole units of
 * [unit]; [digits] bounds the drum count (default 6 → up to 999,999).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OdoOdometer(
    value: Long?,
    onValueChange: (Long) -> Unit,
    title: String,
    subtitle: String,
    odometerLabel: String,
    saveLabel: String,
    kmLabel: String,
    milesLabel: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    hint: String? = null,
    unit: OdoDistanceUnit = LocalOdoDistanceFormat.current.unit,
    note: String? = null,
    quickAdds: List<Int> = listOf(100, 500, 1000),
    scanLabel: String? = null,
    scanBadge: String? = null,
    digits: Int = 6,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
        CollapsedOdometer(
            value = value,
            unit = unit,
            kmLabel = kmLabel,
            milesLabel = milesLabel,
            hint = hint,
            digits = digits,
            enabled = enabled,
            onClick = { open = true },
        )
        if (helper != null) {
            OdoText(helper, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        }
    }

    if (open) {
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = sheetState,
            containerColor = OdoTheme.colors.surface,
        ) {
            OdoOdometerEditor(
                value = value,
                onSave = { reading ->
                    onValueChange(reading)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { open = false }
                },
                title = title,
                subtitle = subtitle,
                odometerLabel = odometerLabel,
                saveLabel = saveLabel,
                kmLabel = kmLabel,
                milesLabel = milesLabel,
                unit = unit,
                note = note,
                quickAdds = quickAdds,
                scanLabel = scanLabel,
                scanBadge = scanBadge,
                digits = digits,
            )
        }
    }
}

/**
 * The odometer **editor on its own** — the drums and quick-add chips, without the
 * collapsed surface or a sheet around it. Typing happens on the system numeric keyboard,
 * which is asked for as soon as the editor appears; tapping the drums brings it back if
 * it was dismissed.
 *
 * [OdoOdometer] renders this inside its own [ModalBottomSheet]; this entry point exists
 * for the callers that already own the sheet — a Nav3 bottom-sheet destination, say — so
 * the reading is captured by the same control everywhere instead of a plain number field
 * standing in for it.
 *
 * Stateful in one narrow sense: the in-progress entry is held here (it is transient
 * until saved), seeded from [value] and reset whenever [value] changes. [onSave] fires with
 * the finished reading in whole units of the displayed unit — the caller converts it back to
 * kilometres — and nothing is reported while the owner is still typing.
 *
 * @param footer rendered between the note and the save button — the slot for caller
 *   context the design system can't know, e.g. the last recorded reading or the distance
 *   driven since. It is handed the reading currently dialled in (null while the drums are
 *   empty), so a caller can show how far that is from where the car was last read. Keep it
 *   short; the keyboard takes the bottom of the screen already.
 */
@Composable
fun OdoOdometerEditor(
    value: Long?,
    onSave: (Long) -> Unit,
    title: String,
    subtitle: String,
    odometerLabel: String,
    saveLabel: String,
    kmLabel: String,
    milesLabel: String,
    modifier: Modifier = Modifier,
    unit: OdoDistanceUnit = LocalOdoDistanceFormat.current.unit,
    note: String? = null,
    quickAdds: List<Int> = listOf(100, 500, 1000),
    scanLabel: String? = null,
    scanBadge: String? = null,
    digits: Int = 6,
    footer: (@Composable ColumnScope.(dialled: Long?) -> Unit)? = null,
) {
    var entry by remember(value) { mutableStateOf(value?.takeIf { it > 0 }?.toString().orEmpty()) }
    OdometerEditor(
        entry = entry,
        unit = unit,
        title = title,
        subtitle = subtitle,
        odometerLabel = odometerLabel,
        saveLabel = saveLabel,
        kmLabel = kmLabel,
        milesLabel = milesLabel,
        note = note,
        quickAdds = quickAdds,
        scanLabel = scanLabel,
        scanBadge = scanBadge,
        digits = digits,
        modifier = modifier,
        footer = footer?.let { slot -> { slot(entry.toLongOrNull()) } },
        onEntryChange = { typed ->
            entry = typed.filter { it.isDigit() }.trimStart('0').take(digits)
        },
        onQuickAdd = { amount ->
            val next = ((entry.toLongOrNull() ?: 0L) + amount).coerceAtMost(maxReading(digits))
            entry = next.toString()
        },
        onSave = { onSave(entry.toLongOrNull() ?: 0L) },
    )
}

/**
 * The odometer shown as a **read-only** hero display — the drum and ruler strip with no
 * click target, no editor sheet, no chevron. Used for a moment that only reports a reading
 * rather than asking for one, like the trip-logged screen's "N km added" panel: it reuses
 * [OdometerFrame]'s exact rendering (the accent-bordered panel, the split-flap digits, the
 * ruler) instead of leaving [OdoOdometer]'s tap-to-edit chrome dimmed via `enabled = false`,
 * which still shows the chevron-less-but-clearly-tappable-looking surface and forces the
 * caller to pass throwaway editor labels (`saveLabel`, `onValueChange`, …) for a sheet that
 * is never opened.
 *
 * [value] is already in the caller's display unit, the same convention [OdoOdometer]'s
 * `value` uses — convert with `LocalOdoDistanceFormat.current.display(km)` first.
 */
@Composable
fun OdoOdometerDisplay(
    value: Long,
    odometerLabel: String,
    modifier: Modifier = Modifier,
    unitLabel: String = LocalOdoDistanceFormat.current.suffix,
    digits: Int = 6,
) {
    val colors = OdoTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.card)
            .border(1.5.dp, colors.accent, OdoTheme.shapes.card)
            .padding(OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcSpeedometer, contentDescription = null, tint = colors.textMuted, size = OdoTheme.iconSizes.small)
            OdoText(odometerLabel, style = OdoTheme.typography.caption.copy(letterSpacing = 3.sp), color = colors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DrumRow(reading = value, digits = digits, big = true)
            OdoText(unitLabel, style = OdoTheme.typography.label, color = colors.textDim)
        }
        Ruler()
    }
}

@OdoThemePreviews
@Composable
private fun OdoOdometerDisplayPreview() = OdoPreview {
    OdoOdometerDisplay(value = 64231, odometerLabel = "ODOMETER")
}

/* ------------------------------ Collapsed surface ------------------------------ */

@Composable
private fun CollapsedOdometer(
    value: Long?,
    unit: OdoDistanceUnit,
    kmLabel: String,
    milesLabel: String,
    hint: String?,
    digits: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.field)
            .background(colors.surface)
            .border(1.dp, colors.border, OdoTheme.shapes.field)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
    ) {
        if (value == null && hint != null) {
            OdoText(hint, style = OdoTheme.typography.body, color = colors.textMuted, modifier = Modifier.weight(1f))
        } else {
            DrumRow(reading = value ?: 0L, digits = digits, big = false, modifier = Modifier.weight(1f))
        }
        OdoText(
            if (unit == OdoDistanceUnit.KM) kmLabel else milesLabel,
            style = OdoTheme.typography.body,
            color = colors.textDim,
        )
        Chevron()
    }
}

/* ------------------------------ Sheet editor ------------------------------ */

@Composable
private fun OdometerEditor(
    entry: String,
    unit: OdoDistanceUnit,
    title: String,
    subtitle: String,
    odometerLabel: String,
    saveLabel: String,
    kmLabel: String,
    milesLabel: String,
    note: String?,
    quickAdds: List<Int>,
    scanLabel: String?,
    scanBadge: String?,
    digits: Int,
    onEntryChange: (String) -> Unit,
    onQuickAdd: (Int) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = OdoTheme.colors
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OdoTheme.spacing.screenEdge)
            .padding(bottom = OdoTheme.spacing.xl)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.lg),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs)) {
            OdoText(title, style = OdoTheme.typography.title, color = colors.text)
            OdoText(subtitle, style = OdoTheme.typography.bodySmall, color = colors.textDim)
        }

        Box {
            OdometerFrame(
                entry = entry,
                unit = unit,
                digits = digits,
                odometerLabel = odometerLabel,
                kmLabel = kmLabel,
                milesLabel = milesLabel,
                onClick = {
                    focusRequester.requestFocus()
                    keyboard?.show()
                },
            )
            // Typing comes from the system number pad. This invisible field owns the
            // focus; the drums above are the display. The cursor is pinned to the end so
            // the keyboard's backspace always removes the last digit.
            BasicTextField(
                value = TextFieldValue(entry, TextRange(entry.length)),
                onValueChange = { onEntryChange(it.text) },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (entry.isNotEmpty()) onSave() }),
                cursorBrush = SolidColor(Color.Transparent),
                singleLine = true,
            )
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            quickAdds.forEach { amount ->
                OdoChip(label = "+" + groupThousands(amount.toLong()), onClick = { onQuickAdd(amount) })
            }
            if (scanLabel != null) {
                ScanChip(label = scanLabel, badge = scanBadge)
            }
        }

        if (note != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OdoTheme.shapes.card)
                    .background(colors.surfaceRaised)
                    .padding(OdoTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
            ) {
                OdoIcon(IcInfo, contentDescription = null, tint = colors.textMuted, size = OdoTheme.iconSizes.medium)
                OdoText(note, style = OdoTheme.typography.bodySmall, color = colors.textDim)
            }
        }

        footer?.invoke(this)

        OdoButton(text = saveLabel, onClick = onSave, enabled = entry.isNotEmpty(), modifier = Modifier.fillMaxWidth())
    }
}

/** The accent-framed odometer panel: label, big drums + unit label, ruler tick strip.
 *  Tapping it calls [onClick], which brings the system keyboard back. No ripple: the
 *  panel is a display, not a button, and a flash across it reads as a glitch. */
@Composable
private fun OdometerFrame(
    entry: String,
    unit: OdoDistanceUnit,
    digits: Int,
    odometerLabel: String,
    kmLabel: String,
    milesLabel: String,
    onClick: () -> Unit,
) {
    val colors = OdoTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OdoTheme.shapes.card)
            .border(1.5.dp, colors.accent, OdoTheme.shapes.card)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(OdoTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcSpeedometer, contentDescription = null, tint = colors.textMuted, size = OdoTheme.iconSizes.small)
            OdoText(odometerLabel, style = OdoTheme.typography.caption.copy(letterSpacing = 3.sp), color = colors.textMuted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DrumRow(reading = entry.toLongOrNull() ?: 0L, filled = entry.length, digits = digits, big = true)
            OdoText(
                if (unit == OdoDistanceUnit.KM) kmLabel else milesLabel,
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.textDim,
            )
        }
        Ruler()
    }
}

/** A row of mechanical digit drums, zero-padded to [digits] and right-aligned. */
@Composable
private fun DrumRow(reading: Long, digits: Int, big: Boolean, modifier: Modifier = Modifier, filled: Int = -1) {
    val padded = reading.toString().padStart(digits, '0').takeLast(digits)
    val activeFrom = if (filled < 0) 0 else digits - filled.coerceIn(0, digits)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (big) 6.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
        padded.forEachIndexed { index, ch ->
            DigitDrum(
                ch = ch,
                big = big,
                isOnes = index == digits - 1,
                dim = index < activeFrom,
            )
        }
    }
}

/** One split-flap drum: a cylindrical dark cell with a seam and one digit. The ones
 *  drum is accent-filled (the fast-rolling wheel); leading unentered digits dim out. */
@Composable
private fun DigitDrum(ch: Char, big: Boolean, isOnes: Boolean, dim: Boolean) {
    val colors = OdoTheme.colors
    val w = if (big) 40.dp else 26.dp
    val h = if (big) 56.dp else 34.dp
    val digitColor = when {
        isOnes -> colors.onAccent
        dim -> colors.textMuted
        else -> colors.accent
    }
    Box(
        modifier = Modifier
            .width(w)
            .height(h)
            .clip(OdoTheme.shapes.small)
            .then(
                if (isOnes) Modifier.background(colors.accent)
                else Modifier.background(Brush.verticalGradient(listOf(colors.bg, colors.surfaceRaised, colors.bg)))
            )
            .border(1.dp, if (isOnes) colors.accent else colors.border, OdoTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        // Split-flap seam across the middle.
        Box(Modifier.fillMaxWidth().height(1.dp).align(Alignment.Center).background(colors.bg.copy(alpha = 0.6f)))
        OdoText(
            ch.toString(),
            style = OdoTheme.typography.numeric.copy(fontSize = if (big) 30.sp else 16.sp),
            color = digitColor,
        )
    }
}

/** The odometer's tick strip — a ruler with a highlighted centre mark. */
@Composable
private fun Ruler() {
    val colors = OdoTheme.colors
    Canvas(Modifier.fillMaxWidth().height(16.dp)) {
        val gap = 12.dp.toPx()
        val count = (size.width / gap).toInt()
        val centre = count / 2
        for (i in 0..count) {
            val x = i * gap
            val tall = i % 5 == 0
            val isCentre = i == centre
            val tickH = when {
                isCentre -> size.height
                tall -> size.height * 0.6f
                else -> size.height * 0.35f
            }
            drawLine(
                color = if (isCentre) colors.accent else colors.border,
                start = Offset(x, size.height - tickH),
                end = Offset(x, size.height),
                strokeWidth = if (isCentre) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** "Scan" quick-action chip with a trailing status badge (e.g. SOON) — disabled for now. */
@Composable
private fun ScanChip(label: String, badge: String?) {
    val colors = OdoTheme.colors
    Row(
        modifier = Modifier
            .clip(OdoTheme.shapes.pill)
            .border(1.dp, colors.border, OdoTheme.shapes.pill)
            .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoIcon(IcCamera, contentDescription = null, tint = colors.textMuted, size = OdoTheme.iconSizes.small)
        OdoText(label, style = OdoTheme.typography.label, color = colors.textMuted)
        if (badge != null) OdoBadge(badge, tone = OdoBadgeTone.Neutral)
    }
}

/** A small right-pointing chevron for the collapsed field. */
@Composable
private fun Chevron() {
    OdoIcon(
        imageVector = IcChevronRight,
        contentDescription = null,
        tint = OdoTheme.colors.textDim,
        size = OdoTheme.iconSizes.medium,
    )
}

private fun maxReading(digits: Int): Long {
    var m = 1L
    repeat(digits) { m *= 10 }
    return m - 1
}

/** Group whole numbers into thousands ("54000" → "54,000") without a locale. */
private fun groupThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    s.reversed().forEachIndexed { i, c ->
        if (i > 0 && i % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.reverse().toString()
}

@OdoThemePreviews
@Composable
private fun OdoOdometerCollapsedPreview() = OdoPreview {
    OdoOdometer(
        value = 54000,
        onValueChange = {},
        title = "Current odometer",
        subtitle = "Powers your Rs./km and service predictions.",
        odometerLabel = "ODOMETER",
        saveLabel = "Save",
        kmLabel = "km",
        milesLabel = "mi",
        helper = "Tap to set — powers your Rs./km and service predictions.",
        note = "Read it off your dashboard cluster — an approximate value is fine.",
        scanLabel = "Scan",
        scanBadge = "SOON",
    )
}
