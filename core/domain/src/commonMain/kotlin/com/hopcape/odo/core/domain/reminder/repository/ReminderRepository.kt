package com.hopcape.odo.core.domain.reminder.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.reminder.model.CustomReminder
import com.hopcape.odo.core.domain.reminder.model.ReminderDismissal
import com.hopcape.odo.core.domain.reminder.model.ReminderId
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for the stored half of reminders: the owner's custom reminders, and the record
 * of nudges they dismissed. The implementation lives in `:core:data` (local `reminders`
 * mirror table); the domain stays ignorant of it.
 *
 * Deliberately *not* a store of the derived reminders (insurance, PUC, service due) —
 * those are recomputed from documents and service history on every read, so persisting
 * them would only create rows that go stale the moment a document is edited. The only
 * derived thing worth a row is a dismissal, because "the owner waved this one away" is
 * a fact no source data can reproduce.
 */
interface ReminderRepository {

    /** A car's non-deleted custom reminders, paused ones included. */
    fun observeCustom(carId: CarId): Flow<List<CustomReminder>>

    /** A single non-deleted custom reminder (edit prefill); emits `null` if absent. */
    fun observe(id: ReminderId): Flow<CustomReminder?>

    suspend fun add(reminder: CustomReminder): Either<DomainError, CustomReminder>

    suspend fun update(reminder: CustomReminder): Either<DomainError, CustomReminder>

    /** Soft delete (sets `deleted_at`); the row is retained for history/audit. */
    suspend fun softDelete(id: ReminderId): Either<DomainError, Unit>

    /**
     * Every dismissal recorded for the car. The whole set rather than a query per
     * occurrence: the feed filters a handful of items against it in memory, and one
     * stream keeps the feed's inputs emitting together.
     */
    fun observeDismissals(carId: CarId): Flow<List<ReminderDismissal>>

    /** Record one dismissed occurrence. Dismissing the same occurrence twice succeeds. */
    suspend fun dismiss(carId: CarId, dismissal: ReminderDismissal): Either<DomainError, Unit>
}
