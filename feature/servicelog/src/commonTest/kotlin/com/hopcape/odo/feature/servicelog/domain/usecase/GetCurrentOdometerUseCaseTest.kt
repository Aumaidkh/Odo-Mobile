package com.hopcape.odo.feature.servicelog.domain.usecase

import com.hopcape.odo.core.domain.car.model.CarId
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.feature.servicelog.presentation.FakeCurrentOdometerProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetCurrentOdometerUseCaseTest {

    private val carId = CarId("car-1")

    private fun km(value: Int): Distance = Distance.of(value).let { it.getOrNull()!! }

    @Test
    fun withNoReadingAtAll_returnsNull() = runTest {
        val useCase = GetCurrentOdometerUseCase(FakeCurrentOdometerProvider(null))

        assertNull(useCase(carId))
    }

    @Test
    fun returnsWhateverTheProviderSays_tripsIncluded() = runTest {
        // The provider already folds a counted trip on top of the manual reading — this
        // use case just has to hand that number to the form, not recompute it.
        val useCase = GetCurrentOdometerUseCase(FakeCurrentOdometerProvider(km(505)))

        assertEquals(505, useCase(carId)?.km)
    }
}
