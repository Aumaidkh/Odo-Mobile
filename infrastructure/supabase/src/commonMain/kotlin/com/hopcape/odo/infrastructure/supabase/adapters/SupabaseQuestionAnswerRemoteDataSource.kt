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

    override suspend fun push(answers: List<QuestionAnswerDto>): List<QuestionAnswerDto> =
        postgrest.upsert(table = TABLE, serializer = QuestionAnswerDto.serializer(), rows = answers)

    private companion object {
        const val TABLE = "profile_answers"
        const val COLUMN_OWNER_ID = "owner_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
