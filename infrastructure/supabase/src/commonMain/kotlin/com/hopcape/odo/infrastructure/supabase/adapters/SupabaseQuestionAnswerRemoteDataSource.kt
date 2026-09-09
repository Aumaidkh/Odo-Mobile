package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.owner.QuestionAnswerDto
import com.hopcape.odo.core.data.owner.QuestionAnswerRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `profile_answers` over PostgREST (#394).
 *
 * The delta pull is `owner_id = ? AND updated_at > cursor`, ordered so the cursor advances
 * monotonically. Tombstones are not filtered out: they are how this device learns an option
 * was deselected elsewhere (SYNC_DESIGN §6).
 */
internal class SupabaseQuestionAnswerRemoteDataSource(
    private val postgrest: PostgrestClient,
) : QuestionAnswerRemoteDataSource {

    override suspend fun fetchSince(ownerId: String, since: Instant?): List<QuestionAnswerDto> =
        postgrest.select(
            table = TABLE,
            serializer = QuestionAnswerDto.serializer(),
            filters = buildMap {
                put(COLUMN_OWNER_ID, "eq.$ownerId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    /**
     * Resolved on the unique triple, not on `id`.
     *
     * The same answer can exist on both sides under different ids — the backfill minted its
     * own, and a device offline at the time minted another. Resolving on `id` makes that an
     * INSERT, which violates the triple's index, and a 409 is permanent: the row parks in
     * `CONFLICT` and never syncs again. Naming the triple makes the push update the row that
     * is already there.
     */
    override suspend fun push(answers: List<QuestionAnswerDto>): List<QuestionAnswerDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = QuestionAnswerDto.serializer(),
            rows = answers,
            onConflict = CONFLICT_TARGET,
        )

    private companion object {
        const val TABLE = "profile_answers"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val CONFLICT_TARGET = "owner_id,question_key,answer_value"
    }
}
