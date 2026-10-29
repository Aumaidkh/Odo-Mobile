package com.hopcape.odo.core.data.reminder

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.reminder.model.DeclaredExpiry
import kotlinx.coroutines.flow.Flow

/** Local storage for declared expiries. Writes leave rows `PENDING` for sync. */
interface DeclaredExpiryLocalDataSource {
    fun observeByCar(carId: CarId): Flow<List<DeclaredExpiry>>

    /** Replaces the car's live expiry of the same kind with [expiry], in one transaction. */
    suspend fun replace(expiry: DeclaredExpiry)
}
