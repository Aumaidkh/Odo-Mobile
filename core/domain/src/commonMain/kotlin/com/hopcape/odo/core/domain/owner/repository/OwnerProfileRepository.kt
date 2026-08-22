package com.hopcape.odo.core.domain.owner.repository

import arrow.core.Either
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/**
 * Port for persisting and observing the owner's profile. The implementation lives in
 * `:core:data` (local DB as source of truth); the domain stays ignorant of it.
 *
 * [observe] takes no id on purpose: one device holds one signed-in owner, the same reason
 * [com.hopcape.odo.core.domain.car.repository.CarRepository.observePrimaryCar] takes none.
 * Multi-account switching would change that, and it is not on the roadmap.
 */
interface OwnerProfileRepository {

    /**
     * Insert or update the profile — keyed on [OwnerProfile.id], so calling it twice with
     * the same profile is not an error and not a duplicate. Onboarding writes the name and
     * goal in one step, so an upsert is the only write shape needed.
     */
    suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile>

    /** The stored profile, or `null` before onboarding has written one. */
    fun observe(): Flow<OwnerProfile?>

    /**
     * Record the number a session has just proved this account signs in with.
     *
     * Its own operation rather than part of [save], because the caller is auth and auth
     * knows the number and nothing else — there may be no profile yet at all, and a
     * whole-profile write would need one. Creates a row if there is none, keyed to
     * [ownerId], the same way the server's signup trigger does.
     *
     * Called at every sign-in, not only the first. The server's only writer for the number
     * is a trigger on account creation, so anything it missed stays missing forever unless
     * the client puts it back — which is why this exists.
     */
    suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber): Either<DomainError, Unit>

    /**
     * Remove the owner's profile — what "delete my data" leaves behind on the device.
     *
     * A soft delete, like every other user row: the profile stops being readable (so the
     * app opens on first-run setup again) while the tombstone stays for the sync engine to
     * push. Erasing the server account is a different operation, server-side, and is not
     * this (DB_SCHEMA §13).
     *
     * Deleting when there is no profile succeeds — the caller wanted it gone, and it is.
     */
    suspend fun delete(): Either<DomainError, Unit>
}
