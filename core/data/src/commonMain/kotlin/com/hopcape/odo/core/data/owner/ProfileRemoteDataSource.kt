package com.hopcape.odo.core.data.owner

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * The server side of the owner's profile.
 *
 * First in the sync order, because everything else is foreign-keyed to it (SYNC_DESIGN §8).
 * There is exactly one row per account — the id *is* `auth.users.id` — so [fetchSince] can
 * only ever answer with zero or one, and the cursor exists for symmetry with the other
 * ports rather than because a page could be large.
 *
 * The row is created server-side by a trigger on signup, so a push is always an update of
 * something that already exists.
 */
interface ProfileRemoteDataSource {

    /** The profile if it changed since [since] (null = never synced). */
    suspend fun fetchSince(ownerId: String, since: Instant?): List<ProfileDto>

    /** Send local changes; the returned rows are the server's accepted versions. */
    suspend fun push(profiles: List<ProfileDto>): List<ProfileDto>
}

/**
 * The wire shape of a profile — snake_case to match the Postgres columns (DB_SCHEMA §9.2).
 *
 * Three server columns are deliberately absent, and their absence is what protects them:
 * PostgREST's merge-duplicates upsert only touches the columns in the payload, so leaving
 * one out means "don't change it" rather than "set it to null".
 *
 *  - `home_city_id` is a uuid into the `cities` lookup, while the client holds a city
 *    *name*. Mapping the two needs a local copy of that lookup, which does not exist yet.
 *    Until it does, the owner's city stays on the device and does not survive a reinstall.
 *  - `phone` belongs to the auth session, not to anything the client edits.
 *  - `preferred_language` has no UI yet; the server default stands.
 */
@Serializable
data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("onboarding_goal") val onboardingGoal: String? = null,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakeProfileRemoteDataSource : ProfileRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<ProfileDto> = emptyList()
    override suspend fun push(profiles: List<ProfileDto>): List<ProfileDto> = profiles
}
