package com.hopcape.odo.infrastructure.database.owner

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hopcape.odo.core.data.owner.ProfileLocalDataSource
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.infrastructure.database.sync.SyncStatus
import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.owner.model.OwnerProfile
import com.hopcape.odo.core.domain.owner.model.PhoneNumber
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
        val completedAt = profile.onboardingCompletedAt?.toString()
        val email = profile.email?.value
        val phone = profile.phone?.value
        // Insert-then-update rather than an UPSERT: `ON CONFLICT ... DO UPDATE` needs
        // SQLite 3.24 and minSdk 26 ships 3.18. The insert is ignored when the row already
        // exists (which is what preserves created_at), so on an edit the UPDATE is what
        // writes. One transaction, so no reader sees the half-written state.
        database.transaction {
            queries.insertProfile(
                id = profile.id.value,
                fullName = fullName,
                onboardingCompletedAt = completedAt,
                city = profile.city,
                email = email,
                avatarPath = profile.avatarPath,
                sharesPrices = profile.sharesPricesAnonymously.toDbLong(),
                now = now,
                syncStatus = SyncStatus.PENDING.name,
                phone = phone,
            )
            queries.updateProfile(
                fullName = fullName,
                onboardingCompletedAt = completedAt,
                city = profile.city,
                email = email,
                avatarPath = profile.avatarPath,
                sharesPrices = profile.sharesPricesAnonymously.toDbLong(),
                // Null here means "the caller wasn't told the number", not "clear it" —
                // the statement coalesces. See Profile.sq.
                phone = phone,
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

    override suspend fun recordPhone(ownerId: OwnerId, phone: PhoneNumber) {
        // Writes the number onto a profile this device already has, and does nothing at all
        // when there is none. The update matches on "the single live profile" rather than on
        // an id, because at sign-in the row may still be keyed to the placeholder owner —
        // adoption re-keys it during the sync that follows.
        //
        // Creating a row here is what broke: the push is a whole-row upsert, so a row holding
        // only the number went up with `full_name` null and overwrote the account's real name
        // on the server. The server's signup trigger already writes the number, and once a
        // pull has brought the profile down the next `recordSessionPhone()` puts it on that
        // row and pushes it.
        queries.updatePhone(phone = phone.value, updatedAt = clock.now().toString())
    }

    override suspend fun softDeleteAll() {
        val now = clock.now().toString()
        queries.softDeleteProfiles(deletedAt = now, syncStatus = SyncStatus.PENDING.name)
    }

    override suspend fun currentCity(): String? = withContext(dispatcher) {
        queries.selectProfile().executeAsOneOrNull()?.city
    }
}
