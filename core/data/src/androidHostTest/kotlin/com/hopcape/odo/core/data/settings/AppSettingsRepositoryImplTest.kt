package com.hopcape.odo.core.data.settings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import com.hopcape.performance.api.PerformanceTracer
import com.hopcape.performance.api.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppSettingsRepositoryImplTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun repo(db: OdoDatabase) = AppSettingsRepositoryImpl(
        local = SqlDelightAppSettingsLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined),
        telemetry = DataTelemetry(logger = NoopLogger, tracer = NoopTracer, crash = NoopCrash),
    )

    @Test
    fun observe_beforeAnythingIsStored_emitsTheDefaults() = runTest {
        assertEquals(AppSettings.Default, repo(newDb()).observe().first())
    }

    @Test
    fun save_thenObserve_readsEverythingBack() = runTest {
        val repo = repo(newDb())
        val settings = AppSettings(
            theme = ThemePreference.DARK,
            largerText = true,
            distanceUnit = DistanceUnit.MILE,
            fuelEfficiencyUnit = FuelEfficiencyUnit.UNITS_PER_100KM,
            notifications = NotificationPreferences(
                documentExpiry = false,
                serviceDue = true,
                customReminders = true,
                overchargeAlerts = false,
                monthlySummary = false,
                healthScoreDrops = true,
                partnerOffers = true,
                push = false,
                whatsapp = true,
            ),
        )

        assertTrue(repo.save(settings).isRight())

        assertEquals(settings, repo.observe().first())
    }

    @Test
    fun save_twice_editsTheOneRowInsteadOfDuplicating() = runTest {
        val db = newDb()
        val repo = repo(db)

        assertTrue(repo.save(AppSettings(theme = ThemePreference.LIGHT)).isRight())
        assertTrue(repo.save(AppSettings(theme = ThemePreference.DARK)).isRight())

        assertEquals(ThemePreference.DARK, repo.observe().first().theme)
        assertEquals(1, db.appSettingsQueries.selectSettings().executeAsList().size)
    }

    @Test
    fun observe_unreadableStoredValues_readAsDefaultsRatherThanCrashing() = runTest {
        val db = newDb()
        // A row written by a newer build. Settings are a preference: the app has to
        // start, so an unknown value reads as the default.
        db.appSettingsQueries.insertSettings(
            theme = "NEON",
            largerText = 0,
            distanceUnit = "FURLONG",
            fuelEfficiencyUnit = "MPG",
            notifDocExpiry = 1,
            notifServiceDue = 1,
            notifCustom = 0,
            notifOvercharge = 1,
            notifMonthly = 1,
            notifHealthDrop = 0,
            notifPartner = 0,
            notifPush = 1,
            notifWhatsapp = 0,
            updatedAt = "2026-08-01T10:00:00Z",
        )

        val stored = repo(db).observe().first()
        assertEquals(ThemePreference.SYSTEM, stored.theme)
        assertEquals(DistanceUnit.KILOMETRE, stored.distanceUnit)
        assertEquals(FuelEfficiencyUnit.DISTANCE_PER_UNIT, stored.fuelEfficiencyUnit)
    }

    private object NoopLogger : Logger {
        override fun log(
            level: LogLevel,
            tag: String,
            event: String,
            traceContext: TraceContext?,
            fields: Map<String, Any?>,
        ) = Unit
        override fun flush() = Unit
    }

    private class FakeSpan(
        override val spanId: String,
        override val traceId: String,
        override val parentSpanId: String?,
        override val name: String,
    ) : Span {
        override fun setAttribute(key: String, value: Any?): Span = this
    }

    private object NoopTracer : PerformanceTracer {
        override fun startSpan(name: String, traceId: String, parentSpanId: String?): Span =
            FakeSpan("span", traceId, parentSpanId, name)
        override fun endSpan(span: Span) = Unit
        override fun flush() = Unit
    }

    private object NoopCrash : CrashRecorder {
        override fun recordNonFatal(throwable: Throwable, customKeys: Map<String, Any?>) = Unit
        override fun leaveBreadcrumb(tag: String, message: String) = Unit
        override fun setCustomKey(key: String, value: Any?) = Unit
        override fun setUserId(userId: String?) = Unit
    }
}
