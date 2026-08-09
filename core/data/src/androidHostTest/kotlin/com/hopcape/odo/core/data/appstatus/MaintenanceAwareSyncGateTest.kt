package com.hopcape.odo.core.data.appstatus

import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.sync.SyncGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaintenanceAwareSyncGateTest {

    @Test
    fun `allowed and session permits, sync is allowed`() = runTest {
        val session = FakeSyncGate(result = true)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Allowed))

        assertTrue(gate.canSync())
    }

    @Test
    fun `allowed but no session, sync is refused`() = runTest {
        val session = FakeSyncGate(result = false)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Allowed))

        assertFalse(gate.canSync())
    }

    @Test
    fun `degraded maintenance refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(result = true)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.DegradedByMaintenance(null)))

        assertFalse(gate.canSync())
        assertEquals(0, session.callCount, "the adoption side effect must not run mid-maintenance")
    }

    @Test
    fun `a full block refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(result = true)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Blocked.Maintenance(null)))

        assertFalse(gate.canSync())
        assertEquals(0, session.callCount)
    }

    @Test
    fun `update required refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(result = true)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Blocked.UpdateRequired))

        assertFalse(gate.canSync())
        assertEquals(0, session.callCount)
    }

    private class FakeSyncGate(private val result: Boolean) : SyncGate {
        var callCount = 0
        override suspend fun canSync(): Boolean {
            callCount++
            return result
        }
    }

    private class FakeAppStatusProvider(initial: AppAvailability) : AppStatusProvider {
        private val flow = MutableStateFlow(initial)
        override val availability: StateFlow<AppAvailability> = flow
        override suspend fun refresh() = Unit
    }
}
