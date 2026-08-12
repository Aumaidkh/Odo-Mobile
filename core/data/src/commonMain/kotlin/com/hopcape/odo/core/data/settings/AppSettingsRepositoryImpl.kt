package com.hopcape.odo.core.data.settings

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * [AppSettingsRepository] over an [AppSettingsLocalDataSource].
 *
 * No sync: `app_settings` is device-local and mirrors no server table, so unlike every
 * other repository here it neither stamps a sync status nor asks the scheduler for a push.
 *
 * A failed or missing read falls back to [AppSettings.Default] instead of failing the
 * flow. The app's theme and units hang off this, so an unreadable row must leave a usable
 * app — but a failure is reported, because "never set" and "cannot be read" look identical
 * from the outside.
 */
internal class AppSettingsRepositoryImpl(
    private val local: AppSettingsLocalDataSource,
    private val telemetry: DataTelemetry,
) : AppSettingsRepository {

    override fun observe(): Flow<AppSettings> =
        local.observe()
            .map { it ?: AppSettings.Default }
            .catch { e ->
                telemetry.crashed(ENTITY, OP_OBSERVE, e)
                emit(AppSettings.Default)
            }

    override suspend fun save(settings: AppSettings): Either<DomainError, AppSettings> =
        telemetry.span(ENTITY, OP_SAVE) {
            try {
                local.save(settings)
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
