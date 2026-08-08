package com.hopcape.odo.infrastructure.database.trip

import com.hopcape.odo.core.triptracker.model.LocationSample
import com.hopcape.odo.core.triptracker.model.SessionSnapshot
import com.hopcape.odo.core.triptracker.port.TripSessionStore
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * SQLDelight-backed [TripSessionStore] — the crash-resume journal (TRIPTRACKER_PLAN §5.2),
 * one row, pinned to id 1.
 *
 * [SessionSnapshot.lastFix]'s monotonic `elapsed` does not survive a process restart (it
 * is measured against a clock that resets on reboot), so a restored fix always comes back
 * with [Duration.ZERO] — the next real fix re-anchors the integrator, at the same
 * worst-case cost already accepted for a kill mid-trip.
 */
internal class TripSessionStoreImpl(private val database: OdoDatabase) : TripSessionStore {

    private val queries get() = database.tripSessionQueries

    override suspend fun save(snapshot: SessionSnapshot) {
        database.transaction {
            queries.insertSession(
                phase = snapshot.phase,
                carId = snapshot.carId,
                mode = snapshot.mode,
                startedAt = snapshot.startedAt?.toString(),
                distanceM = snapshot.distanceMeters,
                estimatedM = snapshot.estimatedMeters,
                lastFixLat = snapshot.lastFix?.lat,
                lastFixLon = snapshot.lastFix?.lon,
                lastFixAccuracyM = snapshot.lastFix?.accuracyM?.toDouble(),
                lastFixSpeedMps = snapshot.lastFix?.speedMps?.toDouble(),
                lastFixAt = snapshot.lastFix?.at?.toString(),
                stitchDeadline = snapshot.stitchDeadline?.toString(),
            )
            queries.updateSession(
                phase = snapshot.phase,
                carId = snapshot.carId,
                mode = snapshot.mode,
                startedAt = snapshot.startedAt?.toString(),
                distanceM = snapshot.distanceMeters,
                estimatedM = snapshot.estimatedMeters,
                lastFixLat = snapshot.lastFix?.lat,
                lastFixLon = snapshot.lastFix?.lon,
                lastFixAccuracyM = snapshot.lastFix?.accuracyM?.toDouble(),
                lastFixSpeedMps = snapshot.lastFix?.speedMps?.toDouble(),
                lastFixAt = snapshot.lastFix?.at?.toString(),
                stitchDeadline = snapshot.stitchDeadline?.toString(),
            )
        }
    }

    override suspend fun load(): SessionSnapshot? =
        queries.selectSession(::sessionFromRow).executeAsOneOrNull()

    override suspend fun clear() {
        queries.clearSession()
    }
}

@Suppress("LongParameterList", "UNUSED_PARAMETER")
internal fun sessionFromRow(
    id: Long,
    phase: String,
    carId: String?,
    mode: String?,
    startedAt: String?,
    distanceM: Long,
    estimatedM: Long,
    lastFixLat: Double?,
    lastFixLon: Double?,
    lastFixAccuracyM: Double?,
    lastFixSpeedMps: Double?,
    lastFixAt: String?,
    stitchDeadline: String?,
): SessionSnapshot = SessionSnapshot(
    phase = phase,
    carId = carId,
    mode = mode,
    startedAt = startedAt?.let { Instant.parse(it) },
    distanceMeters = distanceM,
    estimatedMeters = estimatedM,
    lastFix = lastFixLat?.let { lat ->
        LocationSample(
            at = lastFixAt?.let { Instant.parse(it) } ?: Instant.fromEpochSeconds(0),
            elapsed = Duration.ZERO,
            lat = lat,
            lon = lastFixLon ?: 0.0,
            accuracyM = (lastFixAccuracyM ?: 0.0).toFloat(),
            speedMps = lastFixSpeedMps?.toFloat(),
        )
    },
    stitchDeadline = stitchDeadline?.let { Instant.parse(it) },
)
