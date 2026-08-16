package com.hopcape.odo.core.domain.cost.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.cost.model.FuelFill
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for a car's fuel fills. The implementation lives in `:core:data`; the domain stays
 * ignorant of it.
 *
 * The reads arrived with smart refuel, which needs the history for three separate things:
 * prefilling the next fill from the last one, working out what the owner usually spends, and
 * measuring what the last tank returned. All three want the same list newest-first, so there
 * is one query rather than three.
 *
 * There is still no update. A fill records a payment that already happened at a pump;
 * correcting one means deleting it and recording what actually occurred, and an editable
 * payment record is one nobody can rely on.
 */
interface FuelFillRepository {

    suspend fun add(fill: FuelFill): Either<DomainError, FuelFill>

    /**
     * The car's fills, newest first, as a stream.
     *
     * A stream rather than a snapshot because the screens built on it — the confirm step's
     * mileage line, the prefilled draft — are open while a fill is being written, and a
     * list read once would show the state before it.
     *
     * A read failure emits an empty list rather than throwing. Every caller treats "no
     * history" as a normal state already: the prefill starts blank and the mileage line is
     * omitted, which is exactly the right behaviour when the history could not be read.
     */
    fun observeForCar(carId: CarId): Flow<List<FuelFill>>

    /**
     * The car's most recent fill, or `null` if it has none.
     *
     * A one-shot read for the callers that only need the last visit — the prefilled draft,
     * and the notification that has to build a draft without a screen open.
     */
    suspend fun latestForCar(carId: CarId): Either<DomainError, FuelFill?>
}
