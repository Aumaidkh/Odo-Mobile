package com.hopcape.odo.core.data.showcase.observability

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.EventSchema
import com.hopcape.analytics.api.PropertyType
import com.hopcape.logging.api.Logger
import com.hopcape.odo.core.domain.showcase.ShowcaseHookId
import com.hopcape.odo.core.domain.showcase.ShowcaseTelemetry

/**
 * [ShowcaseTelemetry]'s real implementation — the same shape as [com.hopcape.odo.core.data.appstatus.observability.AppStatusTelemetry].
 *
 * Three events keyed by hook, and nothing else: they answer whether each of the six coach
 * marks earns its place (#235) — shown-and-acted-on does, shown-and-dismissed at a high
 * rate is budget spent for nothing — and whether six was already too many. No PII by
 * construction: the hook name is the whole payload.
 */
internal class ShowcaseTelemetryImpl(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
) : ShowcaseTelemetry {

    override fun shown(hook: ShowcaseHookId) = report(EVENT_SHOWN, hook)

    override fun dismissed(hook: ShowcaseHookId) = report(EVENT_DISMISSED, hook)

    override fun actedOn(hook: ShowcaseHookId) = report(EVENT_ACTED_ON, hook)

    private fun report(event: String, hook: ShowcaseHookId) {
        analytics.track(event, mapOf(KEY_HOOK to hook.name))
        logger.info(TAG, event, fields = mapOf(KEY_HOOK to hook.name))
    }

    internal companion object {
        const val TAG = "SHOWCASE"
        const val EVENT_SHOWN = "showcase_shown"
        const val EVENT_DISMISSED = "showcase_dismissed"
        const val EVENT_ACTED_ON = "showcase_acted_on"
        const val KEY_HOOK = "hook"
    }
}

/** The declared schema, concatenated into `odoAnalyticsEvents` — unregistered events are dropped in debug. */
val showcaseAnalyticsEvents: List<EventSchema> = listOf(
    EventSchema(ShowcaseTelemetryImpl.EVENT_SHOWN, mapOf(ShowcaseTelemetryImpl.KEY_HOOK to PropertyType.STRING)),
    EventSchema(ShowcaseTelemetryImpl.EVENT_DISMISSED, mapOf(ShowcaseTelemetryImpl.KEY_HOOK to PropertyType.STRING)),
    EventSchema(ShowcaseTelemetryImpl.EVENT_ACTED_ON, mapOf(ShowcaseTelemetryImpl.KEY_HOOK to PropertyType.STRING)),
)
