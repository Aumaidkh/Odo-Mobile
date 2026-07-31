package com.hopcape.odo.feature.garage.domain.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceFacetTest {

    @Test
    fun all_takesEveryEntry() {
        assertTrue(ServiceFacet.ALL.accepts(setOf(ServiceCategory.TYRES)))
        assertTrue(ServiceFacet.ALL.accepts(emptySet()))
    }

    @Test
    fun service_groupsRoutineWorkshopWork() {
        assertTrue(ServiceFacet.SERVICE.accepts(setOf(ServiceCategory.GENERAL_SERVICE)))
        assertTrue(ServiceFacet.SERVICE.accepts(setOf(ServiceCategory.BRAKES)))
        assertTrue(ServiceFacet.SERVICE.accepts(setOf(ServiceCategory.OTHER)))
        assertFalse(ServiceFacet.SERVICE.accepts(setOf(ServiceCategory.TYRES)))
    }

    @Test
    fun tyresAndBattery_takeOnlyTheirOwn() {
        assertTrue(ServiceFacet.TYRES.accepts(setOf(ServiceCategory.TYRES)))
        assertFalse(ServiceFacet.TYRES.accepts(setOf(ServiceCategory.BATTERY)))
        assertTrue(ServiceFacet.BATTERY.accepts(setOf(ServiceCategory.BATTERY)))
        assertFalse(ServiceFacet.BATTERY.accepts(setOf(ServiceCategory.AC)))
    }

    /** A visit that did two things shows under both chips, because both happened. */
    @Test
    fun multiTaggedEntry_showsUnderEveryChipItMatches() {
        val categories = setOf(ServiceCategory.GENERAL_SERVICE, ServiceCategory.BATTERY)

        assertTrue(ServiceFacet.SERVICE.accepts(categories))
        assertTrue(ServiceFacet.BATTERY.accepts(categories))
        assertFalse(ServiceFacet.TYRES.accepts(categories))
    }

    /** Untagged work is still workshop work — it reads as Service, not as nothing. */
    @Test
    fun untaggedEntry_readsAsService() {
        assertTrue(ServiceFacet.SERVICE.accepts(emptySet()))
        assertFalse(ServiceFacet.TYRES.accepts(emptySet()))
        assertFalse(ServiceFacet.BATTERY.accepts(emptySet()))
    }
}
