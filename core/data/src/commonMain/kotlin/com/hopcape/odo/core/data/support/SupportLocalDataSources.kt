package com.hopcape.odo.core.data.support

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.support.FeatureIdea
import com.hopcape.odo.core.domain.support.SupportTicket
import kotlinx.coroutines.flow.Flow

/**
 * Local storage for tickets. Owns how a row is written and read; the repository owns what an
 * operation means.
 *
 * Throws on storage failure, which the repository turns into a `DomainError`.
 */
interface SupportTicketLocalDataSource {

    /** Write the ticket, `PENDING`, under [ownerId]. */
    suspend fun insert(ownerId: OwnerId, ticket: SupportTicket)

    /** Everything this owner has sent, newest first. */
    fun observe(ownerId: OwnerId): Flow<List<SupportTicket>>
}

/**
 * Local storage for the curated ideas and this owner's votes on them.
 *
 * The two are one data source because the screen reads them as one row — an idea and whether
 * this owner voted for it — and splitting the read would mean joining in Kotlin what SQL
 * already joins.
 */
interface FeatureIdeaLocalDataSource {

    /** The catalogue with [ownerId]'s votes on it, most wanted first. */
    fun observe(ownerId: OwnerId): Flow<List<FeatureIdea>>

    /**
     * Record a vote, or take one back.
     *
     * Taking one back is a tombstone rather than a delete: the server has to hear that it was
     * withdrawn, and a row that is gone has nothing to push.
     */
    suspend fun setVote(ownerId: OwnerId, ideaId: String, voted: Boolean)

    /**
     * Make [ideas] the whole of the stored catalogue, in one transaction.
     *
     * Replaced rather than merged: the server is the only author, so a row it no longer sends
     * is a row that should stop being shown. Votes are a separate table and survive it.
     */
    suspend fun replaceCatalogue(ideas: List<FeatureIdeaDto>)
}

/** The server's half of the catalogue. Pull-only — nothing here writes an idea. */
interface FeatureIdeaRemoteDataSource {

    /** Everything curated, as the server holds it. */
    suspend fun ideas(): List<FeatureIdeaDto>
}

/**
 * A ticket on the wire.
 *
 * `clientId` is the app's own id and the column the sync keys on — the server's `id` is a
 * bigint it assigns, which a device could not have named. Everything else is carried as the
 * strings the columns hold, because nothing between here and PostgREST needs to read them.
 */
data class SupportTicketDto(
    val clientId: String,
    val ownerId: String,
    val kind: String,
    val body: String,
    val details: String,
    val attachments: String,
    val replyTo: String?,
    val diagnosticsReference: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

/** A vote on the wire. Keyed on the pair, like the table it comes from. */
data class IdeaVoteDto(
    val ideaId: String,
    val ownerId: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)

/** The server's half of a ticket and its votes. */
interface SupportTicketRemoteDataSource {

    suspend fun push(rows: List<SupportTicketDto>): List<SupportTicketDto>

    suspend fun fetch(ownerId: String, since: String?): List<SupportTicketDto>
}

interface IdeaVoteRemoteDataSource {

    suspend fun push(rows: List<IdeaVoteDto>): List<IdeaVoteDto>

    suspend fun fetch(ownerId: String, since: String?): List<IdeaVoteDto>
}

/** One curated idea, as the server sends it. */
data class FeatureIdeaDto(
    val id: String,
    val title: String,
    val status: String,
    val votes: Long,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
)
