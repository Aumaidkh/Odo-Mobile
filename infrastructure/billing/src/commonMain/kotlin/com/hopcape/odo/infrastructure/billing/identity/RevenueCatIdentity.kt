package com.hopcape.odo.infrastructure.billing.identity

import com.hopcape.odo.core.domain.owner.model.OwnerId
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import com.revenuecat.purchases.kmp.Purchases
import com.revenuecat.purchases.kmp.either.awaitLogInEither
import com.revenuecat.purchases.kmp.either.awaitLogOutEither
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * [SubscriptionIdentity] over RevenueCat's `logIn` / `logOut`.
 *
 * Both are launched rather than awaited, which is the port's contract. Signing in must not
 * wait on a call to a payments vendor, and it must certainly not fail because of one: the
 * owner has verified a phone number, and whether their subscription has finished moving
 * across is not something they should be held at a spinner for.
 *
 * `logIn` is what makes an anonymous purchase survive. Someone who bought Pro without signing
 * in owns it under an identity RevenueCat minted; this hands that purchase to their account,
 * so a new phone signed into the same number has it too.
 *
 * `logOut` returns the device to an anonymous identity. It does not cancel anything — the
 * subscription belongs to the account, and signing back in brings it straight back.
 *
 * **Guarded on [Purchases.isConfigured], not just on being bound.** This class is only bound
 * when [BillingEnvironment.isConfigured] said a key exists at build time
 * ([BillingInfrastructureModule]) — but `RevenueCatBootstrap.configure()` can still fail at
 * runtime (a bad key, the SDK refusing to link) and swallows that failure rather than
 * crashing startup over a subscription nobody has bought yet. Without this guard,
 * `Purchases.sharedInstance` throws `UninitializedPropertyAccessException` the moment a sign-in
 * reaches here, taking the whole app down for something a purchase-less flow should not
 * depend on.
 */
internal class RevenueCatIdentity(
    private val scope: CoroutineScope,
    private val telemetry: BillingTelemetry,
) : SubscriptionIdentity {

    override fun identify(ownerId: OwnerId) {
        if (!Purchases.isConfigured) {
            telemetry.identifyFailed(NOT_CONFIGURED, "Purchases.configure() never succeeded")
            return
        }
        scope.launch {
            Purchases.sharedInstance.awaitLogInEither(newAppUserID = ownerId.value).fold(
                ifLeft = { telemetry.identifyFailed(it.code.toString(), it.message) },
                // `created` says whether RevenueCat had never seen this account before, which
                // is the difference between a first sign-in and a returning owner.
                ifRight = { telemetry.identified(created = it.created) },
            )
        }
    }

    override fun forget() {
        if (!Purchases.isConfigured) {
            telemetry.forgetFailed(NOT_CONFIGURED, "Purchases.configure() never succeeded")
            return
        }
        scope.launch {
            Purchases.sharedInstance.awaitLogOutEither().fold(
                ifLeft = { telemetry.forgetFailed(it.code.toString(), it.message) },
                ifRight = { telemetry.forgotten() },
            )
        }
    }

    private companion object {
        const val NOT_CONFIGURED = "NOT_CONFIGURED"
    }
}
