package com.hopcape.odo.feature.onboarding.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class LookupPlateUseCaseTest {

    private class FakeRegistry(
        private val answer: Either<DomainError, RegisteredVehicle>,
    ) : VehicleRegistryLookup {
        var lastPlate: RegistrationNumber? = null
        var callCount = 0
        override suspend fun lookup(
            registrationNumber: RegistrationNumber,
        ): Either<DomainError, RegisteredVehicle> {
            callCount++
            lastPlate = registrationNumber
            return answer
        }
    }

    private val swift = RegisteredVehicle(
        make = "Maruti Suzuki",
        model = "Swift",
        variant = "VXI",
        year = ModelYear.of(2020).getOrNull()!!,
        fuelType = FuelType.PETROL,
    )

    @Test
    fun plateIsNormalizedBeforeItReachesTheRegistry() = runTest {
        val registry = FakeRegistry(swift.right())

        val result = LookupPlateUseCase(registry)("  mh 12 ab 1234 ")

        assertEquals("MH12AB1234", registry.lastPlate?.value)
        assertEquals(swift, result.getOrNull())
    }

    @Test
    fun blankPlate_neverCallsTheRegistry() = runTest {
        val registry = FakeRegistry(swift.right())

        val result = LookupPlateUseCase(registry)("   ")

        // A guaranteed-empty answer isn't worth a round trip.
        assertEquals(0, registry.callCount)
        assertIs<DomainError.BlankRegistrationNumber>(result.leftOrNull())
    }

    @Test
    fun nullPlate_isRejectedWithoutCallingTheRegistry() = runTest {
        val registry = FakeRegistry(swift.right())

        assertIs<DomainError.BlankRegistrationNumber>(LookupPlateUseCase(registry)(null).leftOrNull())
        assertEquals(0, registry.callCount)
    }

    @Test
    fun notFound_isSurfacedAsIsAndInventsNothing() = runTest {
        val registry = FakeRegistry(DomainError.RegistrationNotFound.left())

        val result = LookupPlateUseCase(registry)("MH12AB1234")

        // A wrong match would poison every benchmark derived from the car, so a miss
        // stays a miss.
        assertIs<DomainError.RegistrationNotFound>(result.leftOrNull())
        assertNull(result.getOrNull())
    }

    @Test
    fun retryableFailures_keepTheirDistinctReasons() = runTest {
        // Offline and service-down must not collapse into "not found" — one tells the
        // owner to give up, the others are worth another tap.
        assertIs<DomainError.LookupOffline>(
            LookupPlateUseCase(FakeRegistry(DomainError.LookupOffline.left()))("MH12AB1234").leftOrNull(),
        )
        assertIs<DomainError.LookupUnavailable>(
            LookupPlateUseCase(FakeRegistry(DomainError.LookupUnavailable.left()))("MH12AB1234").leftOrNull(),
        )
    }
}
