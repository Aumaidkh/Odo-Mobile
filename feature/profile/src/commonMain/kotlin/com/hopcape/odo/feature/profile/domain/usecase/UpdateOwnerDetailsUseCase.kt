package com.hopcape.odo.feature.profile.domain.usecase

import arrow.core.EitherNel
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.toNonEmptyListOrNull
import com.hopcape.odo.core.domain.owner.model.OwnerEmail
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first

/**
 * Save what the owner changed about themselves — their name, contact email and home city.
 *
 * The city matters more than it looks: it is the key every fairness benchmark is looked up
 * by, and onboarding never asks for one, so this screen is the only way an owner turns
 * verdicts on.
 *
 * Every validation failure comes back at once, so the form can mark both fields rather than
 * making the owner fix one, submit, and find the other.
 */
internal class UpdateOwnerDetailsUseCase(
    private val profiles: OwnerProfileRepository,
) {
    suspend operator fun invoke(command: OwnerDetailsCommand): EitherNel<DomainError, OwnerProfile> {
        val stored = profiles.observe().first()
            ?: return nonEmptyListOf<DomainError>(DomainError.ProfileNotFound).left()

        val name = OwnerName.of(command.name)
        val email = OwnerEmail.of(command.email)
        val failures = listOfNotNull(name.leftOrNull(), email.leftOrNull()).toNonEmptyListOrNull()
        if (failures != null) return failures.left()

        val updated = stored
            .withName(name.getOrNull()!!)
            .withEmail(email.getOrNull())
            .withCity(command.city)

        return profiles.save(updated).mapLeft { nonEmptyListOf(it) }
    }
}

/** Raw, unvalidated answers from the edit-profile form. */
internal data class OwnerDetailsCommand(
    val name: String?,
    val email: String? = null,
    val city: String? = null,
)
