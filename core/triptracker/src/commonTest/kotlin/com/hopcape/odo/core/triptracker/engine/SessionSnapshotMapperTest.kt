package com.hopcape.odo.core.triptracker.engine

import com.hopcape.odo.core.domain.trip.model.TripMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class SessionSnapshotMapperTest {

    private val t0 = Instant.fromEpochSeconds(1_700_000_000)
    private val session = TripSession(
        startedAt = t0,
        mode = TripMode.GPS_ONLY,
        distanceMeters = 4_200,
        estimatedMeters = 300,
        attributionConfident = true,
    )

    @Test
    fun tracking_roundTrips() {
        val snapshot = SessionSnapshotMapper.toSnapshot(TripPhase.Tracking(session))
        checkNotNull(snapshot)
        val restored = assertIs<TripPhase.Tracking>(SessionSnapshotMapper.fromSnapshot(snapshot))
        assertEquals(session.startedAt, restored.session.startedAt)
        assertEquals(session.mode, restored.session.mode)
        assertEquals(session.distanceMeters, restored.session.distanceMeters)
        assertEquals(session.estimatedMeters, restored.session.estimatedMeters)
        // Not persisted — a resumed session is conservatively "not confident".
        assertEquals(false, restored.session.attributionConfident)
    }

    @Test
    fun pendingStop_roundTripsTheDeadline() {
        val deadline = t0 + 5.minutes
        val snapshot = SessionSnapshotMapper.toSnapshot(TripPhase.PendingStop(session, deadline))
        checkNotNull(snapshot)
        val restored = assertIs<TripPhase.PendingStop>(SessionSnapshotMapper.fromSnapshot(snapshot))
        assertEquals(deadline, restored.stopDeadline)
    }

    @Test
    fun standbyAndDisabled_haveNoSnapshot() {
        assertNull(SessionSnapshotMapper.toSnapshot(TripPhase.Standby()))
        assertNull(SessionSnapshotMapper.toSnapshot(TripPhase.Disabled))
    }
}
