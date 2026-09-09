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
 * Two server columns are deliberately absent, and their absence is what protects them:
 * PostgREST's merge-duplicates upsert only touches the columns in the payload, so leaving
 * one out means "don't change it" rather than "set it to null".
 *
 *  - `home_city_id` is a uuid into the `cities` lookup, while the client holds a city
 *    *name*. Mapping the two needs a local copy of that lookup, which does not exist yet.
 *    Until it does, the owner's city stays on the device and does not survive a reinstall.
 *  - `preferred_language` has no UI yet; the server default stands.
 *
 * `phone` used to be a third. It belongs to the auth session rather than to anything the
 * client edits, which is true and was the wrong conclusion: the server's only writer for it
 * is a trigger that fires on INSERT into `auth.users`, so any account whose row was created
 * before GoTrue had set the number kept `phone` NULL forever, and no client could repair it.
 * The client now sends what the session proved.
 */
@Serializable
data class ProfileDto(
    @SerialName("id") val id: String,
    @SerialName("full_name") val fullName: String? = null,
    /**
     * The number this account signs in with, in E.164.
     *
     * Written by the client at every successful sign-in, not only at signup. Null when the
     * device has no session yet and has nothing to claim; a null never clears a number the
     * server already has, because the local row keeps whichever of the two is non-null (see
     * `Profile.sq`).
     */
    @SerialName("phone") val phone: String? = null,
    /**
     * none | read_only | blocked — whether support has restricted this account (#369).
     *
     * Pull-only in practice: the client writes whatever it last read, and the column's
     * update policy is admin-only, so a device claiming to be unrestricted changes nothing.
     * The app reads it to explain itself; the enforcement is a restrictive policy on every
     * owned table and, for blocked, firebase-session refusing to mint a session.
     *
     * Defaulted so an older server row without the column still decodes.
     */
    @SerialName("restriction") val restriction: String = "none",
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    /**
     * Whether this owner's prices may feed the city benchmark.
     *
     * Non-null with a default, so it is always present in the payload — a field omitted
     * from a PostgREST batch is what makes a cleared value never sync. The default is `true`
     * only so a row pulled from a server that predates the column reads as opted in, which
     * is the same answer a new profile gets.
     */
    @SerialName("shares_prices") val sharesPrices: Boolean = true,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** Accepts pushes, remembers nothing, has nothing to pull. */
internal class FakeProfileRemoteDataSource : ProfileRemoteDataSource {
    override suspend fun fetchSince(ownerId: String, since: Instant?): List<ProfileDto> = emptyList()
    override suspend fun push(profiles: List<ProfileDto>): List<ProfileDto> = profiles
}
