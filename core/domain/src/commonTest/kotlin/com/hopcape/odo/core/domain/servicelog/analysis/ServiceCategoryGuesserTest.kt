package com.hopcape.odo.core.domain.servicelog.analysis

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServiceCategoryGuesserTest {

    @Test
    fun jobsAreRecognisedFromHowWorkshopsWriteThem() {
        val expected = mapOf(
            "Engine oil 5W-30" to ServiceCategory.OIL_CHANGE,
            "OIL CHANGE" to ServiceCategory.OIL_CHANGE,
            "Oil filter (Bosch)" to ServiceCategory.OIL_CHANGE,
            "Front brake pad set" to ServiceCategory.BRAKES,
            "Brake shoe replacement" to ServiceCategory.BRAKES,
            "Wheel alignment & balancing" to ServiceCategory.TYRES,
            "Puncture repair" to ServiceCategory.TYRES,
            "A/C gas top up" to ServiceCategory.AC,
            "AC GAS REFILLING" to ServiceCategory.AC,
            "Battery replacement (Exide)" to ServiceCategory.BATTERY,
            "Rear shock absorber" to ServiceCategory.SUSPENSION,
            "Shocker replacement" to ServiceCategory.SUSPENSION,
            "Alternator repair" to ServiceCategory.ELECTRICAL,
            "Periodic service - 40000 km" to ServiceCategory.GENERAL_SERVICE,
            "Paid service" to ServiceCategory.GENERAL_SERVICE,
        )

        expected.forEach { (label, category) ->
            assertEquals(category, ServiceCategoryGuesser.of(label), "label=<$label>")
        }
    }

    @Test
    fun brakeFluidIsBrakeWork_notAnOilChange() {
        assertEquals(ServiceCategory.BRAKES, ServiceCategoryGuesser.of("Brake oil / fluid"))
    }

    @Test
    fun anAcCompressorIsAcWork_notElectrical() {
        assertEquals(ServiceCategory.AC, ServiceCategoryGuesser.of("AC compressor overhaul"))
    }

    @Test
    fun labourIsNeverAJob() {
        // A city average is what a whole job costs. Benchmarking a labour line against one
        // would report a saving nobody made.
        listOf("Labour charges", "Labour", "Service charges", "Workshop labour").forEach { label ->
            assertNull(ServiceCategoryGuesser.of(label), "label=<$label>")
        }
    }

    @Test
    fun partsInsideAServiceStayUnjudged() {
        listOf("Air filter", "Cabin filter", "Spark plug set", "Coolant top up", "Wiper blades").forEach { label ->
            assertNull(ServiceCategoryGuesser.of(label), "label=<$label>")
        }
    }

    @Test
    fun nothingToReadIsNoGuess() {
        listOf(null, "", "   ", "Misc", "Sundries", "1234").forEach { label ->
            assertNull(ServiceCategoryGuesser.of(label), "label=<$label>")
        }
    }
}
