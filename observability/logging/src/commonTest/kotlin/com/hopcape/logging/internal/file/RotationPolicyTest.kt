package com.hopcape.logging.internal.file

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RotationPolicyTest {

    @Test
    fun maxSize_rotatesOnlyAtOrAboveTheThreshold() {
        val policy = RotationPolicy.maxSize(1_000L)

        assertFalse(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 999L, nowMs = 0L))
        assertTrue(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 1_000L, nowMs = 0L))
        assertTrue(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 2_000L, nowMs = 0L))
    }

    @Test
    fun utcMidnight_rotatesOnlyOnceTheCalendarDayChanges() {
        val policy = RotationPolicy.utcMidnight()
        val openedAtMs = 1_609_459_200_000L // 2021-01-01T00:00:00Z
        val sameDayLater = 1_609_545_599_000L // 2021-01-01T23:59:59Z
        val nextDay = 1_609_545_600_000L // 2021-01-02T00:00:00Z

        assertFalse(policy.shouldRotate(openedAtMs, activeSizeBytes = 0L, nowMs = openedAtMs))
        assertFalse(policy.shouldRotate(openedAtMs, activeSizeBytes = 0L, nowMs = sameDayLater))
        assertTrue(policy.shouldRotate(openedAtMs, activeSizeBytes = 0L, nowMs = nextDay))
    }

    @Test
    fun anyOf_rotatesWhenEitherComposedPolicyWould() {
        val policy = RotationPolicy.anyOf(RotationPolicy.maxSize(1_000L), RotationPolicy.utcMidnight())

        assertFalse(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 0L, nowMs = 0L))
        assertTrue(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 1_000L, nowMs = 0L))
        assertTrue(policy.shouldRotate(openedAtMs = 0L, activeSizeBytes = 0L, nowMs = 86_400_000L))
    }
}
