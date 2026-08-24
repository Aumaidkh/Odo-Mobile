package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.owner.LocalUserDataWipe
import com.hopcape.odo.core.platform.file.PlatformFileStore

/**
 * The sign-out wipe, as one transaction.
 *
 * Two things about it are easy to get wrong and both would lose data:
 *
 * **The deletes are hard, not soft.** Every other delete in the app sets `deleted_at` and
 * leaves the row `PENDING`, which is how a deletion reaches the server. Doing that here
 * would mean signing out destroys the owner's cloud backup — the next sign-in would push a
 * tombstone for every row. These rows are being forgotten locally, not deleted.
 *
 * **`sync_state` is cleared too.** The cursors are a high-water mark against server time. A
 * cursor left behind would make the next sign-in's delta pull start from it and skip
 * everything written in between — data that exists on the server and never arrives, with
 * nothing to indicate why. Clearing means the next pull is a full one, which is exactly
 * right for a device that now holds nothing.
 *
 * Stored files go with the rows that named them. A blob whose row is gone is unreachable
 * bytes, and these are bill photos and insurance papers.
 *
 * **`analytics_events` is cleared too.** A queued-but-undelivered event carries the
 * signed-out owner's user id and traits in its stored context — draining it after sign-out
 * would deliver a previous owner's activity under whichever session happens to be running
 * when the destination finally accepts it.
 */
internal class SqlDelightLocalUserDataWipe(
    private val database: OdoDatabase,
    private val files: PlatformFileStore,
    private val telemetry: DataTelemetry,
) : LocalUserDataWipe {

    override suspend fun wipe() {
        telemetry.span(DataTelemetry.SYNC, OP_WIPE) {
            // Collected before the rows go, because afterwards there is nothing to ask.
            val storedFiles = filePaths()

            try {
                database.transaction {
                    // Children first: a foreign key would refuse the parent otherwise.
                    database.serviceLogQueries.deleteAllCategories()
                    database.overchargeReportQueries.deleteAllRows()
                    database.serviceLogQueries.deleteAllRows()
                    database.documentQueries.deleteAllRows()
                    database.fuelFillQueries.deleteAllRows()
                    database.healthScoreQueries.deleteAllRows()
                    database.reminderQueries.deleteAllRows()
                    // Trips were missing here, so signing out left them behind for the next
                    // account to inherit. They carry an `owner_id` like everything else.
                    database.tripQueries.deleteAllRows()
                    database.carQueries.deleteAllRows()
                    database.profileQueries.deleteAllRows()
                    // Without this the next sign-in's pull starts from a stale mark.
                    database.syncStateQueries.deleteAll()
                    database.analyticsEventQueries.deleteAll()
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SYNC, OP_WIPE, e)
                // Reported and swallowed: sign-out has already happened as far as the owner
                // is concerned, and throwing here would strand them on a screen with a
                // session that is already gone.
                //
                // The cursors are then cleared on their own, because of the three things
                // this transaction does they are the one whose survival is silent. Rows left
                // behind are at least visible; a cursor left behind makes the *next*
                // account's first pull a delta since a mark it never set, which fetches
                // nothing and looks exactly like an empty account (issue #312). One
                // statement over a tiny table, so it can fail where the big transaction did
                // not.
                clearCursors()
                return@span
            }

            // Outside the transaction: file IO is not transactional, and a failed delete is
            // wasted space rather than a broken sign-out.
            storedFiles.forEach { files.delete(it) }
        }
    }

    /**
     * Every stored file the owner's rows point at, before those rows are removed.
     *
     * All rows, not just the unsynced ones, and not filtered on `deleted_at`: a soft-deleted
     * row's bytes are still sitting on disk.
     */
    private fun filePaths(): List<String> = runCatching {
        database.documentQueries.selectAllStoragePaths().executeAsList() +
            database.serviceLogQueries.selectAllBillPhotoPaths().executeAsList().mapNotNull { it }
    }.getOrElse { emptyList() }

    /**
     * Second attempt at the one statement that must not be skipped.
     *
     * Its own try/catch: if even this fails there is nothing further to try, and sign-out
     * still has to complete for the owner.
     */
    private suspend fun clearCursors() = try {
        database.syncStateQueries.deleteAll()
    } catch (e: Exception) {
        telemetry.crashed(DataTelemetry.SYNC, OP_CLEAR_CURSORS, e)
    }

    private companion object {
        const val OP_WIPE = "signOutWipe"
        const val OP_CLEAR_CURSORS = "signOutClearCursors"
    }
}
