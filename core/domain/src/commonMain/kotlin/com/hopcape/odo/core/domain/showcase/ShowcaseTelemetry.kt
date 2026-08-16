package com.hopcape.odo.core.domain.showcase

/**
 * The three counts that make the showcase's premise checkable (#235): shown, dismissed,
 * acted on — per hook. Shown-and-acted-on is a hook earning its place; shown-and-dismissed
 * at a high rate is one spending the budget without returning anything.
 *
 * An interface here rather than a concrete class because the arbiter — the one place all
 * three outcomes pass through — lives in the domain, which stays free of the analytics
 * APIs. The real implementation is `:core:data`'s `ShowcaseTelemetryImpl`. A hook id and
 * an outcome is all that ever leaves; nothing about the car or the owner.
 */
interface ShowcaseTelemetry {

    fun shown(hook: ShowcaseHookId)

    fun dismissed(hook: ShowcaseHookId)

    fun actedOn(hook: ShowcaseHookId)

    /** For tests and any graph without analytics wired. */
    object Noop : ShowcaseTelemetry {
        override fun shown(hook: ShowcaseHookId) = Unit
        override fun dismissed(hook: ShowcaseHookId) = Unit
        override fun actedOn(hook: ShowcaseHookId) = Unit
    }
}
