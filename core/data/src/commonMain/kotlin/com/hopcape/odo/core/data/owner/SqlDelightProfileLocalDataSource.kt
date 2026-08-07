package com.hopcape.odo.core.data.owner

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * SQLDelight-backed [ProfileLocalDataSource] — fully offline. The local DB is the source
 * of truth; every write stamps `updated_at` and leaves the row `sync_status = PENDING`
 * for the sync engine.
 *
 * Timestamps are client-stamped here (offline-first; the server reconciles on sync).
 */
internal class SqlDelightProfileLocalDataSource(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ProfileLocalDataSource {

    private val queries get() = database.profileQueries

    override suspend fun save(profile: OwnerProfile) {
        val now = clock.now().toString()
        val fullName = profile.name?.value
        val goal = profile.goal?.name
        val completedAt = profile.onboardingCompletedAt?.toString()
        val email = profile.email?.value
        // Insert-then-update rather than an UPSERT: `ON CONFLICT ... DO UPDATE` needs
        // SQLite 3.24 and minSdk 26 ships 3.18. The insert is ignored when the row already
        // exists (which is what preserves created_at), so on an edit the UPDATE is what
        // writes. One transaction, so no reader sees the half-written state.
        database.transaction {
            queries.insertProfile(
                id = profile.id.value,
                fullName = fullName,
                onboardingGoal = goal,
                onboardingCompletedAt = completedAt,
                city = profile.city,
                email = email,
                avatarPath = profile.avatarPath,
                now = now,
                syncStatus = SyncStatus.PENDING.name,
            )
            queries.updateProfile(
                fullName = fullName,
                onboardingGoal = goal,
                onboardingCompletedAt = completedAt,
                city = profile.city,
                email = email,
                avatarPath = profile.avatarPath,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING.name,
                id = profile.id.value,
            )
        }
    }

    override fun observe(): Flow<OwnerProfile?> =
        queries.selectProfile()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }

    override suspend fun softDeleteAll() {
        val now = clock.now().toString()
        queries.softDeleteProfiles(deletedAt = now, syncStatus = SyncStatus.PENDING.name)
    }

    override suspend fun currentCity(): String? = withContext(dispatcher) {
        queries.selectProfile().executeAsOneOrNull()?.city
    }
}
