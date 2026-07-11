package com.hopcape.odo.core.domain.shared

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals

class DistanceFormatTest {

    private fun km(value: Int): Distance = Distance.of(value).getOrElse { error("valid") }

    @Test
    fun formatKm_groupsIndianStyle_andSuffixesKm() {
        assertEquals("54,000 km", km(54_000).formatKm())
        assertEquals("22,200 km", km(22_200).formatKm())
        assertEquals("0 km", km(0).formatKm())
        assertEquals("1,40,000 km", km(140_000).formatKm())
    }
}
