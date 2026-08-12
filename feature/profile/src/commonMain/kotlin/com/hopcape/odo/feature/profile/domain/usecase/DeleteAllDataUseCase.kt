package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import com.hopcape.odo.core.domain.car.repository.CarRepository
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.settings.model.AppSettings
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.platform.file.PlatformFileStore
import kotlinx.coroutines.flow.first

/**
 * Erase what this device holds about the owner: their car (with its service history and
 * documents), their profile, their photo, and their settings.
 *
 * **Local only.** There is no account to close yet — nothing has ever been pushed to a
 * server — so this is exactly as complete as it claims to be. When auth lands, the server
 * erasure runs alongside it (DB_SCHEMA §13) and the copy stops saying "on this device".
 *
 * The car goes first, because removing it is what takes the service logs and documents with
 * it in one transaction. Then the profile, which is what sends the next launch back to
 * first-run setup. Settings are reset last and are not allowed to fail the wipe: the owner
 * asked for their data gone, and a leftover theme is not their data.
 */
internal class DeleteAllDataUseCase(
    private val cars: CarRepository,
    private val profiles: OwnerProfileRepository,
    private val settings: AppSettingsRepository,
    private val files: PlatformFileStore,
) {
    suspend operator fun invoke(): Either<DomainError, Unit> {
        val car = cars.observePrimaryCar().first()
        val avatar = profiles.observe().first()?.avatarPath

        val removed = car?.let { cars.softDelete(it.id) } ?: Unit.right()

        return removed
            .flatMap { profiles.delete() }
            .onRight {
                avatar?.let { files.delete(it) }
                settings.save(AppSettings.Default)
            }
    }
}
