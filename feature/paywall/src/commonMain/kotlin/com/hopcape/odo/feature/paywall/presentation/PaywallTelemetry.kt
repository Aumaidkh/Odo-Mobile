package com.hopcape.odo.feature.paywall.presentation

import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.common.id.IdGenerator

/**
 * The purchase funnel, behind intent-named methods.
 *
 * This is the one screen in the app where the drop-off between steps is the product question.
 * Every step is counted separately — shown, plan chosen, checkout opened, and then one of
 * completed / cancelled / failed — because "how many subscribed" alone cannot tell the
 * difference between a price nobody likes and a checkout that keeps breaking.
 *
 * [trigger] rides on every event that has one. Which surface sent the owner here is the thing
 * the paywall's own numbers cannot see, and it decides which gates are worth keeping.
 *
 * **No PII, and no prices as numbers.** Plan identifiers, the store's error codes, and
 * booleans. The formatted price is the store's localized string and would be a different
 * value per country, so what is recorded is the plan, not the figure.
 *
 * Every method is fire-and-forget: nothing here returns a decision, so instrumentation cannot
 * change what the screen does.
 */
internal class PaywallTelemetry(
    private val logger: Logger,
    private val analytics: AnalyticsTracker,
    private val ids: IdGenerator,
) {

    /** One trace per visit, so every step of one owner's funnel joins up in the logs. */
    private val flowTrace = TraceContext(flowId = FLOW, traceId = "${FLOW}_${ids.newId()}")

    /** The paywall opened, and what sent the owner to it. */
    fun shown(trigger: String) {
        report(Event.SHOWN, mapOf(Key.TRIGGER to trigger))
    }

    /**
     * The offer could not be shown.
     *
     * The denominator for everything below it: a funnel that counts opens but not the opens
     * that showed no price will read as a pricing problem when it is a store problem.
     */
    fun offerUnavailable(trigger: String, reason: String) {
        report(Event.OFFER_UNAVAILABLE, mapOf(Key.TRIGGER to trigger, Key.REASON to reason))
    }

    /** The owner switched plans. Which one they land on is what the badge is worth. */
    fun planSelected(trigger: String, planId: String) {
        report(Event.PLAN_SELECTED, mapOf(Key.TRIGGER to trigger, Key.PLAN to planId))
    }

    /** The CTA was tapped and the store's sheet is opening. */
    fun checkoutStarted(trigger: String, planId: String, withTrial: Boolean) {
        report(Event.CHECKOUT_STARTED, mapOf(Key.TRIGGER to trigger, Key.PLAN to planId, Key.TRIAL to withTrial))
    }

    /** Money changed hands. */
    fun purchaseCompleted(trigger: String, planId: String, withTrial: Boolean) {
        report(Event.PURCHASE_COMPLETED, mapOf(Key.TRIGGER to trigger, Key.PLAN to planId, Key.TRIAL to withTrial))
    }

    /**
     * The owner closed the store's sheet.
     *
     * Counted, and deliberately not as a failure. It is the most common ending a paywall has,
     * and separating it from a refusal is the difference between "the price is wrong" and
     * "payments are broken".
     */
    fun purchaseCancelled(trigger: String, planId: String) {
        report(Event.PURCHASE_CANCELLED, mapOf(Key.TRIGGER to trigger, Key.PLAN to planId))
    }

    /** The store refused. */
    fun purchaseFailed(trigger: String, planId: String) {
        report(Event.PURCHASE_FAILED, mapOf(Key.TRIGGER to trigger, Key.PLAN to planId))
    }

    /** Restore was tapped. */
    fun restoreTapped(trigger: String) {
        report(Event.RESTORE_TAPPED, mapOf(Key.TRIGGER to trigger))
    }

    /** What restoring turned up — [restored] false means the account had nothing. */
    fun restoreFinished(trigger: String, restored: Boolean) {
        report(Event.RESTORE_FINISHED, mapOf(Key.TRIGGER to trigger, Key.RESTORED to restored))
    }

    /**
     * The paywall was closed without buying.
     *
     * The other half of [shown]. Without it the funnel cannot tell someone who read the offer
     * and left from someone whose app was killed.
     */
    fun dismissed(trigger: String) {
        report(Event.DISMISSED, mapOf(Key.TRIGGER to trigger))
    }

    /* ------------------------------ One-time offers ------------------------------ */

    /**
     * The "buy just this instead" sheet was opened.
     *
     * [count] is how many of the three the store actually returned. Zero is the interesting
     * value and the reason it is here: it means the products have not been created yet, and
     * the owner was shown an empty sheet — which looks identical to a broken one.
     */
    fun oneTimeOffersShown(count: Int) {
        report(Event.ONE_TIME_SHOWN, mapOf(Key.COUNT to count))
    }

    /** The store could not be read at all. */
    fun oneTimeOffersUnavailable(reason: String) {
        report(Event.ONE_TIME_UNAVAILABLE, mapOf(Key.REASON to reason))
    }

    /**
     * A product was tapped. Nothing is bought yet — the purchase path is the next slice —
     * so this is the only signal there is that anyone wants one of these.
     */
    fun oneTimeOfferTapped(productId: String) {
        report(Event.ONE_TIME_TAPPED, mapOf(Key.PRODUCT to productId))
    }

    fun oneTimePurchaseCompleted(productId: String) {
        report(Event.ONE_TIME_PURCHASED, mapOf(Key.PRODUCT to productId))
    }

    /** Backing out. Counted apart from a failure — one is a decision, the other is a fault. */
    fun oneTimePurchaseCancelled(productId: String) {
        report(Event.ONE_TIME_CANCELLED, mapOf(Key.PRODUCT to productId))
    }

    fun oneTimePurchaseFailed(productId: String) {
        report(Event.ONE_TIME_FAILED, mapOf(Key.PRODUCT to productId))
    }

    private fun report(event: String, fields: Map<String, Any?>) {
        analytics.track(event, fields)
        logger.info(TAG, event, tc = flowTrace, fields = fields)
    }

    /** Event names — shipped analytics values. Renaming one breaks every saved query. */
    internal object Event {
        const val SHOWN = "paywall_shown"
        const val OFFER_UNAVAILABLE = "paywall_offer_unavailable"
        const val PLAN_SELECTED = "paywall_plan_selected"
        const val CHECKOUT_STARTED = "paywall_checkout_started"
        const val PURCHASE_COMPLETED = "paywall_purchase_completed"
        const val PURCHASE_CANCELLED = "paywall_purchase_cancelled"
        const val PURCHASE_FAILED = "paywall_purchase_failed"
        const val RESTORE_TAPPED = "paywall_restore_tapped"
        const val RESTORE_FINISHED = "paywall_restore_finished"
        const val DISMISSED = "paywall_dismissed"
        const val ONE_TIME_SHOWN = "paywall_one_time_shown"
        const val ONE_TIME_UNAVAILABLE = "paywall_one_time_unavailable"
        const val ONE_TIME_TAPPED = "paywall_one_time_tapped"
        const val ONE_TIME_PURCHASED = "paywall_one_time_purchased"
        const val ONE_TIME_CANCELLED = "paywall_one_time_cancelled"
        const val ONE_TIME_FAILED = "paywall_one_time_failed"
    }

    /** Field keys — kept here so a dashboard query never breaks on a renamed literal. */
    internal object Key {
        const val TRIGGER = "trigger"
        const val PLAN = "plan"
        const val TRIAL = "trial"
        const val REASON = "reason"
        const val RESTORED = "restored"
        const val COUNT = "count"
        const val PRODUCT = "product"
    }

    private companion object {
        const val TAG = "paywall"
        const val FLOW = "paywall"
    }
}
