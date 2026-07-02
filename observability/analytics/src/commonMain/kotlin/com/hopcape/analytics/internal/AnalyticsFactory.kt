package com.hopcape.analytics.internal

import com.hopcape.analytics.api.AnalyticsConfig
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.internal.dedup.Deduplicator
import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.destinations.ConsoleDestination
import com.hopcape.analytics.internal.destinations.FirebaseDestination
import com.hopcape.analytics.internal.destinations.PostHogDestination
import com.hopcape.analytics.internal.destinations.SafeDestination
import com.hopcape.analytics.internal.dispatch.BatchDispatcher
import com.hopcape.analytics.internal.model.GlobalContext
import com.hopcape.analytics.internal.store.InMemoryEventStore
import com.hopcape.analytics.internal.validation.EventRegistry

// ─────────────────────────────────────────────────────────────
// AnalyticsFactory — Factory + composition root for the pipeline,
// mirroring the logger's LoggerFactory. This is the ONE place that
// names concrete types (PostHog/Firebase/Console destinations, the
// in-memory store, the dispatcher); everything else depends only on
// interfaces (DIP). It is `internal`: the public entry points are
// the HAnalytics facade and the analyticsModule Koin binding, both
// of which feed an AnalyticsConfig through here.
// ─────────────────────────────────────────────────────────────
internal object AnalyticsFactory {

    /**
     * Builds a fully-wired [AnalyticsTracker] from [config]. [contextProvider]
     * supplies the live [GlobalContext] (owned by the facade) so session/user
     * updates land on every subsequent event without re-wiring.
     */
    fun create(config: AnalyticsConfig, contextProvider: () -> GlobalContext): AnalyticsTracker {
        val registry = EventRegistry(config.events)

        val rawDestinations = buildList {
            add(PostHogDestination())        // primary vendor (North Star metric)
            add(FirebaseDestination())       // secondary vendor
            if (config.isDebug) add(ConsoleDestination())
        }
        // Every destination is exception-isolated so one failing vendor can never
        // crash the app or block delivery to the others.
        val destinations: List<AnalyticsDestination> = rawDestinations.map { destination ->
            SafeDestination(destination) { name, error ->
                config.onDiagnostic("destination '$name' failed: ${error.message}")
            }
        }

        val store = InMemoryEventStore()
        val dispatcher = BatchDispatcher(
            store = store,
            destinations = destinations,
            batchSize = config.batchSize,
            flushInterval = config.flushInterval,
            onDropped = { event, error ->
                config.onDiagnostic("dropped '${event.name}' after retries: ${error.message}")
            },
        ).also { it.start() }

        return AnalyticsTrackerImpl(
            registry = registry,
            destinations = destinations,
            store = store,
            dedup = Deduplicator(),
            dispatcher = dispatcher,
            strictSchemaValidation = config.isDebug,
            onDiagnostic = config.onDiagnostic,
            contextProvider = contextProvider,
        )
    }
}
