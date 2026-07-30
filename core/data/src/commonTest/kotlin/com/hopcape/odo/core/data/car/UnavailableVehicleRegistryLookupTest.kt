package com.hopcape.odo.core.data.car

import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

class UnavailableVehicleRegistryLookupTest {

    private val plate = RegistrationNumber.of("MH12AB1234")!!

    @Test
    fun everyLookup_reportsTheServiceIsUnavailable() = runTest {
        val result = UnavailableVehicleRegistryLookup().lookup(plate)

        // Unavailable, not "not found": nothing was asked, so nothing was missing —
        // and the difference decides whether the UI offers a retry.
        assertIs<DomainError.LookupUnavailable>(result.leftOrNull())
    }

    @Test
    fun noPlate_everProducesAMatch() = runTest {
        val lookup = UnavailableVehicleRegistryLookup()

        // A guessed car would silently become the one every fairness benchmark and
        // per-km figure is computed against, with nothing on screen to doubt.
        listOf("MH12AB1234", "DL8CAF5031", "22BH1234AA").forEach { raw ->
            assertNull(lookup.lookup(RegistrationNumber.of(raw)!!).getOrNull())
        }
    }
}
