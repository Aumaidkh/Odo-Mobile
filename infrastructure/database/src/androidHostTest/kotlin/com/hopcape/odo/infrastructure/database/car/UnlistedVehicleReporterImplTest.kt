package com.hopcape.odo.infrastructure.database.car

import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.core.data.car.VehicleMakeDto
import com.hopcape.odo.core.data.car.VehicleModelDto
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.infrastructure.database.sync.NoopCrash
import com.hopcape.odo.infrastructure.database.sync.NoopTracer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UnlistedVehicleReporterImplTest {

    private val telemetry = DataTelemetry(logger = SilentLogger(), tracer = NoopTracer, crash = NoopCrash)

    @Test
    fun report_pushesTheSubmissionUnderTheSignedInOwner() = runTest {
        val remote = RecordingRemote()
        val reporter = UnlistedVehicleReporterImpl(remote = remote, owner = { OwnerId("owner-1") }, telemetry = telemetry)

        reporter.report(make = "Rare Motors", model = "Concept One", variant = "Turbo")

        val submitted = remote.submissions.single()
        assertEquals("owner-1", submitted.ownerId)
        assertEquals("Rare Motors", submitted.make)
        assertEquals("Concept One", submitted.model)
        assertEquals("Turbo", submitted.variant)
    }

    @Test
    fun report_isSkippedBeforeAnyoneHasSignedIn() = runTest {
        val remote = RecordingRemote()
        val reporter = UnlistedVehicleReporterImpl(remote = remote, owner = { OwnerId.LOCAL_PLACEHOLDER }, telemetry = telemetry)

        reporter.report(make = "Rare Motors", model = "Concept One", variant = null)

        // RLS would refuse this anyway (owner_id = auth.uid()); the point is that it never
        // even tries, so a pre-sign-in save never shows up as a failed request.
        assertTrue(remote.submissions.isEmpty())
    }

    @Test
    fun report_neverThrowsWhenTheServerRejectsTheSubmission() = runTest {
        val reporter = UnlistedVehicleReporterImpl(remote = ThrowingRemote, owner = { OwnerId("owner-1") }, telemetry = telemetry)

        // The whole contract: saving the owner's car must never depend on this succeeding.
        reporter.report(make = "Rare Motors", model = "Concept One", variant = null)
    }

    private class RecordingRemote : VehicleCatalogRemoteDataSource {
        val submissions = mutableListOf<VehicleCatalogSubmissionDto>()
        override suspend fun fetchMakes(): List<VehicleMakeDto> = emptyList()
        override suspend fun fetchModels(): List<VehicleModelDto> = emptyList()
        override suspend fun submitUnlisted(submission: VehicleCatalogSubmissionDto) {
            submissions += submission
        }
    }

    private object ThrowingRemote : VehicleCatalogRemoteDataSource {
        override suspend fun fetchMakes(): List<VehicleMakeDto> = emptyList()
        override suspend fun fetchModels(): List<VehicleModelDto> = emptyList()
        override suspend fun submitUnlisted(submission: VehicleCatalogSubmissionDto): Nothing = error("rejected")
    }

    private class SilentLogger : Logger {
        override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) = Unit
        override fun flush() = Unit
    }
}
