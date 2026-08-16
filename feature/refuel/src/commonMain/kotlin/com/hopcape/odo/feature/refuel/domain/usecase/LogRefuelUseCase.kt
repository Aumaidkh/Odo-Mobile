package com.hopcape.odo.feature.refuel.domain.usecase

import arrow.core.EitherNel
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.common.id.IdGenerator
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.cost.model.FuelFillDraft
import com.hopcape.odo.core.domain.cost.model.FuelFillId
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Writes a confirmed draft as a fill.
 *
 * There is deliberately no payment gate. This records a fill the owner is telling Odo about,
 * not one Odo watched being paid for, so there is nothing to wait on: a cash fill in Srinagar
 * has no confirmation to check, and demanding one would make the feature unusable in most of
 * the country.
 *
 * What stands in for a gate is the confirm step — nothing reaches here that the owner has not
 * seen, unless they turned that off themselves.
 */
internal class LogRefuelUseCase(
    private val fills: FuelFillRepository,
    private val owner: CurrentOwnerProvider,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    suspend operator fun invoke(
        carId: CarId,
        draft: FuelFillDraft,
    ): EitherNel<DomainError, FuelFill> = either {
        val today = clock.now().toLocalDateTime(timeZone).date
        val fill = FuelFill.create(
            id = FuelFillId.new(ids),
            carId = carId,
            ownerId = owner.currentOwnerId(),
            // A fill is always recorded as it happens: every channel captures at the pump or
            // moments after it, so there is no date for the owner to get wrong.
            filledOn = today,
            odometerKm = draft.odometerKm,
            quantityMilli = draft.quantityMilli,
            unit = draft.unit,
            amountPaise = draft.amount?.paise,
            today = today,
            stationName = draft.stationName,
            transactionRef = draft.transactionRef,
            entrySource = draft.source,
        ).bind()

        fills.add(fill).mapLeft { nonEmptyListOf(it) }.bind()
    }
}
