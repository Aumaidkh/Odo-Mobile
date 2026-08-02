package com.hopcape.odo.infrastructure.supabase.adapters

import com.hopcape.odo.core.data.document.DocumentDto
import com.hopcape.odo.core.data.document.DocumentRemoteDataSource
import com.hopcape.odo.infrastructure.supabase.postgrest.PostgrestClient
import kotlin.time.Instant

/**
 * `documents` over PostgREST.
 *
 * [DocumentDto] matches the table column for column (DB_SCHEMA §9.7), so it goes over the wire
 * as-is — no intermediate row type, unlike the service log.
 *
 * Rows only. The file each row names lives in the `documents` bucket and moves through
 * `RemoteFileStorage`; a row whose bytes have not been uploaded yet still syncs, and points at
 * a path that will resolve once they have.
 */
internal class SupabaseDocumentRemoteDataSource(
    private val postgrest: PostgrestClient,
) : DocumentRemoteDataSource {

    override suspend fun fetchSince(carId: String, since: Instant?): List<DocumentDto> =
        postgrest.select(
            table = TABLE,
            serializer = DocumentDto.serializer(),
            filters = buildMap {
                put(COLUMN_CAR_ID, "eq.$carId")
                since?.let { put(COLUMN_UPDATED_AT, "gt.$it") }
            },
            order = "$COLUMN_UPDATED_AT.asc",
        )

    override suspend fun push(documents: List<DocumentDto>): List<DocumentDto> =
        postgrest.upsert(
            table = TABLE,
            serializer = DocumentDto.serializer(),
            rows = documents,
        )

    private companion object {
        const val TABLE = "documents"
        const val COLUMN_CAR_ID = "car_id"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
