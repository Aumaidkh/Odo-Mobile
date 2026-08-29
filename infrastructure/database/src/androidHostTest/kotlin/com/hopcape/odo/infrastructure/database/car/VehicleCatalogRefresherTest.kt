package com.hopcape.odo.infrastructure.database.car

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.car.VehicleCatalogRemoteDataSource
import com.hopcape.odo.core.data.car.VehicleCatalogSubmissionDto
import com.hopcape.odo.core.data.car.VehicleMakeDto
import com.hopcape.odo.core.data.car.VehicleModelDto
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.NoopCrash
import com.hopcape.odo.infrastructure.database.sync.NoopTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VehicleCatalogRefresherTest {

    private fun seededDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver).also(::seedVehicleReferenceData)
    }

    private val telemetry = DataTelemetry(logger = SilentLogger(), tracer = NoopTracer, crash = NoopCrash)

    @Test
    fun refresh_replacesTheLocalCacheWithWhatTheServerReturned() = runTest {
        val db = seededDb()
        val remote = FakeRemote(
            makes = listOf(VehicleMakeDto(id = "make-server-brand", name = "Server Brand", displayOrder = 0)),
            models = listOf(
                VehicleModelDto(id = "model-1", makeId = "make-server-brand", name = "Only Model", variant = null, displayOrder = 0),
            ),
        )
        val refresher = VehicleCatalogRefresher(db, remote, telemetry, dispatcher = Dispatchers.Unconfined)

        refresher.refreshInBackground()

        assertEquals(listOf("Server Brand"), db.vehicleMakeQueries.selectAllMakes().executeAsList())
        assertEquals(1L, db.vehicleModelQueries.countModels().executeAsOne())
    }

    @Test
    fun refresh_leavesTheLocalCacheAloneWhenTheServerReturnsNothing() = runTest {
        val db = seededDb()
        val before = db.vehicleMakeQueries.selectAllMakes().executeAsList()
        val refresher = VehicleCatalogRefresher(db, FakeRemote(makes = emptyList(), models = emptyList()), telemetry, dispatcher = Dispatchers.Unconfined)

        refresher.refreshInBackground()

        // An unconfigured build's fake data source (or a genuinely empty server catalog)
        // must never wipe the bundled/local-bootstrap seed out from under the picker.
        assertEquals(before, db.vehicleMakeQueries.selectAllMakes().executeAsList())
    }

    @Test
    fun refresh_leavesTheLocalCacheAloneWhenTheFetchThrows() = runTest {
        val db = seededDb()
        val before = db.vehicleMakeQueries.countMakes().executeAsOne()
        val refresher = VehicleCatalogRefresher(db, ThrowingRemote, telemetry, dispatcher = Dispatchers.Unconfined)

        refresher.refreshInBackground()

        assertEquals(before, db.vehicleMakeQueries.countMakes().executeAsOne())
        assertTrue(before > 0)
    }

    private class FakeRemote(
        private val makes: List<VehicleMakeDto>,
        private val models: List<VehicleModelDto>,
    ) : VehicleCatalogRemoteDataSource {
        override suspend fun fetchMakes(): List<VehicleMakeDto> = makes
        override suspend fun fetchModels(): List<VehicleModelDto> = models
        override suspend fun submitUnlisted(submission: VehicleCatalogSubmissionDto) = Unit
    }

    private object ThrowingRemote : VehicleCatalogRemoteDataSource {
        override suspend fun fetchMakes(): List<VehicleMakeDto> = error("network down")
        override suspend fun fetchModels(): List<VehicleModelDto> = error("network down")
        override suspend fun submitUnlisted(submission: VehicleCatalogSubmissionDto) = Unit
    }

    private class SilentLogger : Logger {
        override fun log(level: LogLevel, tag: String, event: String, traceContext: TraceContext?, fields: Map<String, Any?>) = Unit
        override fun flush() = Unit
    }
}
