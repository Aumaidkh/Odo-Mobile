package com.hopcape.odo.infrastructure.database.sync

import com.hopcape.odo.core.domain.owner.model.OwnerId
import kotlin.time.Instant

/**
 * Reading the two stored strings every syncable row carries, tolerantly.
 *
 * Both are deliberately lenient. A row whose `updated_at` cannot be parsed, or whose
 * `sync_status` is a value this build does not know, must not take down a sync run — it is
 * either a hand-edited database or a column written by a newer version of the app. The
 * conservative answer in both cases keeps the row in the outbox rather than dropping it.
 */
internal fun String?.toInstantOrNull(): Instant? =
    this?.let { runCatching { Instant.parse(it) }.getOrNull() }

/**
 * The stored `sync_status`, defaulting to [SyncStatus.PENDING].
 *
 * Unknown means pending on purpose: an unrecognised status is treated as "not yet on the
 * server", so the row gets pushed again. The alternative — assuming SYNCED — would silently
 * drop it from the outbox forever.
 */
internal fun String?.toSyncStatus(): SyncStatus =
    SyncStatus.entries.firstOrNull { it.name == this } ?: SyncStatus.PENDING

/**
 * The scope id to filter a pull on, or null when there is nothing worth asking for.
 *
 * Null and the offline placeholder mean the same thing to the server: no account. The
 * placeholder is deliberately not a uuid, and the `owner_id` columns are, so sending it
 * gets `22P02 invalid input syntax for type uuid` — a 400 that stops the entity and, because
 * the engine halts at the first refusal, everything queued behind it. Filtering it out here
 * turns that into an empty pull, which is the truth.
 */
internal fun String?.orNullIfPlaceholder(): String? =
    takeUnless { it.isNullOrBlank() || it == OwnerId.LOCAL_PLACEHOLDER.value }
