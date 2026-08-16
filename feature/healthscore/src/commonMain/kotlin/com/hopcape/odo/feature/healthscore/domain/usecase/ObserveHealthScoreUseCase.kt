package com.hopcape.odo.feature.healthscore.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.repository.DocumentRepository
import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.entitlement.Entitlements
import com.hopcape.odo.core.domain.health.analysis.HealthScoreCalculator
import com.hopcape.odo.core.domain.health.repository.HealthScoreRepository
import com.hopcape.odo.core.domain.odometer.CurrentOdometerProvider
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.healthscore.domain.model.HealthScoreSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * A car's health score, as a stream.
 *
 * Gathers the three things the rules read — the car's service logs, its documents and its
 * odometer readings — and hands them to [HealthScoreCalculator], which owns the scoring.
 * This use case decides only what the calculator cannot see: what day it is, what the
 * score is compared against, and what the owner's plan lets them see.
 *
 * Every source is observed rather than read once, because every one of them can change
 * while the screen is open: a service logged from the ledger, a policy uploaded to the
 * vault, an odometer updated in the garage. A score that did not move when the owner just
 * did the thing it asked for reads as broken.
 *
 * Entitlements are observed for the same reason, and it is a newer one: a subscription used
 * to be bought outside the app, but a purchase now completes on the paywall and this screen
 * is where the owner lands afterwards. The breakdown has to unlock without a reopen.
 *
 * The score itself is never read from storage. It is computed here from today's data, so
 * a document that lapsed overnight lowers it without anything having to notice. Storage
 * holds only history, and only so [delta][HealthScoreSummary.delta] has something to
 * subtract.
 */
internal class ObserveHealthScoreUseCase(
    private val logs: ServiceLogRepository,
    private val documents: DocumentRepository,
    private val snapshots: HealthScoreRepository,
    private val entitlements: EntitlementSource,
    private val currentOdometer: CurrentOdometerProvider,
    private val clock: Clock,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    operator fun invoke(carId: CarId): Flow<HealthScoreSummary> = combine(
        logs.observe(carId),
        documents.observe(carId),
        logs.observeOdometerReadings(carId),
        currentOdometer.observeCurrent(carId),
        entitlements.observe(),
    ) { entries, documents, readings, odometer, entitlements ->
        summaryOf(carId, entries, documents, readings, odometer, entitlements)
    }

    private suspend fun summaryOf(
        carId: CarId,
        entries: List<ServiceLogEntry>,
        documents: List<Document>,
        readings: List<OdometerReading>,
        currentOdometer: Distance?,
        entitlements: Entitlements,
    ): HealthScoreSummary {
        // Resolved once per emission, so the score and the baseline are read on the same day.
        val now = clock.now()
        val score = HealthScoreCalculator.compute(
            today = now.toLocalDateTime(timeZone).date,
            entries = entries,
            documents = documents,
            readings = readings,
            currentOdometer = currentOdometer,
        )
        val baseline = snapshots.latestOnOrBefore(carId, now - DELTA_WINDOW)

        return HealthScoreSummary(
            score = score,
            delta = score.deltaFrom(baseline?.score),
            entitlements = entitlements,
        )
    }

    private companion object {
        /**
         * How far back "this month" reaches. The screen says "points this month", so the
         * baseline is the newest score that is already at least this old — not merely the
         * previous one, which could have been taken yesterday.
         */
        val DELTA_WINDOW = 30.days
    }
}
