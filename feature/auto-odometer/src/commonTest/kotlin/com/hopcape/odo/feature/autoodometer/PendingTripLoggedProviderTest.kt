package com.hopcape.odo.feature.autoodometer

import com.hopcape.odo.core.domain.trip.model.TripId
import com.hopcape.odo.core.domain.trip.model.TripStatus
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeAppSettingsRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.FakeTripRepository
import com.hopcape.odo.feature.autoodometer.domain.usecase.ObservePendingTripLogged
import com.hopcape.odo.feature.autoodometer.domain.usecase.TEST_CAR
import com.hopcape.odo.feature.autoodometer.domain.usecase.testTrip
import com.hopcape.odo.feature.autoodometer.presentation.FakeActiveCarProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Coverage for the shared-shell's cross-feature contract (plan §4.4). */
class PendingTripLoggedProviderTest {

    @Test
    fun noActiveCar_emitsNull_withoutTouchingTheUseCase() = runTest {
        val trips = FakeTripRepository(
            initial = listOf(
                testTrip(
                    id = "t1",
                    startedAt = Instant.parse("2026-01-01T08:00:00Z"),
                    endedAt = Instant.parse("2026-01-01T08:30:00Z"),
                    distanceMeters = 5_000,
                ),
            ),
        )
        val provider = DefaultPendingTripLoggedProvider(
            observePendingTripLogged = ObservePendingTripLogged(trips = trips, settings = FakeAppSettingsRepository()),
            activeCar = FakeActiveCarProvider(carId = null),
        )

        assertNull(provider.pending().first())
    }

    @Test
    fun activeCar_delegatesToTheUseCase() = runTest {
        val trip = testTrip(
            id = "t1",
            startedAt = Instant.parse("2026-01-01T08:00:00Z"),
            endedAt = Instant.parse("2026-01-01T08:30:00Z"),
            distanceMeters = 5_000,
            status = TripStatus.RECORDED,
        )
        val provider = DefaultPendingTripLoggedProvider(
            observePendingTripLogged = ObservePendingTripLogged(
                trips = FakeTripRepository(initial = listOf(trip)),
                settings = FakeAppSettingsRepository(),
            ),
            activeCar = FakeActiveCarProvider(carId = TEST_CAR),
        )

        assertEquals(TripId("t1"), provider.pending().first())
    }
}
