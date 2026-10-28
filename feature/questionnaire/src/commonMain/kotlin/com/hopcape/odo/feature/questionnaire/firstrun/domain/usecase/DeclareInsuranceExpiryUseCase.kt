package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import com.hopcape.odo.core.domain.reminder.model.ReminderKind
import com.hopcape.odo.core.domain.reminder.repository.DeclaredExpiryRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.notification.DocumentReminderScheduler
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Stores the insurance expiry month from setup and plans its reminders. Due on the month's last day. */
internal class DeclareInsuranceExpiryUseCase(
    private val expiries: DeclaredExpiryRepository,
    private val documentReminders: DocumentReminderScheduler,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(carId: CarId, ownerId: OwnerId, month: LocalDate): Either<DomainError, Unit> {
        val dueOn = LocalDate(month.year, month.month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
        return expiries.declare(DeclaredExpiry(ids.newId(), carId, ownerId, ReminderKind.INSURANCE_EXPIRY, dueOn))
            .onRight { documentReminders.refresh() }
    }
}
