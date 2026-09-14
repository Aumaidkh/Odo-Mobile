package com.hopcape.odo.feature.support.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.domain.support.FeatureIdeaRepository

/**
 * Adds this owner's name to an idea, or takes it off.
 *
 * A toggle rather than two calls: the pill is one control and pressing it twice has to end
 * where it started, which is easier to be sure of when one thing decides the new value.
 */
internal class CastIdeaVoteUseCase(
    private val ideas: FeatureIdeaRepository,
) {
    suspend operator fun invoke(ideaId: String, voted: Boolean): Either<DomainError, Unit> =
        ideas.vote(ideaId, voted)
}
