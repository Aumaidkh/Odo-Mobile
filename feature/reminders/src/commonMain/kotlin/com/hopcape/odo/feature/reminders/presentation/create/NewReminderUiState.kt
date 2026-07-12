package com.hopcape.odo.feature.reminders.presentation.create

/** The quick-pick topics on the create screen; [CUSTOM] frees the name field. */
internal enum class ReminderPreset { AIR_PRESSURE, COOLANT, WIPER_FLUID, BATTERY, CUSTOM }

/** How often the reminder recurs. [BY_DISTANCE] triggers off odometer, not the calendar. */
internal enum class ReminderRepeat { EVERY_15_DAYS, MONTHLY, BY_DISTANCE, ONCE }

/**
 * Display state for the "New reminder" (create custom) form — the topic, its name,
 * the cadence, and when it starts. The reminder engine turns this into a schedule; the
 * screen just edits it.
 *
 * [startMillis] is the first-nudge date as epoch millis (fed to / read from the date
 * picker); [hour]/[minute] are the 24h time (fed to / read from the time picker).
 * [customLabel] holds the owner's own topic once they've typed one into the "+ Custom"
 * chip — empty until then.
 */
internal data class NewReminderUiState(
    val preset: ReminderPreset,
    val name: String,
    val repeat: ReminderRepeat,
    val startMillis: Long,
    val hour: Int,
    val minute: Int,
    val customLabel: String = "",
) {
    /**
     * Picks a preset. A concrete topic also fills the name with its [defaultName];
     * [ReminderPreset.CUSTOM] leaves whatever the owner has typed intact.
     */
    fun selectPreset(preset: ReminderPreset, defaultName: String): NewReminderUiState =
        if (preset == ReminderPreset.CUSTOM) copy(preset = preset)
        else copy(preset = preset, name = defaultName)

    /** Commits a typed custom topic: selects [ReminderPreset.CUSTOM] and names it [label]. */
    fun withCustomLabel(label: String): NewReminderUiState =
        copy(preset = ReminderPreset.CUSTOM, customLabel = label, name = label)
}

/** Sample state (mirrors the mockup: air-pressure check, every 15 days, [startMillis] at 9 AM). */
internal fun sampleNewReminder(startMillis: Long): NewReminderUiState = NewReminderUiState(
    preset = ReminderPreset.AIR_PRESSURE,
    name = "Air pressure check",
    repeat = ReminderRepeat.EVERY_15_DAYS,
    startMillis = startMillis,
    hour = 9,
    minute = 0,
)
