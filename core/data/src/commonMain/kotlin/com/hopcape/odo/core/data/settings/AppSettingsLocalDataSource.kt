package com.hopcape.odo.core.data.settings

import com.hopcape.odo.core.domain.settings.model.AppSettings
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for device settings. Hides the SQLDelight database from
 * [AppSettingsRepositoryImpl]: this owns how the single row is read and written, the
 * repository owns what an operation means (error mapping, telemetry, the
 * default-on-missing-or-unreadable policy).
 *
 * `save` throws on storage failure; the repository turns that into a
 * `DomainError.PersistenceFailure`. [observe] is raw — `null` before anything has been
 * saved, and a read failure propagates to the collector for the repository to report and
 * fall back on.
 */
internal interface AppSettingsLocalDataSource {

    /**
     * Write [settings] over the single stored row, creating it on the first call.
     * Insert-then-update in one transaction: the insert is ignored once the row exists,
     * so on every later save the update is what writes.
     */
    suspend fun save(settings: AppSettings)

    /** The stored settings, as they change; `null` before the first save. */
    fun observe(): Flow<AppSettings?>
}
