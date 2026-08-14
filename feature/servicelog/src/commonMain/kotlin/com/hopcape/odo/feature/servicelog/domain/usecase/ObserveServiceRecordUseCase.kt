package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.record.analysis.ServiceRecordBuilder
import com.hopcape.odo.core.domain.record.model.ServiceRecord
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The car's whole record as a stream — what the share sheet renders to a PDF.
 *
 * Gathers the five things the printed document is made of: the car, its services, its
 * documents, its score history and the owner's own details. [ServiceRecordBuilder] owns the
 * assembly; this decides only what the builder cannot see — which car, what day it is, and
 * what zone that day belongs to.
 *
 * One combined read rather than five the sheet stitches together, for the same reason as the
 * Timeline's own read: the headline counts and the printed rows are one fact about the car,
 * and separate streams settle at different moments. A document claiming nine entries above
 * eight printed lines is the failure this shape prevents.
 *
 * All five are observed rather than read once, because the sheet stays open while the owner
 * picks a target, and a service logged from another tab in that time belongs in the file they
 * are about to send.
 *
 * The day is read once per emission rather than captured at construction, so a sheet left
 * open across midnight dates the document the day it was actually produced.
 */
internal class ObserveServiceRecordUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val documents: DocumentRepository,
    private val scores: HealthScoreRepository,
    private val owners: OwnerProfileRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<ServiceRecord> = combine(
        cars.observe(carId),
        logs.observe(carId),
        documents.observe(carId),
        scores.observeHistory(carId),
        owners.observe(),
    ) { car, entries, papers, history, owner ->
        ServiceRecordBuilder.build(
            car = car,
            owner = owner,
            entries = entries,
            documents = papers,
            scores = history,
            today = clock.now().toLocalDateTime(timeZone).date,
            zone = timeZone,
        )
    }
}
