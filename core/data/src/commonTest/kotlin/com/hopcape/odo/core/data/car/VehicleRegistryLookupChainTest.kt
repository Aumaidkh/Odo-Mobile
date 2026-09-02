package com.hopcape.odo.core.data.car

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.domain.car.lookup.RegisteredVehicle
import com.hopcape.odo.core.domain.car.lookup.VehicleRegistryLookup
import com.hopcape.odo.core.domain.car.lookup.VehicleSource
import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.ModelYear
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VehicleRegistryLookupChainTest {

    @Test
    fun theFirstTierThatNamesACarWins() = runTest {
        val chain = chainOf(
            tier(own.right()),
            tier(other.right()),
        )

        assertEquals(own, chain.lookup(PLATE).getOrNull())
    }

    @Test
    fun aTierWithNoRecordFallsThroughToTheNext() = runTest {
        val later = tier(other.right())
        val chain = chainOf(tier(DomainError.RegistrationNotFound.left()), later)

        assertEquals(other, chain.lookup(PLATE).getOrNull())
        assertEquals(1, later.calls)
    }

    @Test
    fun everyTierMissing_isNotFound() = runTest {
        val chain = chainOf(
            tier(DomainError.RegistrationNotFound.left()),
            tier(DomainError.RegistrationNotFound.left()),
        )

        assertIs<DomainError.RegistrationNotFound>(chain.lookup(PLATE).leftOrNull())
    }

    @Test
    fun anOfflineTierIsReportedRatherThanReadAsNoRecord() = runTest {
        // "We have no record of this plate" is permanent and sends the owner to manual
        // entry. Saying it because the network was down would be a lie with consequences.
        val chain = chainOf(
            tier(DomainError.LookupOffline.left()),
            tier(DomainError.RegistrationNotFound.left()),
        )

        assertIs<DomainError.LookupOffline>(chain.lookup(PLATE).leftOrNull())
    }

    @Test
    fun aLaterMatchStillBeatsAnEarlierFailure() = runTest {
        val chain = chainOf(
            tier(DomainError.LookupOffline.left()),
            tier(other.right()),
        )

        assertEquals(other, chain.lookup(PLATE).getOrNull())
    }

    @Test
    fun theFirstFailureIsTheOneReported() = runTest {
        // The cheapest tier that could have answered is the one whose problem matters; a
        // later tier's error is downstream of it.
        val chain = chainOf(
            tier(DomainError.LookupOffline.left()),
            tier(DomainError.LookupUnavailable.left()),
        )

        assertIs<DomainError.LookupOffline>(chain.lookup(PLATE).leftOrNull())
    }

    @Test
    fun noTierAtAll_isNotFound() = runTest {
        // What an unconfigured build's chain looks like if it ever loses its tiers: it
        // must send the owner to manual entry, not offer a retry that answers nothing.
        assertIs<DomainError.RegistrationNotFound>(chainOf().lookup(PLATE).leftOrNull())
    }

    private fun chainOf(vararg tiers: VehicleRegistryLookup) =
        ChainedVehicleRegistryLookup(tiers.toList())

    private fun tier(answer: Either<DomainError, RegisteredVehicle>) = CountingTier(answer)

    private class CountingTier(
        private val answer: Either<DomainError, RegisteredVehicle>,
    ) : VehicleRegistryLookup {
        var calls = 0
            private set

        override suspend fun lookup(
            registrationNumber: RegistrationNumber,
        ): Either<DomainError, RegisteredVehicle> {
            calls++
            return answer
        }
    }

    private companion object {
        val PLATE = RegistrationNumber.of("MH12AB1234")!!

        val own = vehicle("Swift", VehicleSource.OWN_RECORD)
        val other = vehicle("City", VehicleSource.ANOTHER_RECORD)

        fun vehicle(model: String, source: VehicleSource) = RegisteredVehicle(
            make = "Maruti Suzuki",
            model = model,
            variant = "VXI",
            year = ModelYear.of(2020).getOrNull()!!,
            fuelType = FuelType.PETROL,
            source = source,
        )
    }
}
