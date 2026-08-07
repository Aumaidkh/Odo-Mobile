package com.hopcape.analytics.internal

import com.hopcape.analytics.api.AnalyticsConfig
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.internal.dedup.Deduplicator
import com.hopcape.analytics.internal.destinations.AnalyticsDestination
import com.hopcape.analytics.internal.destinations.ConsoleDestination
import com.hopcape.analytics.internal.destinations.PostHogDestination
import com.hopcape.analytics.internal.destinations.SafeDestination
import com.hopcape.analytics.internal.destinations.SinkDestination
import com.hopcape.analytics.internal.dispatch.BatchDispatcher
import com.hopcape.analytics.internal.model.GlobalContext
import com.hopcape.analytics.internal.store.EventStore
import com.hopcape.analytics.internal.store.InMemoryEventStore
import com.hopcape.analytics.internal.store.PublicEventStoreAdapter
import com.hopcape.analytics.internal.validation.EventRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// ─────────────────────────────────────────────────────────────
// AnalyticsFactory — Factory + composition root for the pipeline,
// mirroring the logger's LoggerFactory. This is the ONE place that
// names concrete built-in types (PostHog/Console destinations, the
// in-memory store, the dispatcher); everything else depends only on
// interfaces (DIP). Vendor destinations this module doesn't own
// (e.g. Firebase) arrive via AnalyticsConfig.destinations as
// AnalyticsSink and are adapted with SinkDestination. It is
// `internal`: the public entry points are the HAnalytics facade and
// the analyticsModule Koin binding, both of which feed an
// AnalyticsConfig through here.
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
            if (config.isDebug) add(ConsoleDestination())
            config.destinations.forEach { add(SinkDestination(it)) }
        }
        // Every destination is exception-isolated so one failing vendor can never
        // crash the app or block delivery to the others.
        val destinations: List<AnalyticsDestination> = rawDestinations.map { destination ->
            SafeDestination(destination) { name, error ->
                config.onDiagnostic("destination '$name' failed: ${error.message}")
            }
        }

        // Shared so a durable store's fire-and-forget writes and the dispatcher's own
        // background work (timer loop, batch/flush dispatch) live on the one scope,
        // torn down together if this pipeline is ever shut down.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store: EventStore = config.eventStore?.let { provider ->
            PublicEventStoreAdapter(provider = provider, scope = scope, onDiagnostic = config.onDiagnostic)
        } ?: InMemoryEventStore()
        val dispatcher = BatchDispatcher(
            store = store,
            destinations = destinations,
            batchSize = config.batchSize,
            flushInterval = config.flushInterval,
            scope = scope,
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
