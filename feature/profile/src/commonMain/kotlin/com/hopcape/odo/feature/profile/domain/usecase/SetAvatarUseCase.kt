package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlinx.coroutines.flow.first

/**
 * Keep the photo the owner picked and point their profile at it.
 *
 * The picker hands back a reference into another app's storage that stops resolving after
 * a restart, so the bytes are copied into app storage first and the stored key is what the
 * profile holds. The previous photo is deleted afterwards, once the new key is safely
 * saved: deleting first would leave a profile pointing at nothing if the save failed.
 */
internal class SetAvatarUseCase(
    private val profiles: OwnerProfileRepository,
    private val files: PlatformFileStore,
) {
    suspend operator fun invoke(pickedRef: String): Either<DomainError, OwnerProfile> {
        val stored = profiles.observe().first() ?: return DomainError.ProfileNotFound.left()

        return files.save(pickedRef, DIRECTORY, stored.id.value)
            .flatMap { key -> profiles.save(stored.withAvatar(key)) }
            .onRight { saved ->
                val previous = stored.avatarPath
                if (previous != null && previous != saved.avatarPath) files.delete(previous)
            }
    }

    private companion object {
        /** Where profile photos live under app storage. */
        const val DIRECTORY = "avatars"
    }
}
