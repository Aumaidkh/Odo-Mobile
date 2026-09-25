package com.hopcape.odo.feature.questionnaire.firstrun.domain.usecase

import arrow.core.EitherNel
import arrow.core.flatMap
import arrow.core.nonEmptyListOf
import arrow.core.raise.either
import com.hopcape.odo.core.domain.owner.CurrentOwnerProvider
import com.hopcape.odo.core.domain.owner.model.OwnerName
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock

/**
 * Finish first-run setup: validate the profile step's answers, stamp the completion time,
 * and persist the profile. Returns every validation failure at once via [EitherNel].
 *
 * **This is where "onboarding needs a name" lives** — it is onboarding's rule, not a
 * universal truth about owners. The server creates a profile row at signup without one, so
 * [OwnerProfile] itself accepts its absence; only this flow refuses to finish without it.
 * Keeping the rule here is what lets the shared kernel stay free of any one feature's policy.
 *
 * Goals are no longer validated here. They are stored separately in `profile_answers` before
 * this runs, and whether a question may be left blank is the asking screen's rule (#394).
 *
 * Completion is stamped before the save rather than after, so a profile is never persisted
 * in a state that claims setup is unfinished when it is.
 */
internal class CompleteOnboardingUseCase(
    private val profiles: OwnerProfileRepository,
    private val currentOwner: CurrentOwnerProvider,
    private val clock: Clock = Clock.System,
) {
    suspend operator fun invoke(
        command: CompleteOnboardingCommand,
    ): EitherNel<DomainError, OwnerProfile> = either {
        val validName = OwnerName.of(command.name).mapLeft { nonEmptyListOf(it) }.bind()
        OwnerProfile.new(id = currentOwner.currentOwnerId(), name = validName)
            .completeOnboarding(clock.now())
    }.flatMap { profile -> profiles.save(profile).mapLeft { nonEmptyListOf(it) } }
}
