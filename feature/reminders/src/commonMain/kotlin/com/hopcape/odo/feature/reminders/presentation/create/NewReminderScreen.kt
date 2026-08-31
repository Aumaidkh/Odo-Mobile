package com.hopcape.odo.feature.reminders.presentation.create

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.hopcape.odo.core.designsystem.component.OdoButton
import com.hopcape.odo.core.designsystem.component.OdoCard
import com.hopcape.odo.core.designsystem.component.OdoChip
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButton
import com.hopcape.odo.core.designsystem.component.OdoCircularIconButtonVariant
import com.hopcape.odo.core.designsystem.component.OdoIcon
import com.hopcape.odo.core.designsystem.component.OdoInputField
import com.hopcape.odo.core.designsystem.component.OdoOdometerField
import com.hopcape.odo.core.designsystem.component.OdoScreen
import com.hopcape.odo.core.designsystem.component.OdoText
import com.hopcape.odo.core.designsystem.units.LocalOdoDistanceFormat
import com.hopcape.odo.core.designsystem.icons.IcBellFilled
import com.hopcape.odo.core.designsystem.icons.IcCalendar
import com.hopcape.odo.core.designsystem.icons.IcClock
import com.hopcape.odo.core.designsystem.icons.IcClose
import com.hopcape.odo.core.designsystem.icons.IcTyre
import com.hopcape.odo.core.designsystem.preview.OdoPreview
import com.hopcape.odo.core.designsystem.preview.OdoThemePreviews
import com.hopcape.odo.core.designsystem.text.DistanceArg
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.designsystem.text.asString
import com.hopcape.odo.core.designsystem.theme.OdoTheme
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import com.hopcape.odo.core.domain.shared.formatDate
import com.hopcape.odo.feature.reminders.resources.Res
import com.hopcape.odo.feature.reminders.resources.rm_new_about
import com.hopcape.odo.feature.reminders.resources.rm_new_cd_close
import com.hopcape.odo.feature.reminders.resources.rm_new_change
import com.hopcape.odo.feature.reminders.resources.rm_new_custom_placeholder
import com.hopcape.odo.feature.reminders.resources.rm_new_anchor
import com.hopcape.odo.feature.reminders.resources.rm_new_default_air
import com.hopcape.odo.feature.reminders.resources.rm_new_default_battery
import com.hopcape.odo.feature.reminders.resources.rm_new_default_coolant
import com.hopcape.odo.feature.reminders.resources.rm_new_default_tyre
import com.hopcape.odo.feature.reminders.resources.rm_new_default_wiper
import com.hopcape.odo.feature.reminders.resources.rm_new_name_label
import com.hopcape.odo.feature.reminders.resources.rm_new_name_placeholder
import com.hopcape.odo.feature.reminders.resources.rm_new_notified_channels
import com.hopcape.odo.feature.reminders.resources.rm_new_notified_prefix
import com.hopcape.odo.feature.reminders.resources.rm_new_notified_suffix
import com.hopcape.odo.feature.reminders.resources.rm_new_picker_cancel
import com.hopcape.odo.feature.reminders.resources.rm_new_picker_confirm
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_air
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_battery
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_coolant
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_custom
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_tyre
import com.hopcape.odo.feature.reminders.resources.rm_new_preset_wiper
import com.hopcape.odo.feature.reminders.resources.rm_new_repeat_15
import com.hopcape.odo.feature.reminders.resources.rm_new_repeat_distance
import com.hopcape.odo.feature.reminders.resources.rm_new_repeat_label
import com.hopcape.odo.feature.reminders.resources.rm_new_repeat_monthly
import com.hopcape.odo.feature.reminders.resources.rm_new_repeat_once
import com.hopcape.odo.feature.reminders.resources.rm_new_save
import com.hopcape.odo.feature.reminders.resources.rm_new_starts_label
import com.hopcape.odo.feature.reminders.resources.rm_new_starts_today
import com.hopcape.odo.feature.reminders.resources.rm_new_starts_tomorrow
import com.hopcape.odo.feature.reminders.resources.rm_new_step_km
import com.hopcape.odo.feature.reminders.resources.rm_new_step_label
import com.hopcape.odo.feature.reminders.resources.rm_new_step_miles
import com.hopcape.odo.feature.reminders.resources.rm_new_time_label
import com.hopcape.odo.feature.reminders.resources.rm_new_title
import com.hopcape.odo.feature.reminders.resources.rm_new_title_edit
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/**
 * The "New reminder" (create custom) form — reached from the reminders home's "+ Add".
 * The owner picks a topic (or types their own into the "+ Custom" chip), a cadence, a
 * start date and time; the notify card mirrors their channel settings with a shortcut.
 *
 * State-free: renders [state] and forwards intents. Persisting the reminder is M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewReminderScreen(
    state: NewReminderUiState,
    onPresetSelect: (ReminderPreset, String) -> Unit,
    onCustomSave: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onRepeatChange: (ReminderRepeat) -> Unit,
    onStartChange: (Long) -> Unit,
    onTimeChange: (Int, Int) -> Unit,
    onDistanceStepChange: (Int) -> Unit,
    onChangeChannels: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val defaultNames = mapOf(
        ReminderPreset.AIR_PRESSURE to stringResource(Res.string.rm_new_default_air),
        ReminderPreset.COOLANT to stringResource(Res.string.rm_new_default_coolant),
        ReminderPreset.WIPER_FLUID to stringResource(Res.string.rm_new_default_wiper),
        ReminderPreset.BATTERY to stringResource(Res.string.rm_new_default_battery),
        ReminderPreset.TYRE_ROTATION to stringResource(Res.string.rm_new_default_tyre),
    )
    OdoScreen(
        modifier = modifier,
        topBar = { NewReminderTopBar(state.editing, onClose) },
        bottomBar = { SaveBar(onSave) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(vertical = OdoTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.xl),
        ) {
            Field(stringResource(Res.string.rm_new_about)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    ReminderPreset.entries.forEach { preset ->
                        OdoChip(
                            label = presetLabel(preset),
                            selected = preset == state.preset,
                            onClick = { onPresetSelect(preset, defaultNames[preset].orEmpty()) },
                            leadingIcon = if (preset == ReminderPreset.AIR_PRESSURE || preset == ReminderPreset.TYRE_ROTATION) {
                                { OdoIcon(IcTyre, contentDescription = null, size = OdoTheme.iconSizes.small) }
                            } else null,
                        )
                    }
                    CustomChip(
                        label = state.customLabel,
                        selected = state.customSelected,
                        onSave = onCustomSave,
                    )
                }
            }

            Field(stringResource(Res.string.rm_new_name_label)) {
                OdoInputField(
                    value = state.name,
                    onValueChange = onNameChange,
                    placeholder = stringResource(Res.string.rm_new_name_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                state.nameError?.let { ErrorLine(it.asString()) }
            }

            Field(stringResource(Res.string.rm_new_repeat_label)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm),
                ) {
                    // No reading, no by-distance: a chip that creates a reminder the
                    // domain rejects is worse than an absent one.
                    ReminderRepeat.entries
                        .filter { it != ReminderRepeat.BY_DISTANCE || state.distanceAvailable }
                        .forEach { repeat ->
                            OdoChip(
                                label = repeatLabel(repeat),
                                selected = repeat == state.repeat,
                                onClick = { onRepeatChange(repeat) },
                            )
                        }
                }
                if (state.repeat == ReminderRepeat.BY_DISTANCE && state.anchorKm != null) {
                    OdoText(
                        UiText(Res.string.rm_new_anchor, listOf(DistanceArg(state.anchorKm))).asString(),
                        style = OdoTheme.typography.bodySmall,
                        color = OdoTheme.colors.textDim,
                    )
                }
            }

            if (state.repeat == ReminderRepeat.BY_DISTANCE) {
                Field(stringResource(Res.string.rm_new_step_label)) {
                    DistanceStepField(
                        stepKm = state.distanceStepKm,
                        onStepChange = onDistanceStepChange,
                        errorText = state.distanceStepError?.asString(),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md)) {
                Field(stringResource(Res.string.rm_new_starts_label), Modifier.weight(1f)) {
                    PickerField(value = startDateLabel(state.startMillis), trailing = IcCalendar) { showDatePicker = true }
                }
                Field(stringResource(Res.string.rm_new_time_label), Modifier.weight(1f)) {
                    PickerField(value = formatTime(state.hour, state.minute), trailing = IcClock) { showTimePicker = true }
                }
            }
            state.startError?.let { ErrorLine(it.asString()) }
            state.formError?.let { ErrorLine(it.asString()) }

            NotifyCard(onChangeChannels)
        }
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState(initialSelectedDateMillis = state.startMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let(onStartChange)
                    showDatePicker = false
                }) { OdoText(stringResource(Res.string.rm_new_picker_confirm), color = OdoTheme.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    OdoText(stringResource(Res.string.rm_new_picker_cancel), color = OdoTheme.colors.textDim)
                }
            },
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(initialHour = state.hour, initialMinute = state.minute, is24Hour = false)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeChange(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { OdoText(stringResource(Res.string.rm_new_picker_confirm), color = OdoTheme.colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    OdoText(stringResource(Res.string.rm_new_picker_cancel), color = OdoTheme.colors.textDim)
                }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

/**
 * The "+ Custom" chip. Tapping it turns the chip into an inline text field; once the
 * owner types a name and confirms, it saves and shows as a normal (selectable) chip
 * carrying that name. Tapping a saved custom chip again re-opens it for editing.
 */
@Composable
private fun CustomChip(label: String, selected: Boolean, onSave: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    when {
        editing -> CustomChipEditor(
            initial = label,
            onCommit = { text ->
                editing = false
                if (text.isNotBlank()) onSave(text.trim())
            },
        )
        label.isEmpty() -> OdoChip(label = stringResource(Res.string.rm_new_preset_custom), onClick = { editing = true })
        else -> OdoChip(label = label, selected = selected, onClick = { if (selected) editing = true else onSave(label) })
    }
}

@Composable
private fun CustomChipEditor(initial: String, onCommit: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(
        shape = OdoTheme.shapes.pill,
        color = OdoTheme.colors.accent.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, OdoTheme.colors.accent),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = OdoTheme.typography.label.copy(color = OdoTheme.colors.accent),
            cursorBrush = SolidColor(OdoTheme.colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(text) }),
            modifier = Modifier
                .focusRequester(focus)
                .widthIn(min = 96.dp)
                .padding(horizontal = OdoTheme.spacing.md, vertical = OdoTheme.spacing.sm),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    OdoText(stringResource(Res.string.rm_new_custom_placeholder), style = OdoTheme.typography.label, color = OdoTheme.colors.textMuted)
                }
                inner()
            },
        )
    }
}

@Composable
private fun ErrorLine(message: String) {
    OdoText(message, style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.danger)
}

@Composable
private fun NewReminderTopBar(editing: Boolean, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OdoCircularIconButton(
            IcClose,
            contentDescription = stringResource(Res.string.rm_new_cd_close),
            onClick = onClose,
            variant = OdoCircularIconButtonVariant.Raised,
        )
        OdoText(
            stringResource(if (editing) Res.string.rm_new_title_edit else Res.string.rm_new_title),
            style = OdoTheme.typography.title,
        )
    }
}

@Composable
private fun SaveBar(onSave: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = OdoTheme.spacing.screenEdge, vertical = OdoTheme.spacing.md),
    ) {
        OdoButton(stringResource(Res.string.rm_new_save), onClick = onSave, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * How far apart the nudges are, in the owner's own unit — converted to/from kilometres at
 * this screen, the same way an odometer entry is (see [com.hopcape.odo.core.designsystem.units.OdoDistanceFormat]).
 * A local buffer holds what is on screen mid-typing; [stepKm] is only ever the last value
 * that actually parsed, so backspacing to an empty field does not send a zero-km step.
 */
@Composable
private fun DistanceStepField(stepKm: Int, onStepChange: (Int) -> Unit, errorText: String?) {
    val format = LocalOdoDistanceFormat.current
    var text by remember(stepKm) { mutableStateOf(format.display(stepKm).toString()) }
    OdoOdometerField(
        value = text,
        onValueChange = { typed ->
            text = typed
            typed.toIntOrNull()?.takeIf { it > 0 }?.let { onStepChange(format.store(it)) }
        },
        kmLabel = stringResource(Res.string.rm_new_step_km),
        milesLabel = stringResource(Res.string.rm_new_step_miles),
        errorText = errorText,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A read-only, tappable field that opens a picker — the field visual, but the whole surface is a button. */
@Composable
private fun PickerField(value: String, trailing: ImageVector, onClick: () -> Unit) {
    Box {
        OdoInputField(
            value = value,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { OdoIcon(trailing, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium) },
        )
        Box(Modifier.matchParentSize().clip(OdoTheme.shapes.field).clickable(onClick = onClick))
    }
}

@Composable
private fun NotifyCard(onChange: () -> Unit) {
    OdoCard {
        Row(horizontalArrangement = Arrangement.spacedBy(OdoTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            OdoIcon(IcBellFilled, contentDescription = null, tint = OdoTheme.colors.textDim, size = OdoTheme.iconSizes.medium)
            OdoText(notifiedText(), style = OdoTheme.typography.bodySmall, color = OdoTheme.colors.textDim, modifier = Modifier.weight(1f))
            OdoText(
                stringResource(Res.string.rm_new_change),
                style = OdoTheme.typography.label,
                color = OdoTheme.colors.accent,
                modifier = Modifier.clip(OdoTheme.shapes.pill).clickable(onClick = onChange).padding(horizontal = OdoTheme.spacing.sm, vertical = OdoTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun notifiedText() = buildAnnotatedString {
    append(stringResource(Res.string.rm_new_notified_prefix))
    append(" ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = OdoTheme.colors.text)) {
        append(stringResource(Res.string.rm_new_notified_channels))
    }
    append(" ")
    append(stringResource(Res.string.rm_new_notified_suffix))
}

@Composable
private fun Field(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(OdoTheme.spacing.sm)) {
        OdoText(label, style = OdoTheme.typography.caption, color = OdoTheme.colors.textMuted)
        content()
    }
}

/** "Today" / "Tomorrow" for the near days, else "12 Jul 2026" (UTC, to match the date picker's millis). */
@Composable
private fun startDateLabel(millis: Long): String {
    val days = (millis / 86_400_000L).toInt()
    val today = (Clock.System.now().toEpochMilliseconds() / 86_400_000L).toInt()
    return when (days - today) {
        0 -> stringResource(Res.string.rm_new_starts_today)
        1 -> stringResource(Res.string.rm_new_starts_tomorrow)
        else -> formatDate(LocalDate.fromEpochDays(days))
    }
}

/** 24h hour/minute → "9:00 AM". */
private fun formatTime(hour: Int, minute: Int): String {
    val period = if (hour < 12) "AM" else "PM"
    val h12 = if (hour % 12 == 0) 12 else hour % 12
    return "$h12:${minute.toString().padStart(2, '0')} $period"
}

@Composable
private fun presetLabel(preset: ReminderPreset): String = stringResource(
    when (preset) {
        ReminderPreset.AIR_PRESSURE -> Res.string.rm_new_preset_air
        ReminderPreset.COOLANT -> Res.string.rm_new_preset_coolant
        ReminderPreset.WIPER_FLUID -> Res.string.rm_new_preset_wiper
        ReminderPreset.BATTERY -> Res.string.rm_new_preset_battery
        ReminderPreset.TYRE_ROTATION -> Res.string.rm_new_preset_tyre
    },
)

@Composable
private fun repeatLabel(repeat: ReminderRepeat): String = stringResource(
    when (repeat) {
        ReminderRepeat.EVERY_15_DAYS -> Res.string.rm_new_repeat_15
        ReminderRepeat.MONTHLY -> Res.string.rm_new_repeat_monthly
        ReminderRepeat.BY_DISTANCE -> Res.string.rm_new_repeat_distance
        ReminderRepeat.ONCE -> Res.string.rm_new_repeat_once
    },
)

@OdoThemePreviews
@Composable
private fun NewReminderScreenPreview() = OdoPreview(padded = false) {
    NewReminderScreen(
        state = NewReminderUiState(name = "Air pressure check", anchorKm = 42_000),
        onPresetSelect = { _, _ -> },
        onCustomSave = {},
        onNameChange = {},
        onRepeatChange = {},
        onStartChange = {},
        onTimeChange = { _, _ -> },
        onDistanceStepChange = {},
        onChangeChannels = {},
        onSave = {},
        onClose = {},
    )
}
