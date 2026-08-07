package com.hopcape.odo.infrastructure.database.analytics

import com.hopcape.analytics.api.AnalyticsEventStore
import com.hopcape.analytics.api.StoredAnalyticsEvent
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.infrastructure.database.db.Analytics_events
import com.hopcape.odo.infrastructure.database.db.OdoDatabase
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The durable [AnalyticsEventStore] behind `:observability:analytics`'s queue — see
 * `AnalyticsEvent.sq` for why this table carries no sync columns.
 *
 * Every method reports through [telemetry] rather than throwing: the port's own caller
 * ([com.hopcape.analytics.internal.store.PublicEventStoreAdapter]) already treats a throw
 * here as "delivery/storage failed" and degrades gracefully, but a silently-dropped event
 * is exactly the kind of failure this module's telemetry sweep exists to catch.
 *
 * [rowCap]/[maxAge] are constructor params rather than fixed constants purely so a test can
 * exercise eviction without inserting a thousand rows — production always uses the defaults.
 */
internal class SqlDelightAnalyticsEventStore(
    private val database: OdoDatabase,
    private val telemetry: DataTelemetry,
    private val clock: Clock = Clock.System,
    private val rowCap: Int = DEFAULT_ROW_CAP,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
) : AnalyticsEventStore {

    private val queries get() = database.analyticsEventQueries

    override suspend fun enqueue(event: StoredAnalyticsEvent) {
        telemetry.span(DataTelemetry.ANALYTICS_EVENT, OP_ENQUEUE) {
            try {
                val evicted = database.transactionWithResult {
                    queries.insertEvent(
                        eventId = event.eventId,
                        name = event.name,
                        propertiesJson = event.properties.toPropertiesJson(),
                        contextJson = event.context.toJson(),
                        sequenceNumber = event.sequenceNumber,
                        timestampMs = event.timestampMs,
                        attemptCount = event.attemptCount.toLong(),
                    )
                    val before = queries.selectCount().executeAsOne()
                    // Both caps enforced on every insert: the table is small enough (at
                    // most rowCap rows) that this is cheap, and "always correct" beats
                    // "correct except between periodic sweeps".
                    queries.evictBeyondRowCap(rowCap.toLong())
                    queries.evictOlderThan(clock.now().toEpochMilliseconds() - maxAge.inWholeMilliseconds)
                    before - queries.selectCount().executeAsOne()
                }
                // A destination stuck failing for a week, or one that's simply gone quiet,
                // would otherwise evict silently — the same "an invisible failure" this
                // module's telemetry sweep exists to catch, applied to storage instead of
                // delivery.
                if (evicted > 0) {
                    telemetry.missing(DataTelemetry.ANALYTICS_EVENT, OP_EVICTED, key = evicted.toString())
                }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_ENQUEUE, e, event.eventId)
                // Swallowed: the caller (PublicEventStoreAdapter) already runs this off
                // the UI thread and reports its own failure; throwing again here would
                // just be a second report of the same fact.
            }
        }
    }

    override suspend fun peekBatch(maxSize: Int): List<StoredAnalyticsEvent> =
        telemetry.span(DataTelemetry.ANALYTICS_EVENT, OP_PEEK) {
            val rows = try {
                queries.selectBatch(maxSize.toLong()).executeAsList()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_PEEK, e)
                return@span emptyList()
            }

            val unreadableIds = mutableListOf<String>()
            val events = rows.mapNotNull { row -> row.toStoredEventOrNull() ?: run { unreadableIds += row.event_id; null } }

            // A row that cannot be decoded will never decode differently on the next
            // pass — deleting it is the same "permanent, not transient" call the
            // sanitizer makes for an invalid event name, applied to storage instead.
            if (unreadableIds.isNotEmpty()) {
                unreadableIds.forEach { id ->
                    telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_UNREADABLE, UnreadableEventRow, id)
                }
                runCatching { queries.deleteByIds(unreadableIds) }
            }

            events
        }

    override suspend fun remove(eventIds: List<String>) {
        if (eventIds.isEmpty()) return
        telemetry.span(DataTelemetry.ANALYTICS_EVENT, OP_REMOVE) {
            try {
                queries.deleteByIds(eventIds)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_REMOVE, e)
            }
        }
    }

    override suspend fun size(): Int =
        telemetry.span(DataTelemetry.ANALYTICS_EVENT, OP_SIZE) {
            try {
                queries.selectCount().executeAsOne().toInt()
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_SIZE, e)
                0
            }
        }

    override suspend fun recordAttempt(eventId: String, attempt: Int) {
        telemetry.span(DataTelemetry.ANALYTICS_EVENT, OP_RECORD_ATTEMPT, eventId) {
            try {
                queries.updateAttempt(attempt = attempt.toLong(), eventId = eventId)
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.ANALYTICS_EVENT, OP_RECORD_ATTEMPT, e, eventId)
            }
        }
    }

    private fun Analytics_events.toStoredEventOrNull(): StoredAnalyticsEvent? {
        val properties = properties_json.toPropertiesOrNull() ?: return null
        val context = context_json.toStoredContextOrNull() ?: return null
        return StoredAnalyticsEvent(
            eventId = event_id,
            name = name,
            properties = properties,
            sequenceNumber = sequence_number,
            timestampMs = timestamp_ms,
            context = context,
            attemptCount = attempt_count.toInt(),
        )
    }

    private companion object {
        const val OP_ENQUEUE = "enqueue"
        const val OP_PEEK = "peekBatch"
        const val OP_REMOVE = "remove"
        const val OP_SIZE = "size"
        const val OP_RECORD_ATTEMPT = "recordAttempt"
        const val OP_UNREADABLE = "unreadableRow"
        const val OP_EVICTED = "evicted"

        const val DEFAULT_ROW_CAP = 1000
        val DEFAULT_MAX_AGE = 7.days
    }
}

/** Marker reported when a row's JSON can no longer be decoded (a format change, corruption). */
private object UnreadableEventRow : Exception("unreadable analytics_events row")
