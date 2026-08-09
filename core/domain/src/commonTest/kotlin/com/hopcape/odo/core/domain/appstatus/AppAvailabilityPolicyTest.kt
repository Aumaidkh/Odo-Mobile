package com.hopcape.odo.core.domain.appstatus

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val NOW = Instant.fromEpochMilliseconds(1_700_000_000_000)
private const val CURRENT_VERSION = 100L

class AppAvailabilityPolicyTest {

    @Test
    fun `below min version and fresh maintenance off blocks for update`() {
        val status = statusOf(minVersion = 200L, maintenance = MaintenanceSeverity.OFF, fetchedAt = NOW)

        assertEquals(AppAvailability.Blocked.UpdateRequired, evaluate(status))
    }

    @Test
    fun `below min version with no fetch ever still blocks for update`() {
        val status = statusOf(minVersion = 200L, maintenance = MaintenanceSeverity.OFF, fetchedAt = null)

        assertEquals(AppAvailability.Blocked.UpdateRequired, evaluate(status))
    }

    @Test
    fun `update required outranks a fresh full block`() {
        val status = statusOf(minVersion = 200L, maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = NOW)

        assertEquals(AppAvailability.Blocked.UpdateRequired, evaluate(status))
    }

    @Test
    fun `at or above min version with fresh full block is blocked for maintenance`() {
        val status = statusOf(minVersion = 100L, maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = NOW, message = "down for migration")

        assertEquals(AppAvailability.Blocked.Maintenance("down for migration"), evaluate(status))
    }

    @Test
    fun `stale full block is allowed rather than holding an offline owner hostage`() {
        val staleFetch = NOW - DEFAULT_MAINTENANCE_TRUST_WINDOW - 1.minutes
        val status = statusOf(minVersion = 100L, maintenance = MaintenanceSeverity.FULL_BLOCK, fetchedAt = staleFetch)

        assertEquals(AppAvailability.Allowed, evaluate(status))
    }

    @Test
    fun `fresh degraded maintenance degrades rather than blocks`() {
        val status = statusOf(minVersion = 100L, maintenance = MaintenanceSeverity.DEGRADED, fetchedAt = NOW, message = "read-only for now")

        assertEquals(AppAvailability.DegradedByMaintenance("read-only for now"), evaluate(status))
    }

    @Test
    fun `maintenance off is allowed`() {
        val status = statusOf(minVersion = 100L, maintenance = MaintenanceSeverity.OFF, fetchedAt = NOW)

        assertEquals(AppAvailability.Allowed, evaluate(status))
    }

    @Test
    fun `min version zero never blocks regardless of current version`() {
        val status = statusOf(minVersion = 0L, maintenance = MaintenanceSeverity.OFF, fetchedAt = null)

        assertEquals(AppAvailability.Allowed, evaluate(status, currentVersionCode = 1L))
    }

    @Test
    fun `maintenance exactly at the trust window boundary is still fresh`() {
        val status = statusOf(
            minVersion = 100L,
            maintenance = MaintenanceSeverity.FULL_BLOCK,
            fetchedAt = NOW - DEFAULT_MAINTENANCE_TRUST_WINDOW,
        )

        assertEquals(AppAvailability.Blocked.Maintenance(null), evaluate(status))
    }

    private fun statusOf(
        minVersion: Long,
        maintenance: MaintenanceSeverity,
        fetchedAt: Instant?,
        message: String? = null,
    ) = AppStatus(
        minSupportedVersionCode = minVersion,
        maintenance = maintenance,
        maintenanceMessage = message,
        fetchedAt = fetchedAt,
    )

    private fun evaluate(status: AppStatus, currentVersionCode: Long = CURRENT_VERSION) =
        evaluateAvailability(status, currentVersionCode, NOW)
}
