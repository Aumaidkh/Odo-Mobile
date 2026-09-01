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
 * Binding this over [com.hopcape.odo.core.domain.subscription.SubscriptionIdentity] only means
 * a key was present at startup ([com.hopcape.odo.infrastructure.billing.BillingEnvironment]) —
 * it does not mean `Purchases.configure` actually succeeded. That call can fail for reasons
 * outside this codebase (a bad or mismatched key), and `RevenueCatBootstrap` swallows the
 * failure rather than bringing startup down over a subscription nobody has bought yet. Every
 * call here is signed-in/signed-out lifecycle, unconditional and unrelated to whether a paywall
 * was ever opened, so a guard is checked before touching `Purchases.sharedInstance` rather than
 * assumed from the binding.
 */
internal class RevenueCatIdentity(
    private val scope: CoroutineScope,
    private val telemetry: BillingTelemetry,
) : SubscriptionIdentity {

    override fun identify(ownerId: OwnerId) {
        if (!Purchases.isConfigured) {
            telemetry.identifyFailed(NOT_CONFIGURED, NOT_CONFIGURED_MESSAGE)
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
            telemetry.forgetFailed(NOT_CONFIGURED, NOT_CONFIGURED_MESSAGE)
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
        const val NOT_CONFIGURED = "not_configured"
        const val NOT_CONFIGURED_MESSAGE = "Purchases.configure did not succeed"
    }
}
