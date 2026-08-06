package com.hopcape.odo.core.data.reminder

import com.hopcape.odo.core.data.db.Reminders
import com.hopcape.odo.core.data.db.SelectDismissalsByCar
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderCadence
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.model.ReminderPreset
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * DB row → domain. The boundary where a generated [Reminders] row becomes a
 * [CustomReminder] or a [ReminderDismissal]; the domain never sees the row type.
 *
 * Both mappers answer `null` for a row this build cannot read — an unknown repeat kind,
 * an unparseable date, a missing title. The row was written by a newer build, and a
 * reminder is the one thing that must never *fire wrong*: a nudge on a guessed cadence
 * is worse than no nudge, so unlike a document (which falls back to OTHER and still
 * opens) an unreadable reminder row is skipped and the caller reports it.
 *
 * An unknown [preset][Reminders.preset] alone does not skip the row — the reminder still
 * works, it only stops claiming a suggestion slot.
 */
internal fun Reminders.toCustomReminderOrNull(
    zone: TimeZone = TimeZone.currentSystemDefault(),
): CustomReminder? {
    val title = title?.takeUnless { it.isBlank() } ?: return null
    val cadence = cadenceOrNull() ?: return null
    val startsOn = starts_on?.toLocalDateOrNull() ?: return null
    val at = remind_at?.toLocalTimeOrNull() ?: return null
    val anchorKm = anchor_km?.toInt()
    if (cadence is ReminderCadence.EveryDistance && (anchorKm == null || anchorKm < 0)) return null

    return CustomReminder.reconstitute(
        id = ReminderId(id),
        ownerId = OwnerId(owner_id),
        carId = CarId(car_id),
        title = title,
        cadence = cadence,
        startsOn = startsOn,
        at = at,
        paused = is_paused != 0L,
        addedOn = created_at.toInstantOrNull()?.toLocalDateTime(zone)?.date,
        preset = preset?.let { name -> ReminderPreset.entries.firstOrNull { it.name == name } },
        anchorKm = anchorKm,
    )
}

/** A dismissal row → domain, or `null` for a kind this build does not know. */
internal fun SelectDismissalsByCar.toDismissalOrNull(): ReminderDismissal? {
    val kind = ReminderKind.entries.firstOrNull { it.name == reminder_type } ?: return null
    val dueOn = due_date.toLocalDateOrNull() ?: return null
    // The domain invariant: a custom dismissal names its reminder, a derived one never
    // does. A row that breaks it is unreadable, not almost-readable.
    if ((kind == ReminderKind.CUSTOM) != (dismissed_custom_id != null)) return null
    return ReminderDismissal(
        kind = kind,
        dueOn = dueOn,
        customId = dismissed_custom_id?.let(::ReminderId),
    )
}

private fun Reminders.cadenceOrNull(): ReminderCadence? = when (repeat_kind) {
    REPEAT_ONCE -> ReminderCadence.Once
    REPEAT_MONTHLY -> ReminderCadence.Monthly
    REPEAT_EVERY_DAYS ->
        repeat_every_days?.toInt()?.takeIf { it > 0 }?.let { ReminderCadence.EveryDays(it) }
    REPEAT_EVERY_KM ->
        repeat_every_km?.toInt()?.takeIf { it > 0 }?.let { ReminderCadence.EveryDistance(it) }
    else -> null
}

/**
 * Domain cadence → the three columns that store it. One place, so the insert and the
 * update cannot disagree about which column a kind uses.
 */
internal data class CadenceColumns(
    val repeatKind: String,
    val repeatEveryDays: Long?,
    val repeatEveryKm: Long?,
)

internal fun ReminderCadence.toColumns(): CadenceColumns = when (this) {
    ReminderCadence.Once -> CadenceColumns(REPEAT_ONCE, null, null)
    ReminderCadence.Monthly -> CadenceColumns(REPEAT_MONTHLY, null, null)
    is ReminderCadence.EveryDays -> CadenceColumns(REPEAT_EVERY_DAYS, days.toLong(), null)
    is ReminderCadence.EveryDistance -> CadenceColumns(REPEAT_EVERY_KM, null, km.toLong())
}

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

/** Accepts both the local "09:00" and the wire's "09:00:00". */
private fun String.toLocalTimeOrNull(): LocalTime? = runCatching { LocalTime.parse(this) }.getOrNull()

private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

/** Repeat-kind column values — the Kotlin-side spelling of the `reminder_repeat` enum. */
internal const val REPEAT_ONCE = "ONCE"
internal const val REPEAT_EVERY_DAYS = "EVERY_DAYS"
internal const val REPEAT_MONTHLY = "MONTHLY"
internal const val REPEAT_EVERY_KM = "EVERY_KM"
