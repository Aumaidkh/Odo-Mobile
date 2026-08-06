package com.hopcape.odo.feature.reminders.domain.usecase

import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * What the create/edit form submits — raw, unvalidated input. [CustomReminder.create]
 * is what validates it, so create and edit reject the same things.
 *
 * [anchorKm] matters only for a distance cadence: it is the odometer reading the count
 * starts from, prefilled by the caller from the car's current reading.
 */
internal data class CustomReminderCommand(
    val title: String?,
    val cadence: ReminderCadence,
    val startsOn: LocalDate,
    val at: LocalTime,
    val preset: ReminderPreset? = null,
    val anchorKm: Int? = null,
)
