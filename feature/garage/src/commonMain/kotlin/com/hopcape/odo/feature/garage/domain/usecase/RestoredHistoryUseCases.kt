package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.cost.repository.FuelFillRepository
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.history.RestoredHistoryStore
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.feature.garage.domain.RestoredHistoryPolicy
import com.hopcape.odo.feature.garage.domain.model.RestoredHistory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * The restore waiting to be announced, filled in with what actually came back.
 *
 * The store only remembers which car; the counts are read here, live, because the sync
 * writes the marker during the push phase and the records themselves land in the pull that
 * follows. Reading the counts once at that moment would show zero of everything.
 */
internal class ObserveRestoredHistoryUseCase(
    private val store: RestoredHistoryStore,
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val documents: DocumentRepository,
    private val fills: FuelFillRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<RestoredHistory?> =
        store.pending().flatMapLatest { carId -> carId?.let(::summarise) ?: flowOf(null) }

    private fun summarise(carId: CarId): Flow<RestoredHistory?> = combine(
        cars.observe(carId),
        logs.observe(carId),
        documents.observe(carId),
        fills.observeForCar(carId),
    ) { car, logs, documents, fills ->
        car?.let { RestoredHistoryPolicy.summarise(it, logs, documents, fills) }
    }
}

/**
 * The owner has seen the restore. Forgetting it is what stops the sheet coming back on the
 * next launch, whichever button closed it.
 */
internal class AcknowledgeRestoredHistoryUseCase(private val store: RestoredHistoryStore) {
    suspend operator fun invoke() = store.clear()
}
