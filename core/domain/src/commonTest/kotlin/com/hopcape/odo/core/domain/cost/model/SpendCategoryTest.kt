package com.hopcape.odo.core.domain.cost.model

import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class SpendCategoryTest {

    @Test
    fun brokenThings_areRepairs() {
        listOf(
            ServiceCategory.BRAKES,
            ServiceCategory.SUSPENSION,
            ServiceCategory.ELECTRICAL,
            ServiceCategory.AC,
            ServiceCategory.BATTERY,
        ).forEach { category ->
            assertEquals(SpendCategory.REPAIRS, SpendCategory.of(category), "$category")
        }
    }

    @Test
    fun routineUpkeep_isService() {
        listOf(
            ServiceCategory.OIL_CHANGE,
            ServiceCategory.GENERAL_SERVICE,
            ServiceCategory.TYRES,
            ServiceCategory.OTHER,
        ).forEach { category ->
            assertEquals(SpendCategory.SERVICE, SpendCategory.of(category), "$category")
        }
    }

    @Test
    fun anEntryWithOneRepairTag_isARepair() {
        val categories = setOf(ServiceCategory.OIL_CHANGE, ServiceCategory.BRAKES)

        assertEquals(SpendCategory.REPAIRS, SpendCategory.forEntry(categories))
    }

    @Test
    fun anUntaggedEntry_readsAsService() {
        assertEquals(SpendCategory.SERVICE, SpendCategory.forEntry(emptySet()))
    }
}
