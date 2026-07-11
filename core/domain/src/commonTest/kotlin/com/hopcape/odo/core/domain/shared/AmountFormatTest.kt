package com.hopcape.odo.core.domain.shared

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals

class AmountFormatTest {

    private fun amount(paise: Long): Amount = Amount.of(paise).getOrElse { error("valid") }

    @Test
    fun formatRupees_dropsPaise_andGroupsIndianStyle() {
        assertEquals("Rs. 2,800", amount(280_000).formatRupees())
        assertEquals("Rs. 66,000", amount(6_600_000).formatRupees())
        // Indian grouping: last 3, then 2s.
        assertEquals("Rs. 1,02,000", amount(10_200_000).formatRupees())
        assertEquals("Rs. 0", Amount.ZERO.formatRupees())
    }

    @Test
    fun formatRupeesDecimal_showsNaturalPrecision() {
        assertEquals("Rs. 4.6", amount(460).formatRupeesDecimal())   // trailing zero dropped
        assertEquals("Rs. 2.97", amount(297).formatRupeesDecimal())  // two places kept
        assertEquals("Rs. 0.86", amount(86).formatRupeesDecimal())
        assertEquals("Rs. 0.05", amount(5).formatRupeesDecimal())    // leading zero padded
        assertEquals("Rs. 5", amount(500).formatRupeesDecimal())     // whole rupee, no decimals
    }

    @Test
    fun formatRupeesCompact_abbreviatesThousandsAndLakhs() {
        assertEquals("Rs. 40k", amount(4_000_000).formatRupeesCompact())
        assertEquals("Rs. 1.5L", amount(15_000_000).formatRupeesCompact())
        assertEquals("Rs. 2L", amount(20_000_000).formatRupeesCompact())
        assertEquals("Rs. 500", amount(50_000).formatRupeesCompact())
    }
}
