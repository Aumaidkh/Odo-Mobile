package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.core.sync.SyncEntity
import kotlin.time.Instant

/**
 * One table's half of a sync run, expressed as the handful of things the algorithm needs to
 * know about it.
 *
 * The push/pull/last-write-wins algorithm is identical for every entity — only the table,
 * the payload and the mapping differ. This is the seam that keeps that algorithm in one
 * place ([SyncRunner]) instead of copied six times, where five copies would drift.
 *
 * Implementations are thin: they map rows to DTOs and back, and run the queries. No
 * implementation makes a decision about *whether* to write — that is the runner's job, and
 * it is the part that has to be right.
 */
internal interface SyncTable<Dto : Any> {

    /* ---- identity ---- */

    /** The row's primary key, as both sides know it (a client-generated UUID). */
    fun idOf(dto: Dto): String

    /** The row's `updated_at`, used for the last-write-wins comparison. */
    fun updatedAtOf(dto: Dto): Instant?

    /* ---- push ---- */

    /** Local rows the server has not accepted yet, oldest change first. */
    suspend fun pending(): List<Dto>

    /**
     * Last chance to make [rows] acceptable before they are sent.
     *
     * For tables the server constrains on something other than the primary key, a locally
     * generated row can be a duplicate the moment it is written — a car re-added after a
     * reinstall carries a fresh id for a plate the server already holds. Fixing that is
     * per-entity knowledge, so it lives with the entity; the runner only knows that it
     * happens before the push.
     *
     * Most tables have nothing to reconcile and the default does nothing.
     */
    suspend fun reconcileBeforePush(rows: List<Dto>): List<Dto> = rows

    /**
     * Get any files these rows name onto the server, and answer with the rows carrying the
     * paths the objects landed on.
     *
     * Runs **before** [push], because a row pointing at an object that does not exist is a
     * broken document in the Resale Passport (SYNC_DESIGN §8). Most tables have no files;
     * the default does nothing.
     *
     * A row whose upload failed comes back with its path unchanged. It is still pushed —
     * the dates and amounts on it are worth having — and stays `PENDING`, so the next pass
     * retries the upload.
     */
    suspend fun uploadBlobs(rows: List<Dto>): List<Dto> = rows

    /** Send them. The returned rows are what the server stored, carrying its `updated_at`. */
    suspend fun push(rows: List<Dto>): List<Dto>

    /**
     * Mark a pushed row as SYNCED and record the server's `updated_at` as its
     * `remote_version`.
     *
     * Deliberately **not** "write the server's row back over the local one": the local copy
     * is already the newest version, and only the sync bookkeeping changes here
     * (SYNC_DESIGN §6.1).
     */
    fun markSynced(id: String, remoteVersion: String)

    /**
     * Take a row out of the outbox because the server will never accept it.
     *
     * Only for a refusal that is about the payload — a duplicate key, a rejected value, a
     * policy that says no. Those get the same answer however many times they are sent, so
     * leaving them `PENDING` means every later run fails on the same row and no other table
     * ever gets its turn. The row keeps its data and comes back to `PENDING` the moment the
     * owner edits it.
     */
    fun markConflict(id: String)

    /* ---- pull ---- */

    /**
     * Rows the server has changed since [since], or the reason this table could not say what
     * to ask for.
     *
     * **Not a bare list, and that is the point.** Every scoped table used to answer an
     * unknown owner or an unnamed car with an empty list, which the runner read as "the
     * server has nothing new" — so the pull reported success, the cursor stayed put, and
     * WorkManager dropped a job that had fetched none of the owner's data (issue #312). A
     * scope it cannot name is a failure to fetch, not a fetch that found nothing, and
     * [FetchResult.ScopeMissing] is how it says so.
     */
    suspend fun fetch(since: Instant?): FetchResult<Dto>

    /** What this device currently holds for [id], or null if it has never seen it. */
    fun localState(id: String): LocalRowState?

    /**
     * Write a pulled row over the local one and mark it SYNCED.
     *
     * **Each implementation maps field by field.** A blind row replace would erase any
     * column the server does not carry — the odometer's own timestamp, a locally cached
     * city — and those losses are silent. The mapping is per-entity precisely so each one
     * has to say what it keeps.
     */
    fun applyRemote(dto: Dto)
}

/**
 * What a fetch came back with.
 *
 * [ScopeMissing] is deliberately not an empty [Rows]: an empty result is a fact about the
 * server, and a missing scope key is a fact about this device. Conflating them is what let a
 * run that pulled nothing be recorded as a run that had nothing to pull.
 */
internal sealed interface FetchResult<out Dto : Any> {

    /** What the server had. Empty is a perfectly good answer. */
    data class Rows<out Dto : Any>(val rows: List<Dto>) : FetchResult<Dto>

    /**
     * This table could not name what to fetch — no signed-in owner, a placeholder id, a car
     * that has not been pulled yet. [key] names which one, for the log; it never carries the
     * value, which would be an identifier.
     */
    data class ScopeMissing(val key: String) : FetchResult<Nothing>
}

/**
 * Thrown out of a pull whose table could not name its scope, so it lands in the runner's
 * ordinary failure path: the cursor does not move, the entity is recorded as failed, and the
 * run comes back [com.hopcape.odo.core.sync.SyncResult.Partial] — which is a retry.
 *
 * A dedicated type rather than a generic one because `lastError` stores the exception's
 * class name, and "ScopeMissingException" is the whole diagnosis.
 */
internal class ScopeMissingException(entity: SyncEntity, key: String) :
    IllegalStateException("$entity cannot pull: no $key")

/**
 * What the local table knows about a row, reduced to the two facts the conflict rules need.
 */
internal data class LocalRowState(
    val syncStatus: SyncStatus,
    val updatedAt: Instant?,
)
