package com.hopcape.odo.core.domain.shared

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceFormatTest {

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("valid") }

    @Test
    fun formatKm_groupsIndianStyle_andSuffixesKm() {
        assertEquals("54,000 km", km(54_000).format(DistanceUnit.KILOMETRE))
        assertEquals("22,200 km", km(22_200).format(DistanceUnit.KILOMETRE))
        assertEquals("0 km", km(0).format(DistanceUnit.KILOMETRE))
        assertEquals("1,40,000 km", km(140_000).format(DistanceUnit.KILOMETRE))
    }
}
