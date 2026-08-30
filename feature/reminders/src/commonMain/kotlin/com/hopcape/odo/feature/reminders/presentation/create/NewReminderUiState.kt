package com.hopcape.odo.feature.reminders.presentation.create

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset

/** How often the reminder recurs. [BY_DISTANCE] triggers off odometer, not the calendar. */
internal enum class ReminderRepeat { EVERY_15_DAYS, MONTHLY, BY_DISTANCE, ONCE }

/**
 * Display state for the "New reminder" form — create, or edit when [editing].
 *
 * [preset] is the picked ready-made topic; `null` means the owner's own topic, whose
 * label sits in [customLabel]. [startMillis] is the first-nudge date as epoch millis
 * (fed to / read from the date picker); [hour]/[minute] are the 24h time.
 *
 * [anchorKm] is the car's odometer reading today — what a distance cadence counts from.
 * `null` disables the by-distance chip: a reminder cannot count kilometres from a
 * reading nobody recorded.
 *
 * [distanceStepKm] is how far apart the nudges are for a distance cadence — always
 * kilometres, canonical, the same way [anchorKm] is; the screen converts to/from the
 * owner's chosen unit the same way it does for the anchor line. Prefilled from the
 * picked preset's own step when it has one, [DEFAULT_DISTANCE_STEP_KM] otherwise, and
 * always editable — a preset is a starting point, not a fixed interval.
 */
@Immutable
internal data class NewReminderUiState(
    val editing: Boolean = false,
    val preset: ReminderPreset? = ReminderPreset.AIR_PRESSURE,
    val customLabel: String = "",
    val name: String = "",
    val repeat: ReminderRepeat = ReminderRepeat.EVERY_15_DAYS,
    val startMillis: Long = 0L,
    val hour: Int = 9,
    val minute: Int = 0,
    val anchorKm: Int? = null,
    val distanceStepKm: Int = DEFAULT_DISTANCE_STEP_KM,
    val nameError: UiText? = null,
    val startError: UiText? = null,
    val distanceStepError: UiText? = null,
    val formError: UiText? = null,
    val saving: Boolean = false,
) {
    /** The "+ Custom" chip is the selected topic. */
    val customSelected: Boolean get() = preset == null

    val distanceAvailable: Boolean get() = anchorKm != null

    internal companion object {
        /**
         * The kilometre step a by-distance reminder starts with when nothing — neither the
         * picked preset nor a previous value — says otherwise.
         */
        const val DEFAULT_DISTANCE_STEP_KM = 10_000
    }
}
