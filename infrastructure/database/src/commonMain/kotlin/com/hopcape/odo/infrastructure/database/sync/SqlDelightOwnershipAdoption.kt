package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.data.sync.OwnershipAdoption
import kotlin.time.Instant

/**
 * Adoption, as one transaction over every user-owned table — and, straight after it, the
 * removal of anything belonging to a *different* account.
 *
 * One transaction because a half-adopted database is the worst outcome available: cars
 * belonging to the account and their service logs still belonging to the placeholder would
 * push as orphans and be rejected by the foreign key. Either the whole install moves across
 * or none of it does, and the next run tries again.
 *
 * Order matters inside it. `profiles` is re-keyed last: every other table's `owner_id`
 * points at it, and moving the parent first would leave the children pointing at a row that
 * no longer exists.
 *
 * **The eviction runs after adoption, not before, and that ordering is what makes it simple.**
 * Once the placeholder rows have been moved across, the signed-in account is the only owner
 * that may legitimately appear, so "belongs to somebody else" is a single comparison rather
 * than a two-way exclusion — and an owner's offline work is never at risk, because it was
 * adopted a statement earlier.
 *
 * It is what finishes a sign-out that did not. `LocalUserDataWipe` is supposed to clear the
 * previous account on the way out, but it swallows a failed transaction, and the rows it
 * leaves behind cannot be pushed (RLS refuses an `owner_id` that is not `auth.uid()`), show
 * on screen as though they belonged to whoever is signed in now, and sit beside sync cursors
 * describing someone else's pull — which is an established account signing in to an empty app
 * (issue #312). Clearing the cursors alongside the rows is what makes the next pull a full
 * one.
 *
 * Self-limiting: after one pass there is nothing foreign left, the count comes back zero, and
 * the cursors are never cleared again.
 */
internal class SqlDelightOwnershipAdoption(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val placeholderOwnerId: String,
) : OwnershipAdoption {

    override suspend fun adopt(realOwnerId: String, now: Instant) {
        if (realOwnerId == placeholderOwnerId) return

        telemetry.span(DataTelemetry.SYNC, OP_ADOPT) {
            try {
                val stamp = now.toString()
                database.transaction {
                    database.carQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.serviceLogQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.documentQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.fuelFillQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.healthScoreQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.overchargeReportQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.vehicleCatalogSubmissionQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.reminderQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    // A ticket filed before sign-in. Without this it vanishes from the
                    // owner's own list the moment they sign in — `selectAll` filters on
                    // owner_id — and is pushed under an id that is not `auth.uid()`, which
                    // RLS refuses permanently.
                    database.supportTicketQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    database.ideaVoteQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    // Trips were missing here. They are stamped with an owner like every
                    // other table and, since the pull became owner-scoped, an unadopted trip
                    // is one this account can neither push nor recognise as its own.
                    database.tripQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    // Answers given during onboarding, before anyone signed in. Unadopted,
                    // this account can neither push them nor claim them.
                    database.profileAnswerQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)
                    // Consumables bought before signing in, and anything spent of them.
                    // Unadopted, this account can neither push them nor recognise the
                    // purchase as already honoured — which is how one gets honoured twice.
                    database.purchaseCreditsQueries.adoptClaims(realOwnerId, stamp, placeholderOwnerId)
                    database.purchaseCreditsQueries.adoptSpends(realOwnerId, stamp, placeholderOwnerId)

                    // Last, and a re-key rather than a stamp. `UPDATE OR IGNORE` because the
                    // signed-in account may already have a profile row pulled from the
                    // server — in which case the placeholder simply loses, and the delete
                    // below clears it. Without the OR IGNORE that collision is a crash on
                    // the primary key.
                    database.profileQueries.adoptProfileId(realOwnerId, stamp, placeholderOwnerId)
                    database.profileQueries.deleteProfile(placeholderOwnerId)

                    evictOtherOwners(realOwnerId)
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SYNC, OP_ADOPT, e)
                // Swallowed on purpose. Adoption runs before every sync, so a failure here
                // means this run syncs nothing new and the next one tries again — which is
                // better than a crash on a screen the owner was using at the time.
            }
        }
    }

    /**
     * Take out everything belonging to an account other than [realOwnerId], and reset the
     * sync cursors if there was anything.
     *
     * Called from inside the adoption transaction, so a crash halfway leaves the database
     * exactly as it was rather than half-evicted. Children before parents, matching the
     * sign-out wipe — foreign keys are not enforced on this database, but the ordering is
     * what a reader expects and costs nothing.
     */
    private fun evictOtherOwners(realOwnerId: String) {
        if (database.syncStateQueries.countForeignOwned(realOwnerId).executeAsOne() == 0L) return

        database.overchargeReportQueries.deleteForeignOwned(realOwnerId)
        database.vehicleCatalogSubmissionQueries.deleteForeignOwned(realOwnerId)
        database.serviceLogQueries.deleteForeignOwned(realOwnerId)
        database.serviceLogQueries.deleteOrphanCategories()
        database.documentQueries.deleteForeignOwned(realOwnerId)
        database.fuelFillQueries.deleteForeignOwned(realOwnerId)
        database.healthScoreQueries.deleteForeignOwned(realOwnerId)
        database.reminderQueries.deleteForeignOwned(realOwnerId)
        database.tripQueries.deleteForeignOwned(realOwnerId)
        database.carQueries.deleteForeignOwned(realOwnerId)
        database.profileAnswerQueries.deleteForeignOwned(realOwnerId)
        // Spends before claims: a spend read without the claim that paid for it is the only
        // ordering that could ever look like an overdraft.
        database.purchaseCreditsQueries.deleteForeignSpends(realOwnerId)
        database.purchaseCreditsQueries.deleteForeignClaims(realOwnerId)
        database.profileQueries.deleteForeignOwned(realOwnerId)

        // The cursors described the evicted account's pull. Left in place, this account's
        // first pull is a delta since a mark it never set, which for an established account
        // fetches nothing at all.
        database.syncStateQueries.deleteAll()
    }

    private companion object {
        const val OP_ADOPT = "adoptOwnership"
    }
}
