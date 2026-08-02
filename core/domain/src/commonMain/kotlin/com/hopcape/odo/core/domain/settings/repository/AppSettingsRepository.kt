package com.hopcape.odo.core.domain.settings.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for the device's app settings. The implementation lives in `:core:data`.
 *
 * [observe] never emits null and never fails: a device that has stored nothing has
 * [AppSettings.Default], and a read that breaks falls back to the same defaults rather
 * than leaving the app with no theme and no units. A *write* can fail, so [save] returns
 * an `Either` — the owner pressed something and deserves to know it did not take.
 *
 * Takes no id, like [OwnerProfileRepository][com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository]:
 * there is one set of settings per device, and they are not tied to who is signed in.
 */
interface AppSettingsRepository {

    /** The stored settings, starting with what is on the device now. */
    fun observe(): Flow<AppSettings>

    /** Store [settings], replacing whatever was there. */
    suspend fun save(settings: AppSettings): Either<DomainError, AppSettings>
}
