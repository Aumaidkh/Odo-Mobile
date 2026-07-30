package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.domain.car.model.FuelType
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class StubVehicleRegistryLookupTest {

    private val lookup = StubVehicleRegistryLookup()

    private suspend fun lookup(raw: String) = lookup.lookup(RegistrationNumber.of(raw)!!)

    @Test
    fun knownPlate_returnsItsVehicle() = runTest {
        val vehicle = lookup("JK03N3078").getOrNull()

        assertNotNull(vehicle)
        assertEquals("Maruti Suzuki", vehicle.make)
        assertEquals("Swift", vehicle.model)
        assertEquals("VXI", vehicle.variant)
        assertEquals(2020, vehicle.year.value)
        assertEquals(FuelType.PETROL, vehicle.fuelType)
    }

    @Test
    fun knownPlate_isMatchedAfterNormalization() = runTest {
        // The owner types spaces and lowercase; the map is keyed on the stored form.
        val typed = lookup("jk03n 3078").getOrNull()

        assertEquals(lookup("JK03N3078").getOrNull(), typed)
    }

    @Test
    fun unknownPlate_isNotFoundRatherThanUnavailable() = runTest {
        // The lookup answered and has no record, so retrying can't help — the owner
        // belongs in manual entry, not behind a retry button.
        listOf("MH12AB1234", "DL8CAF5031", "22BH1234AA").forEach { plate ->
            assertIs<DomainError.RegistrationNotFound>(lookup(plate).leftOrNull(), plate)
        }
    }
}
