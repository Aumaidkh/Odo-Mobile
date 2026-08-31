package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import com.hopcape.odo.web.admin.domain.CitiesRepository
import com.hopcape.odo.web.admin.domain.City
import com.hopcape.odo.web.admin.domain.CitySubmission
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import com.hopcape.odo.web.core.infrastructure.supabase.jsonEscaped
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `cities` and `city_submissions` over PostgREST.
 *
 * Every call here is refused by RLS unless the session holds
 * `catalog.cities.write`. Nothing in this file checks that, on purpose: a client
 * that decided for itself would be a client that could be patched to decide
 * differently.
 *
 * Payloads are built by hand rather than serialized from a data class. They are
 * three fields wide, and the alternative is a `@Serializable` DTO per shape whose
 * only job is to be turned into the same string.
 */
internal class SupabaseCitiesRepository(
    private val postgrest: Postgrest,
) : CitiesRepository {

    override suspend fun cities(): Either<WebError, List<City>> =
        postgrest.select(
            table = TABLE_CITIES,
            serializer = CityRow.serializer(),
            // Retired ones included: this is the one caller that has to see them,
            // because restoring one is impossible if it cannot be listed.
            query = "select=id,name,state,tier,is_active&order=name.asc",
        ).map { rows -> rows.map { it.toCity() } }

    override suspend fun submissions(): Either<WebError, List<CitySubmission>> =
        postgrest.select(
            table = TABLE_SUBMISSIONS,
            serializer = SubmissionRow.serializer(),
            // Oldest first: a queue is worked from the front, and somebody who
            // reported a missing city three weeks ago has waited longest.
            query = "select=id,name,state,tier,status,created_at&order=created_at.asc",
        ).map { rows -> rows.map { it.toSubmission() } }

    override suspend fun add(name: String, state: String, tier: Int): Either<WebError, Unit> =
        postgrest.insert(
            table = TABLE_CITIES,
            body = """{"name":"${name.jsonEscaped()}","state":"${state.jsonEscaped()}","tier":$tier}""",
        )

    override suspend fun edit(id: String, name: String, state: String, tier: Int): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE_CITIES,
            query = "id=eq.$id",
            body = """{"name":"${name.jsonEscaped()}","state":"${state.jsonEscaped()}","tier":$tier}""",
        )

    override suspend fun setActive(id: String, active: Boolean): Either<WebError, Unit> =
        postgrest.patch(
            table = TABLE_CITIES,
            query = "id=eq.$id",
            body = """{"is_active":$active}""",
        )

    override suspend fun decideSubmission(
        id: String,
        accepted: Boolean,
        state: String?,
        tier: Int?,
    ): Either<WebError, Unit> {
        val status = if (accepted) "accepted" else "rejected"
        // State and tier go in the same PATCH as the status rather than a separate
        // one. The promote trigger fires on this update and reads the row as it
        // then stands; writing the status first would fire it against a row whose
        // state is still null, which its own guard turns into a silent no-op.
        val fields = buildList {
            add("\"status\":\"$status\"")
            state?.let { add("\"state\":\"${it.jsonEscaped()}\"") }
            tier?.let { add("\"tier\":$it") }
        }
        return postgrest.patch(
            table = TABLE_SUBMISSIONS,
            query = "id=eq.$id",
            body = fields.joinToString(prefix = "{", postfix = "}"),
        )
    }

    override suspend fun deleteSubmission(id: String): Either<WebError, Unit> =
        postgrest.delete(table = TABLE_SUBMISSIONS, query = "id=eq.$id")

    private companion object {
        const val TABLE_CITIES = "cities"
        const val TABLE_SUBMISSIONS = "city_submissions"
    }
}

@Serializable
private data class CityRow(
    val id: String,
    val name: String,
    val state: String,
    val tier: Int,
    @SerialName("is_active") val isActive: Boolean,
) {
    fun toCity() = City(id = id, name = name, state = state, tier = tier, isActive = isActive)
}

@Serializable
private data class SubmissionRow(
    val id: String,
    val name: String,
    val state: String? = null,
    val tier: Int? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
) {
    fun toSubmission() = CitySubmission(
        id = id,
        name = name,
        state = state,
        tier = tier,
        status = status,
        // The date half is all anybody reads off a queue row, and a full
        // timestamptz is noise beside a city name.
        createdAt = createdAt.substringBefore('T'),
    )
}
