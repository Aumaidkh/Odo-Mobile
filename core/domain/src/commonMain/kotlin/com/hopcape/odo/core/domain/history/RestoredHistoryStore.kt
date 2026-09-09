package com.hopcape.odo.core.domain.history

import com.hopcape.odo.core.domain.car.model.CarId
import kotlinx.coroutines.flow.Flow

/**
 * The car a sign-in put history back onto, waiting for the owner to be told about it.
 *
 * Device-local rather than synced: "this phone has already said so" belongs to the phone.
 * Signing in on a second device announces the restore there too, which is right — that
 * device restored it as well.
 */
interface RestoredHistoryStore {

    /** The car whose restore has not been announced yet, or null when there is nothing to say. */
    fun pending(): Flow<CarId?>

    /**
     * A sign-in brought this car's history back.
     *
     * Recording the same car twice is still one announcement, so both restore paths — a
     * plate matched to the account's car, and a car pulled down for the first time — can
     * call it without checking each other.
     */
    suspend fun record(carId: CarId)

    /** The owner has seen it. */
    suspend fun clear()
}
