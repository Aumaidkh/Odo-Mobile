package com.hopcape.odo.feature.timeline.domain.usecase

import com.hopcape.odo.core.domain.activity.analysis.ActivityFeedBuilder
import com.hopcape.odo.core.domain.activity.model.ActivityEvent
import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.feature.timeline.domain.model.ActivityCategory
import com.hopcape.odo.feature.timeline.domain.model.category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone

/**
 * A car's whole timeline, as a stream.
 *
 * Gathers the four things that have ever happened to a car — the car itself, its services,
 * its documents and its score history — and hands them to [ActivityFeedBuilder], which owns
 * the merging and ordering. This use case decides only what the builder cannot see: which
 * car, and what zone its dates belong to.
 *
 * One combined read rather than four flows the screen stitches together: the header counts
 * and the rows are one fact about the car, and separate streams emit at different moments,
 * which is how a sheet ends up offering "8 services" above seven rows.
 *
 * All four sources are observed rather than read once, because each can change while the tab
 * is open — a service logged from the ledger, a policy uploaded to the vault, a score
 * recorded by the health screen, an odometer updated in the garage.
 */
internal class ObserveTimelineUseCase(
    private val cars: CarRepository,
    private val logs: ServiceLogRepository,
    private val documents: DocumentRepository,
    private val scores: HealthScoreRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<TimelineSnapshot> = combine(
        cars.observe(carId),
        logs.observe(carId),
        documents.observe(carId),
        scores.observeHistory(carId),
    ) { car, entries, documents, history ->
        TimelineSnapshot(
            carName = car?.displayName.orEmpty(),
            events = ActivityFeedBuilder.build(car, entries, documents, history, timeZone),
        )
    }
}

/**
 * The timeline's whole world at one instant: the car it belongs to and every event on it,
 * newest first and unfiltered.
 *
 * Unfiltered on purpose — the filter sheet counts what *exists*, not what is currently
 * shown, so a category the owner has unticked still says how many events turning it back on
 * would reveal.
 */
internal data class TimelineSnapshot(
    val carName: String,
    val events: List<ActivityEvent>,
) {
    /** How many events a category holds, for the sheet's row counts. */
    fun countOf(category: ActivityCategory): Int = events.count { it.category == category }
}
