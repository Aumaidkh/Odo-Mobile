package com.hopcape.odo.core.data.appstatus

import com.hopcape.odo.core.domain.appstatus.AppAvailability
import com.hopcape.odo.core.domain.appstatus.AppStatusProvider
import com.hopcape.odo.core.sync.SyncGate
import com.hopcape.odo.core.sync.SyncVerdict
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MaintenanceAwareSyncGateTest {

    @Test
    fun `allowed and session permits, sync is allowed`() = runTest {
        val session = FakeSyncGate(SyncVerdict.Allowed)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Allowed))

        assertEquals(SyncVerdict.Allowed, gate.evaluate())
    }

    @Test
    fun `allowed but no session, the session gate's own verdict is passed through`() = runTest {
        val verdict = SyncVerdict.NoSession("not signed in")
        val gate = MaintenanceAwareSyncGate(FakeSyncGate(verdict), FakeAppStatusProvider(AppAvailability.Allowed))

        assertEquals(verdict, gate.evaluate())
    }

    @Test
    fun `degraded maintenance refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(SyncVerdict.Allowed)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.DegradedByMaintenance(null)))

        // Unavailable, not NoSession: a maintenance window ends, and a run recorded as done
        // is a run nothing asks for again (issue #312).
        assertIs<SyncVerdict.Unavailable>(gate.evaluate())
        assertEquals(0, session.callCount, "the adoption side effect must not run mid-maintenance")
    }

    @Test
    fun `a full block refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(SyncVerdict.Allowed)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Blocked.Maintenance(null)))

        assertIs<SyncVerdict.Unavailable>(gate.evaluate())
        assertEquals(0, session.callCount)
    }

    @Test
    fun `update required refuses without ever asking the session gate`() = runTest {
        val session = FakeSyncGate(SyncVerdict.Allowed)
        val gate = MaintenanceAwareSyncGate(session, FakeAppStatusProvider(AppAvailability.Blocked.UpdateRequired))

        assertIs<SyncVerdict.Unavailable>(gate.evaluate())
        assertEquals(0, session.callCount)
    }

    private class FakeSyncGate(private val verdict: SyncVerdict) : SyncGate {
        var callCount = 0
        override suspend fun evaluate(): SyncVerdict {
            callCount++
            return verdict
        }
    }

    private class FakeAppStatusProvider(initial: AppAvailability) : AppStatusProvider {
        private val flow = MutableStateFlow(initial)
        override val availability: StateFlow<AppAvailability> = flow
        override suspend fun refresh() = Unit
    }
}
