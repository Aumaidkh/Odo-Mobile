package com.hopcape.odo.feature.garage.domain.usecase

import com.hopcape.odo.core.domain.car.model.Car
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.garage.domain.model.GarageDocument
import com.hopcape.odo.feature.garage.domain.model.ServiceFacet
import com.hopcape.odo.feature.garage.domain.model.ServiceHistoryEntry
import com.hopcape.odo.feature.garage.domain.model.matching
import com.hopcape.odo.feature.garage.domain.model.totalSpent
import com.hopcape.odo.feature.garage.domain.model.verifiedCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * Everything the garage's home base shows, from one read each of the car, its documents
 * and its service log.
 *
 * One use case rather than three flows, because the three are read together on one screen.
 * Separate streams emit at different moments, which is how a car card ends up showing a
 * reading the history below it has already moved past.
 *
 * The documents are resolved against the day once per emission, so every row on screen
 * agrees about what "expiring soon" means.
 */
internal class ObserveGarageUseCase(
    private val cars: CarRepository,
    private val documents: DocumentRepository,
    private val logs: ServiceLogRepository,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<GarageSnapshot> = combine(
        cars.observe(carId),
        documents.observe(carId),
        logs.observe(carId),
    ) { car, documents, entries -> snapshotOf(car, documents, entries) }

    private fun snapshotOf(
        car: Car?,
        documents: List<Document>,
        entries: List<ServiceLogEntry>,
    ): GarageSnapshot {
        val today = clock.now().toLocalDateTime(timeZone).date
        return GarageSnapshot(
            car = car,
            documents = GarageDocument.rowsFor(documents, today),
            history = entries.map { it.toHistoryEntry() },
            today = today,
        )
    }

    /** The summary facts a history row shows. Line items, bill and fairness stay behind. */
    private fun ServiceLogEntry.toHistoryEntry() = ServiceHistoryEntry(
        id = id,
        servicedOn = serviceDate,
        odometer = odometer,
        amount = totalAmount,
        verification = verification,
        categories = categories,
        workshopName = workshopName,
    )
}

/**
 * The garage at one instant: the car, the papers it should be carrying, and what it has
 * been through. All three were read together, so they cannot disagree.
 *
 * [car] is `null` when the owner has no car yet — the home base's "add your car" state.
 */
internal data class GarageSnapshot(
    val car: Car?,
    val documents: List<GarageDocument>,
    val history: List<ServiceHistoryEntry>,
    /** The day the document rows were resolved against. */
    val today: LocalDate,
) {
    /** The entries under [facet] — what the history section renders under the chosen chip. */
    fun historyMatching(facet: ServiceFacet): List<ServiceHistoryEntry> = history.matching(facet)

    /** What the entries under [facet] cost together, for the section's summary line. */
    fun totalMatching(facet: ServiceFacet): Amount = historyMatching(facet).totalSpent()

    /** Documents that have lapsed or are close to it — the rows that read as work to do. */
    val documentsNeedingAttention: List<GarageDocument.OnFile>
        get() = documents.filterIsInstance<GarageDocument.OnFile>().filter { it.needsAttention }

    /** Papers the garage asks for but the car does not have on file. */
    val missingDocuments: List<GarageDocument.Missing>
        get() = documents.filterIsInstance<GarageDocument.Missing>()

    /** How many logged services are bill-backed — the trust half of the record. */
    val verifiedCount: Int get() = history.verifiedCount()
}
