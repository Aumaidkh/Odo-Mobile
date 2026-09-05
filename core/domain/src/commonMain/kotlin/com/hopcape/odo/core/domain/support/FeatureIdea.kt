package com.hopcape.odo.core.domain.support

import arrow.core.Either
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.flow.Flow

/** Where an idea has got to. Set by whoever curates the list, never by the app. */
enum class IdeaStatus {
    UNDER_REVIEW,
    IN_PROGRESS,
    SHIPPING,
    SHIPPED,
}

/**
 * An idea somebody already asked for, and whether this owner has added their name to it.
 *
 * [votes] is the server's count. It is shown rather than computed locally because a count
 * from one device is not a count — but a vote cast here moves it by one straight away, so the
 * number the owner sees answers the tap they just made.
 */
data class FeatureIdea(
    val id: String,
    val title: String,
    val status: IdeaStatus,
    val votes: Int,
    val voted: Boolean,
)

/**
 * The curated list, and this owner's votes on it.
 *
 * Reading is one direction and voting the other: the catalogue is the panel's to write, and a
 * vote is the only thing the app puts back.
 */
interface FeatureIdeaRepository {

    /** The list as it stands, newest count first. Empty until something has been curated. */
    fun observe(): Flow<List<FeatureIdea>>

    /** Ask the server for a fresher list. Failing leaves what is already shown. */
    suspend fun refresh(): Either<DomainError, Unit>

    /** Add or remove this owner's vote. Saved locally first, like every other write. */
    suspend fun vote(ideaId: String, voted: Boolean): Either<DomainError, Unit>
}
