package com.hopcape.odo.web.admin.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.web.admin.domain.ActivityEntry
import com.hopcape.odo.web.admin.domain.DashboardRepository
import com.hopcape.odo.web.admin.domain.DashboardSnapshot
import com.hopcape.odo.web.admin.domain.SignupDay
import com.hopcape.odo.web.core.domain.WebError
import com.hopcape.odo.web.core.infrastructure.supabase.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `admin_dashboard()`, which answers with one object rather than a set.
 *
 * [Postgrest.rpcOne] and not [Postgrest.rpc] for that reason: a function declared
 * `returns jsonb` answers with the object itself, and list decoding fails on the
 * shape.
 */
internal class SupabaseDashboardRepository(
    private val postgrest: Postgrest,
) : DashboardRepository {

    override suspend fun snapshot(): Either<WebError, DashboardSnapshot> =
        postgrest.rpcOne("admin_dashboard", "{}", DashboardRow.serializer()).flatMap { row ->
            // Null means the function answered SQL NULL, which it only does if the
            // permission check let a caller through and then nothing matched — not a
            // state this function can reach, but not one to render as zeroes either.
            row ?: return@flatMap WebError.Unexpected("empty dashboard").left()
            DashboardSnapshot(
                users = row.users,
                users7d = row.users7d,
                usersPrev7d = row.usersPrev7d,
                cars = row.cars,
                serviceLogs = row.serviceLogs,
                documents = row.documents,
                subsActive = row.subsActive,
                subsPastDue = row.subsPastDue,
                ticketsOpen = row.ticketsOpen,
                ticketsUrgent = row.ticketsUrgent,
                postsPublished = row.postsPublished,
                postsDraft = row.postsDraft,
                vehiclePending = row.vehiclePending,
                cityPending = row.cityPending,
                signups = row.signups.map { SignupDay(it.d, it.n) },
                activity = row.activity.map {
                    ActivityEntry(
                        action = it.action,
                        subjectType = it.subjectType,
                        actorEmail = it.actor,
                        at = it.at.replace('T', ' ').substringBefore('.'),
                    )
                },
            ).right()
        }
}

private inline fun <A, B> Either<WebError, A>.flatMap(block: (A) -> Either<WebError, B>): Either<WebError, B> =
    fold({ it.left() }, block)

@Serializable
private data class DashboardRow(
    val users: Int = 0,
    @SerialName("users_7d") val users7d: Int = 0,
    @SerialName("users_prev_7d") val usersPrev7d: Int = 0,
    val cars: Int = 0,
    @SerialName("service_logs") val serviceLogs: Int = 0,
    val documents: Int = 0,
    @SerialName("subs_active") val subsActive: Int = 0,
    @SerialName("subs_past_due") val subsPastDue: Int = 0,
    @SerialName("tickets_open") val ticketsOpen: Int = 0,
    @SerialName("tickets_urgent") val ticketsUrgent: Int = 0,
    @SerialName("posts_published") val postsPublished: Int = 0,
    @SerialName("posts_draft") val postsDraft: Int = 0,
    @SerialName("vehicle_pending") val vehiclePending: Int = 0,
    @SerialName("city_pending") val cityPending: Int = 0,
    val signups: List<SignupRow> = emptyList(),
    val activity: List<ActivityRow> = emptyList(),
)

@Serializable
private data class SignupRow(val d: String, val n: Int = 0)

@Serializable
private data class ActivityRow(
    val action: String,
    @SerialName("subject_type") val subjectType: String,
    val actor: String? = null,
    val at: String,
)
