package com.hopcape.odo.core.data.settings

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.cost.fuel.FuelEfficiencyUnit
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.model.NotificationPreferences
import com.hopcape.odo.core.domain.settings.model.ThemePreference
import com.hopcape.odo.core.domain.shared.DistanceUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * SQL behaviour for [SqlDelightAppSettingsLocalDataSource] — the insert-then-update upsert
 * idiom and the per-field enum fallback a row from a newer build reads back with. The
 * missing-row -> [AppSettings.Default] policy lives in [AppSettingsRepositoryImplTest]
 * instead, against a fake port.
 */
class SqlDelightAppSettingsLocalDataSourceTest {

    private fun newDb(): OdoDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        OdoDatabase.Schema.create(driver)
        return OdoDatabase(driver)
    }

    private fun local(db: OdoDatabase) = SqlDelightAppSettingsLocalDataSource(database = db, dispatcher = Dispatchers.Unconfined)

    @Test
    fun observe_beforeAnythingIsStored_emitsNull() = runTest {
        assertNull(local(newDb()).observe().first())
    }

    @Test
    fun save_thenObserve_readsEverythingBack() = runTest {
        val local = local(newDb())
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

        local.save(settings)

        assertEquals(settings, local.observe().first())
    }

    @Test
    fun save_twice_editsTheOneRowInsteadOfDuplicating() = runTest {
        val db = newDb()
        val local = local(db)

        local.save(AppSettings(theme = ThemePreference.LIGHT))
        local.save(AppSettings(theme = ThemePreference.DARK))

        assertEquals(ThemePreference.DARK, local.observe().first()?.theme)
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

        val stored = local(db).observe().first()
        assertEquals(ThemePreference.SYSTEM, stored?.theme)
        assertEquals(DistanceUnit.KILOMETRE, stored?.distanceUnit)
        assertEquals(FuelEfficiencyUnit.DISTANCE_PER_UNIT, stored?.fuelEfficiencyUnit)
    }
}
