package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * Stamp setup finished on the owner's stored profile; answers whether this call stamped it.
 *
 * A profile is never created here. A fresh install has none, and a bare row would be pushed
 * over the account's real one at sign-in. The start gate counts a stored car as setup done.
 */
internal class CompleteOnboardingUseCase(
    private val profiles: OwnerProfileRepository,
    private val currentOwner: CurrentOwnerProvider,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(): Either<DomainError, Boolean> {
        val stored = profiles.observe().first()?.takeIf { it.id == currentOwner.currentOwnerId() }
        if (stored == null || stored.hasCompletedOnboarding) return false.right()
        return profiles.save(stored.completeOnboarding(clock.now())).map { true }
    }
}
