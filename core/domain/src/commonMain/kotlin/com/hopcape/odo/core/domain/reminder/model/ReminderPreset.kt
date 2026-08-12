package com.hopcape.odo.core.domain.reminder.model

/**
 * The ready-made topics the create screen offers, each with the cadence it defaults to.
 *
 * A preset is a starting point, not a type: picking one prefills the title and cadence,
 * and the owner can change both. The feed also uses this list the other way around — a
 * preset with no live reminder behind it becomes a "Remind me" suggestion row.
 */
enum class ReminderPreset(val defaultCadence: ReminderCadence) {
    AIR_PRESSURE(ReminderCadence.EveryDays(15)),
    COOLANT(ReminderCadence.Monthly),
    WIPER_FLUID(ReminderCadence.Monthly),
    BATTERY(ReminderCadence.Monthly),

    /** Distance-based: tyres wear by the kilometre, not the calendar. */
    TYRE_ROTATION(ReminderCadence.EveryDistance(10_000)),
}
