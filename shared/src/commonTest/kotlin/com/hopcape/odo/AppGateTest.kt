package com.hopcape.odo

import com.hopcape.odo.core.domain.appstatus.AppAvailability
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Coverage for the app-shell's app-status gate (docs/APP_STATUS_PLAN.md). */
class AppGateTest {

    @Test
    fun allowed_doesNotBlock() {
        assertFalse(shouldBlock(AppAvailability.Allowed))
    }

    @Test
    fun degradedByMaintenance_doesNotBlock_onlyTheBannerShows() {
        assertFalse(shouldBlock(AppAvailability.DegradedByMaintenance(message = null)))
    }

    @Test
    fun updateRequired_blocks() {
        assertTrue(shouldBlock(AppAvailability.Blocked.UpdateRequired))
    }

    @Test
    fun maintenanceBlock_blocks() {
        assertTrue(shouldBlock(AppAvailability.Blocked.Maintenance(message = "down for migration")))
    }
}
