package com.hopcape.odo.infrastructure.billing

import com.hopcape.odo.core.common.BuildInfo
import com.hopcape.odo.core.common.BuildVariant
import com.hopcape.odo.core.common.runCatchingCancellable
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.LogLevel
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.PurchasesConfiguration

/**
 * Configures the RevenueCat SDK, once, before anything asks it a question.
 *
 * Everything else in this module — offerings, purchase, restore, the entitlement stream —
 * goes through `Purchases.sharedInstance`, which does not exist until this has run. It
 * configures in its constructor and is registered `createdAtStart`, so it happens while Koin
 * starts rather than when the first caller happens to need it: a paywall that opened before
 * configuration would have to either block or lie about the price.
 *
 * No app user ID is passed. A signed-out owner buys under RevenueCat's own anonymous ID and
 * `Purchases.logIn` merges it into their account when they verify a number (S6) — asking
 * someone to sign in before they can pay is a step that costs more than it protects.
 *
 * **A build with no key is a supported state**, not a broken one — it is what every fresh
 * checkout and every CI run is. Nothing is configured, the reason is logged once, and the
 * free-plan entitlement source stays bound.
 *
 * **Configuring never throws out of here.** Every path downstream already copes with a store
 * that cannot answer, and taking the app's startup down over a subscription nobody has bought
 * yet would be the worse failure.
 */
internal class RevenueCatBootstrap(
    private val environment: BillingEnvironment,
    private val telemetry: BillingTelemetry,
) {
    init {
        configure()
    }

    private fun configure() {
        if (!environment.isConfigured) {
            telemetry.notConfigured()
            return
        }
        if (Purchases.isConfigured) return
        runCatchingCancellable {
            // Why a purchase failed is a question worth answering on a developer's phone and
            // nobody else's: the SDK's own logs name products, offerings and store errors.
            Purchases.logLevel = if (BuildInfo.variant == BuildVariant.RELEASE) LogLevel.WARN else LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration(apiKey = environment.apiKey))
        }
            .onSuccess { telemetry.configured() }
            .onFailure(telemetry::configureFailed)
    }
}
