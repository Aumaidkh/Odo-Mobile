package com.hopcape.odo.core.data.settings

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [AppSettingsRepository].
 *
 * No sync: `app_settings` is device-local and mirrors no server table, so unlike every
 * other repository here it neither stamps a sync status nor asks the scheduler for a push.
 *
 * A failed read falls back to [AppSettings.Default] instead of failing the flow. The app's
 * theme and units hang off this, so an unreadable row must leave a usable app — but it is
 * reported, because "never set" and "cannot be read" look identical from the outside.
 */
internal class AppSettingsRepositoryImpl(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AppSettingsRepository {

    private val queries get() = database.appSettingsQueries

    override fun observe(): Flow<AppSettings> =
        queries.selectSettings()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() ?: AppSettings.Default }
            .catch { e ->
                telemetry.crashed(ENTITY, OP_OBSERVE, e)
                emit(AppSettings.Default)
            }

    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> =
        telemetry.span(ENTITY, OP_SAVE) {
            try {
                val now = clock.now().toString()
                val notifications = settings.notifications
                // Insert-then-update in one transaction: the insert is ignored once the
                // row exists, so on every later save the UPDATE is what writes.
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
                settings.right()
            } catch (e: Exception) {
                telemetry.crashed(ENTITY, OP_SAVE, e)
                DomainError.PersistenceFailure(e.message).left()
            }
        }

    private companion object {
        const val ENTITY = "settings"
        const val OP_OBSERVE = "observe"
        const val OP_SAVE = "save"
    }
}
