package com.hopcape.odo.core.data.owner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the owner's questionnaire answers (#394).
 *
 * Straight after `profiles` in the sync order: a row references one, and nothing references
 * it.
 */
interface QuestionAnswerRemoteDataSource {

    /** The owner's answers changed since [since] (null = never synced). */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<QuestionAnswerDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(answers: List<QuestionAnswerDto>): List<QuestionAnswerDto>
}

/**
 * The wire shape of an answer, snake_case to match the Postgres columns.
 *
 * Every column is listed, nullable ones included. A field left out of a PostgREST batch never
 * syncs, and rows that disagree about which columns they carry are rejected as PGRST102.
 */
@Serializable
data class QuestionAnswerDto(
    @SerialName("id") val id: String,
    @SerialName("owner_id") val ownerId: String,
    @SerialName("question_key") val questionKey: String,
    @SerialName("answer_value") val value: String,
    @SerialName("answered_at") val answeredAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    /** A tombstone: the owner deselected this option. Pulled, never filtered out. */
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakeQuestionAnswerRemoteDataSource : QuestionAnswerRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<QuestionAnswerDto> = emptyList()
    override suspend fun push(answers: List<QuestionAnswerDto>): List<QuestionAnswerDto> = answers
}
