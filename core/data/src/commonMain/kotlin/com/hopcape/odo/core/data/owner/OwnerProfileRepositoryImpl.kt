package com.hopcape.odo.core.data.owner

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.repository.OwnerProfileRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/**
 * SQLDelight-backed [OwnerProfileRepository] — fully offline, like every repository here.
 * The local DB is the source of truth and rows are written `sync_status = PENDING` for the
 * sync engine that lands in M5.
 *
 * Timestamps are client-stamped (offline-first; the server reconciles on sync).
 */
internal class OwnerProfileRepositoryImpl(
    private val database: OdoDatabase,
    private val clock: Clock = Clock.System,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : OwnerProfileRepository {

    private val queries get() = database.profileQueries

    override suspend fun save(profile: OwnerProfile): Either<DomainError, OwnerProfile> = try {
        val now = clock.now().toString()
        val fullName = profile.name?.value
        val goal = profile.goal?.name
        val completedAt = profile.onboardingCompletedAt?.toString()
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
                now = now,
                syncStatus = SyncStatus.PENDING.name,
            )
            queries.updateProfile(
                fullName = fullName,
                onboardingGoal = goal,
                onboardingCompletedAt = completedAt,
                updatedAt = now,
                syncStatus = SyncStatus.PENDING.name,
                id = profile.id.value,
            )
        }
        profile.right()
    } catch (e: Exception) {
        DomainError.PersistenceFailure(e.message).left()
    }

    override fun observe(): Flow<OwnerProfile?> =
        queries.selectProfile()
            .asFlow()
            .mapToOneOrNull(dispatcher)
            .map { row -> row?.toDomain() }
}
