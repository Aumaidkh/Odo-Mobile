package com.hopcape.odo.core.data.owner

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import kotlinx.coroutines.flow.Flow

/**
 * Local persistence for the owner's profile. Hides the SQLDelight database from
 * [OwnerProfileRepositoryImpl] and [ProfileCityProvider] — both read the same `profiles`
 * table, so they share this one port rather than each reaching the database on their own.
 *
 * Write methods throw on storage failure; callers turn that into a `DomainError` or a
 * reported `null`. [observe] is raw — a read failure propagates to the collector, and the
 * repository decides how to report it.
 */
interface ProfileLocalDataSource {

    /**
     * Write [profile] over the single stored row, creating it on the first call.
     * Preserves `created_at` on an edit and leaves the row `PENDING`.
     */
    suspend fun save(profile: OwnerProfile)

    /** The stored profile, as it changes; `null` before the owner has one. */
    fun observe(): Flow<OwnerProfile?>

    /**
     * Store the number the session just proved, on whichever profile row this device holds,
     * creating one keyed to [ownerId] if there is none yet.
     *
     * Separate from [save] because sign-in knows the phone and nothing else: a whole-profile
     * write would need a profile, and there may not be one, or it may still be keyed to the
     * placeholder owner. Leaves the row `PENDING` so the next push carries the number.
     */
    suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber)

    /** Tombstone the profile row. */
    suspend fun softDeleteAll()

    /** The owner's city, read directly rather than through [observe] — `null` if unset. */
    suspend fun currentCity(): String?
}
