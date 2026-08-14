package com.hopcape.odo.infrastructure.billing.observability

import com.hopcape.crashreporting.api.CrashRecorder
import com.hopcape.logging.api.Logger

/**
 * Observability for the store adapters, behind one intent-named surface.
 *
 * Money makes this the module whose failures must never be silent. A purchase that did not
 * complete, an entitlement that did not arrive, an offering that would not load — each is a
 * support ticket that cannot be answered from a screenshot, so each leaves a line here.
 *
 * What is **not** here is the purchase funnel. `PaywallTelemetry` counts what the owner did
 * at the feature layer; a second event for the same tap would double every number on the
 * dashboard. This records what the SDK did.
 *
 * **Never log anything identifying.** No app user ID, no order ID, no email — outcome names,
 * product ids and error types only.
 */
internal class BillingTelemetry(
    private val logger: Logger,
    private val crash: CrashRecorder,
) {

    /** The SDK is up and can be asked for offerings. */
    fun configured() {
        logger.info(TAG, "$CONFIGURE.done")
    }

    /**
     * No API key in this build, so nothing was configured.
     *
     * Info rather than a warning: a checkout with no credentials is the normal state of a
     * fresh clone, not a fault. It is logged at all because "why does the paywall say
     * nothing is for sale" has exactly one cheap answer, and this is it.
     */
    fun notConfigured() {
        logger.info(TAG, "$CONFIGURE.skipped", fields = mapOf(Key.REASON to NO_API_KEY))
    }

    /**
     * Configuring the SDK threw.
     *
     * A non-fatal with a stack trace rather than a log line: nothing downstream of this can
     * work, so it is the first thing to look for when the store appears dead for everyone.
     */
    fun configureFailed(throwable: Throwable) {
        crash.recordNonFatal(throwable, mapOf(Key.STAGE to CONFIGURE))
        logger.error(TAG, "$CONFIGURE.failed", fields = mapOf(Key.ERROR to throwable::class.simpleName))
    }

    /** The offering loaded and has plans to show. */
    fun offeringsLoaded(offeringId: String, planCount: Int) {
        logger.info(
            TAG,
            "$OFFERINGS.loaded",
            fields = mapOf(Key.OFFERING to offeringId, Key.PLANS to planCount),
        )
    }

    /**
     * The store could not be asked what Pro costs.
     *
     * A log line rather than a non-fatal: the usual cause is a phone with no network, which
     * is not a defect. What it does buy is the store's own error code next to the moment the
     * paywall showed a retry.
     */
    fun offeringsFailed(code: String, message: String) {
        logger.warn(TAG, "$OFFERINGS.failed", fields = mapOf(Key.CODE to code, Key.MESSAGE to message))
    }

    /**
     * RevenueCat has no offering marked current.
     *
     * An error, and ours: the dashboard is misconfigured and every owner is looking at a
     * paywall that cannot sell them anything.
     */
    fun noCurrentOffering() {
        logger.error(TAG, "$OFFERINGS.none", fields = mapOf(Key.REASON to NO_CURRENT_OFFERING))
    }

    /** The offering exists but holds nothing this app knows how to show. Also ours to fix. */
    fun noUsablePackages(offeringId: String) {
        logger.error(
            TAG,
            "$OFFERINGS.none",
            fields = mapOf(Key.REASON to NO_USABLE_PACKAGES, Key.OFFERING to offeringId),
        )
    }

    /**
     * A package was not shown because it is not one of the two plans Odo sells.
     *
     * Legitimate — a dashboard may hold a weekly or lifetime package — but never silent. An
     * offering that renders as a shorter paywall than expected has to be explainable.
     */
    fun packageDropped(packageId: String, type: String) {
        logger.info(TAG, "$OFFERINGS.dropped", fields = mapOf(Key.PACKAGE to packageId, Key.TYPE to type))
    }

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val REASON = "reason"
        const val STAGE = "stage"
        const val ERROR = "error"
        const val OFFERING = "offering"
        const val PLANS = "plans"
        const val PACKAGE = "package"
        const val TYPE = "type"
        const val CODE = "code"
        const val MESSAGE = "message"
    }

    internal companion object {
        const val TAG = "billing"
        const val CONFIGURE = "configure"
        const val OFFERINGS = "offerings"
        const val NO_API_KEY = "no_api_key"
        const val NO_CURRENT_OFFERING = "no_current_offering"
        const val NO_USABLE_PACKAGES = "no_usable_packages"
    }
}
