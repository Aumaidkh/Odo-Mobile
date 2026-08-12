package com.hopcape.crashreporting.internal.breadcrumb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BreadcrumbTrailTest {

    @Test
    fun retainsCrumbsInOrder() {
        val trail = BreadcrumbTrail(maxSize = 10)
        trail.add("AUTH", "login_started")
        trail.add("AUTH", "login_succeeded")

        val snapshot = trail.snapshot()
        assertEquals(listOf("login_started", "login_succeeded"), snapshot.map { it.message })
        assertEquals("AUTH", snapshot.first().tag)
    }

    @Test
    fun evictsOldestPastMaxSize() {
        val trail = BreadcrumbTrail(maxSize = 3)
        repeat(5) { trail.add("T", "crumb-$it") }

        val snapshot = trail.snapshot()
        assertEquals(3, snapshot.size, "trail must stay bounded at maxSize")
        // Oldest two (crumb-0, crumb-1) evicted; newest three remain in order.
        assertEquals(listOf("crumb-2", "crumb-3", "crumb-4"), snapshot.map { it.message })
    }

    @Test
    fun clearEmptiesTrail() {
        val trail = BreadcrumbTrail()
        trail.add("T", "x")
        trail.clear()
        assertTrue(trail.snapshot().isEmpty())
    }

    @Test
    fun snapshotIsAStableCopy() {
        val trail = BreadcrumbTrail()
        trail.add("T", "first")
        val snapshot = trail.snapshot()
        trail.add("T", "second")
        // The earlier snapshot must not see the later addition.
        assertEquals(1, snapshot.size)
    }
}
