package com.hopcape.odo.core.data.settings

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.domain.settings.model.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [AppSettingsLocalDataSource].
 *
 * No sync columns: `app_settings` is device-local and mirrors no server table, so unlike
 * every other local data source here it neither stamps a sync status nor asks for a push
 * — that is the repository's call to make, and it does not.
 */
internal class SqlDelightAppSettingsLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AppSettingsLocalDataSource {

    private val queries get() = database.appSettingsQueries

    override suspend fun save(settings: AppSettings) {
        val now = clock.now().toString()
        val notifications = settings.notifications
        database.transaction {
            queries.insertSettings(
                theme = settings.theme.name,
                largerText = settings.largerText.toLong(),
                distanceUnit = settings.distanceUnit.name,
                fuelEfficiencyUnit = settings.fuelEfficiencyUnit.name,
                notifDocExpiry = notifications.documentExpiry.toLong(),
                notifServiceDue = notifications.serviceDue.toLong(),
                notifCustom = notifications.customReminders.toLong(),
                notifOvercharge = notifications.overchargeAlerts.toLong(),
                notifMonthly = notifications.monthlySummary.toLong(),
                notifHealthDrop = notifications.healthScoreDrops.toLong(),
                notifPartner = notifications.partnerOffers.toLong(),
                notifPush = notifications.push.toLong(),
                notifWhatsapp = notifications.whatsapp.toLong(),
                updatedAt = now,
            )
            queries.updateSettings(
                theme = settings.theme.name,
                largerText = settings.largerText.toLong(),
                distanceUnit = settings.distanceUnit.name,
                fuelEfficiencyUnit = settings.fuelEfficiencyUnit.name,
                notifDocExpiry = notifications.documentExpiry.toLong(),
                notifServiceDue = notifications.serviceDue.toLong(),
                notifCustom = notifications.customReminders.toLong(),
                notifOvercharge = notifications.overchargeAlerts.toLong(),
                notifMonthly = notifications.monthlySummary.toLong(),
                notifHealthDrop = notifications.healthScoreDrops.toLong(),
                notifPartner = notifications.partnerOffers.toLong(),
                notifPush = notifications.push.toLong(),
                notifWhatsapp = notifications.whatsapp.toLong(),
                updatedAt = now,
            )
        }
    }

    override fun observe(): Flow<AppSettings?> =
        queries.selectSettings()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
}
