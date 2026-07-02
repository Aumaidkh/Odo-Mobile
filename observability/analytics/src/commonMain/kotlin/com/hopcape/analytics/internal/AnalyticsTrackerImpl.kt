@file:OptIn(ExperimentalAtomicApi::class)

package com.hopcape.analytics.internal

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.analytics.internal.dedup.Deduplicator
import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.dispatch.BatchDispatcher
import com.hopcape.analytics.internal.model.AnalyticsEvent
import com.hopcape.analytics.internal.model.GlobalContext
import com.hopcape.analytics.internal.store.EventStore
import com.hopcape.analytics.internal.validation.EventRegistry
import com.hopcape.analytics.internal.validation.SchemaValidationResult
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

// ─────────────────────────────────────────────────────────────
// AnalyticsTrackerImpl — orchestrates the ingestion pipeline:
//   consent gate → schema validation → dedup → enqueue → dispatch.
// Each concern lives in its own collaborator (SRP); this class only
// sequences them and depends solely on abstractions (DIP). It is
// assembled once by AnalyticsFactory.
// ─────────────────────────────────────────────────────────────
internal class AnalyticsTrackerImpl(
    private val registry: EventRegistry,
    private val destinations: List<AnalyticsDestination>,
    private val store: EventStore,
    private val dedup: Deduplicator,
    private val dispatcher: BatchDispatcher,
    // true in debug: throw on invalid + drop unregistered events (fail fast).
    // false in prod: drop invalid but let unknown events through (never lose data).
    private val strictSchemaValidation: Boolean,
    private val onDiagnostic: (String) -> Unit,
    private val contextProvider: () -> GlobalContext,
) : AnalyticsTracker {

    private val consent = AtomicReference(ConsentStatus.UNKNOWN)

    override fun identify(traits: UserTraits) {
        if (consent.load() != ConsentStatus.GRANTED) return
        destinations.forEach { it.identify(traits) }
    }

    override fun track(eventName: String, properties: Map<String, Any?>) {
        // 1. Consent gate — fail closed. No consent, no event.
        if (consent.load() != ConsentStatus.GRANTED) return

        // 2. Schema validation — protects downstream dashboards from typos/missing fields.
        if (!passesValidation(eventName, properties)) return

        val event = AnalyticsEvent(
            name = eventName,
            properties = properties,
            context = contextProvider(),
            sequenceNumber = dispatcher.nextSequenceNumber(),
        )

        // 3. Dedup — collapse accidental double-fires within the recency window.
        if (dedup.isDuplicate(signatureOf(eventName, properties))) return

        // 4. Enqueue, then let the dispatcher deliver — never call destinations
        //    directly here; the queue guarantees offline durability and retry.
        store.enqueue(event)
        dispatcher.dispatchIfBatchFull()
    }

    override fun setConsent(status: ConsentStatus) {
        consent.store(status)
        // On DENIED a production impl would also purge queued-but-undelivered
        // events and tell vendor SDKs to disable collection.
    }

    override fun flush() = dispatcher.flushNow()

    /** Returns true if the event may proceed; reports and short-circuits otherwise. */
    private fun passesValidation(eventName: String, properties: Map<String, Any?>): Boolean =
        when (val result = registry.validate(eventName, properties)) {
            SchemaValidationResult.Valid -> true

            SchemaValidationResult.Unregistered -> {
                if (strictSchemaValidation) {
                    onDiagnostic("event '$eventName' is not registered — dropped (strict mode)")
                    false
                } else {
                    true // production: allow unknown events so data is never lost
                }
            }

            is SchemaValidationResult.Invalid -> {
                val reason = result.reasons.joinToString("; ")
                onDiagnostic("schema validation failed for '$eventName': $reason")
                if (strictSchemaValidation) throw IllegalArgumentException(reason)
                false // production: drop silently, but surfaced via onDiagnostic
            }
        }

    /** Content signature used for dedup: event name + properties in a stable order. */
    private fun signatureOf(eventName: String, properties: Map<String, Any?>): String =
        buildString {
            append(eventName)
            properties.entries.sortedBy { it.key }.forEach { (key, value) ->
                append('|').append(key).append('=').append(value)
            }
        }
}
