package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServiceLogLineItemTest {

    @Test
    fun valid_buildsLine() {
        val result = ServiceLogLineItem.of("  Engine oil (5W-30)  ", ServiceCategory.OIL_CHANGE, 190_000)
        val line = result.getOrNull()!!
        assertEquals("Engine oil (5W-30)", line.label) // trimmed
        assertEquals(ServiceCategory.OIL_CHANGE, line.category)
        assertEquals(190_000L, line.amount.paise)
    }

    @Test
    fun blankLabel_becomesNull() {
        assertNull(ServiceLogLineItem.of("   ", ServiceCategory.BRAKES, 0).getOrNull()?.label)
    }

    @Test
    fun nullAmount_defaultsToZero() {
        assertEquals(0L, ServiceLogLineItem.of("Labour", ServiceCategory.GENERAL_SERVICE, null).getOrNull()?.amount?.paise)
    }

    @Test
    fun negativeAmount_isRejected() {
        val result = ServiceLogLineItem.of("x", ServiceCategory.AC, -1)
        assertTrue(result.isLeft())
        assertEquals(DomainError.NegativeAmount, result.leftOrNull())
    }
}
