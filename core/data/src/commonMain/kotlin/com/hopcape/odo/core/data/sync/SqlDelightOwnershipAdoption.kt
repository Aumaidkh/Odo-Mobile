package com.hopcape.odo.core.data.sync

import com.hopcape.odo.core.data.db.OdoDatabase
import com.hopcape.odo.core.data.observability.DataTelemetry
import kotlin.time.Instant

/**
 * Adoption, as one transaction over every user-owned table.
 *
 * One transaction because a half-adopted database is the worst outcome available: cars
 * belonging to the account and their service logs still belonging to the placeholder would
 * push as orphans and be rejected by the foreign key. Either the whole install moves across
 * or none of it does, and the next run tries again.
 *
 * Order matters inside it. `profiles` is re-keyed last: every other table's `owner_id`
 * points at it, and moving the parent first would leave the children pointing at a row that
 * no longer exists.
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
                    database.reminderQueries.adoptOwnership(realOwnerId, stamp, placeholderOwnerId)

                    // Last, and a re-key rather than a stamp. `UPDATE OR IGNORE` because the
                    // signed-in account may already have a profile row pulled from the
                    // server — in which case the placeholder simply loses, and the delete
                    // below clears it. Without the OR IGNORE that collision is a crash on
                    // the primary key.
                    database.profileQueries.adoptProfileId(realOwnerId, stamp, placeholderOwnerId)
                    database.profileQueries.deleteProfile(placeholderOwnerId)
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.SYNC, OP_ADOPT, e)
                // Swallowed on purpose. Adoption runs before every sync, so a failure here
                // means this run syncs nothing new and the next one tries again — which is
                // better than a crash on a screen the owner was using at the time.
            }
        }
    }

    private companion object {
        const val OP_ADOPT = "adoptOwnership"
    }
}
