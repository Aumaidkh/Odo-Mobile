package com.hopcape.odo.infrastructure.database.owner

import com.hopcape.odo.core.data.owner.ProfileDto
import com.hopcape.odo.core.data.owner.ProfileRemoteDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.db.Profiles
import com.hopcape.odo.infrastructure.database.sync.FetchResult
import com.hopcape.odo.infrastructure.database.sync.LocalRowState
import com.hopcape.odo.infrastructure.database.sync.SyncTable
import com.hopcape.odo.infrastructure.database.sync.orNullIfPlaceholder
import com.hopcape.odo.infrastructure.database.sync.toInstantOrNull
import com.hopcape.odo.infrastructure.database.sync.toSyncStatus
import kotlin.time.Instant

/**
 * `profiles` as the sync algorithm sees it. One row, first in the order, because everything
 * else is foreign-keyed to it.
 *
 * **The owner's city is deliberately not touched by a pull.** The server holds it as a uuid
 * into a `cities` lookup this device has no copy of, so [ProfileDto] does not carry it and
 * `updateFromRemote` does not list the column. That keeps the local value intact instead of
 * blanking it on every sync — the trade being that the city does not survive a reinstall
 * until the lookup is synced too.
 *
 * **The phone travels both ways, but a null never wins.** Both statements coalesce it, so a
 * pull from a server row written before the number was ever pushed cannot blank what this
 * device knows. The push is the point of the column existing: the server's own writer only
 * runs when the account is created, so a number it missed then can only arrive from here.
 */
internal class ProfileSyncTable(
    private val database: OdoDatabase,
    private val remote: ProfileRemoteDataSource,
    private val ownerId: () -> String?,
) : SyncTable<ProfileDto> {

    private val queries get() = database.profileQueries

    override fun idOf(dto: ProfileDto): String = dto.id

    override fun updatedAtOf(dto: ProfileDto): Instant? = dto.updatedAt.toInstantOrNull()

    override suspend fun pending(): List<ProfileDto> =
        queries.selectPending().executeAsList().map(Profiles::toDto)

    override suspend fun push(rows: List<ProfileDto>): List<ProfileDto> = remote.push(rows)

    override fun markSynced(id: String, remoteVersion: String) =
        queries.markSynced(remoteVersion = remoteVersion, id = id)

    override fun markConflict(id: String) = queries.markConflict(id)

    /**
     * No owner id means this run cannot ask for anything, and that is a failure rather than
     * an empty answer. The gate only lets a run start when there is a session, so a
     * placeholder id reaching here is an inconsistency worth retrying — not the server
     * saying it has nothing (issue #312).
     */
    override suspend fun fetch(since: Instant?): FetchResult<ProfileDto> {
        val owner = ownerId().orNullIfPlaceholder() ?: return FetchResult.ScopeMissing(OWNER)
        return FetchResult.Rows(remote.fetchSince(owner, since))
    }

    override fun localState(id: String): LocalRowState? =
        queries.selectSyncState(id).executeAsOneOrNull()?.let { row ->
            LocalRowState(row.sync_status.toSyncStatus(), row.updated_at.toInstantOrNull())
        }

    override fun applyRemote(dto: ProfileDto) {
        queries.insertFromRemote(
            id = dto.id,
            full_name = dto.fullName,
            onboarding_goal = dto.onboardingGoal?.uppercase(),
            onboarding_completed_at = dto.onboardingCompletedAt,
            // Only on insert, and only ever null: a row this device has never seen has no
            // local city to keep, and the server's is an id we cannot resolve yet.
            city = null,
            email = dto.email,
            avatar_path = dto.avatarPath,
            shares_prices = dto.sharesPrices.toDbLong(),
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            phone = dto.phone,
        )
        queries.updateFromRemote(
            full_name = dto.fullName,
            onboarding_goal = dto.onboardingGoal?.uppercase(),
            onboarding_completed_at = dto.onboardingCompletedAt,
            email = dto.email,
            avatar_path = dto.avatarPath,
            // Unlike the city, this one *is* pulled: the switch belongs to the account, so
            // a device that syncs must learn that another one turned it off.
            shares_prices = dto.sharesPrices.toDbLong(),
            // Coalesced by the statement: a server row that predates the phone ever being
            // pushed answers null, and taking that would throw away what sign-in just wrote.
            phone = dto.phone,
            created_at = dto.createdAt,
            updated_at = dto.updatedAt,
            deleted_at = dto.deletedAt,
            remote_version = dto.updatedAt,
            id = dto.id,
        )
    }

    private companion object {
        /** Names the missing scope in a log. Never the value. */
        const val OWNER = "owner id"
    }
}

private fun Profiles.toDto() = ProfileDto(
    id = id,
    fullName = full_name,
    phone = phone,
    onboardingGoal = onboarding_goal?.lowercase(),
    onboardingCompletedAt = onboarding_completed_at,
    email = email,
    avatarPath = avatar_path,
    sharesPrices = shares_prices != 0L,
    createdAt = created_at,
    updatedAt = updated_at,
    deletedAt = deleted_at,
)
