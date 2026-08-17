package com.hopcape.odo.infrastructure.billing

import com.hopcape.odo.core.domain.entitlement.EntitlementSource
import com.hopcape.odo.core.domain.subscription.SubscriptionCatalog
import com.hopcape.odo.core.domain.subscription.SubscriptionIdentity
import com.hopcape.odo.core.domain.subscription.OneTimePurchaser
import com.hopcape.odo.core.domain.subscription.SubscriptionPurchaser
import com.hopcape.odo.core.domain.subscription.SubscriptionStatusSource
import com.hopcape.odo.infrastructure.billing.catalog.RevenueCatCatalog
import com.hopcape.odo.infrastructure.billing.catalog.UnconfiguredCatalog
import com.hopcape.odo.infrastructure.billing.entitlement.RevenueCatEntitlementSource
import com.hopcape.odo.infrastructure.billing.identity.RevenueCatIdentity
import com.hopcape.odo.infrastructure.billing.purchase.RevenueCatOneTimePurchaser
import com.hopcape.odo.infrastructure.billing.purchase.RevenueCatPurchaser
import com.hopcape.odo.infrastructure.billing.purchase.UnconfiguredOneTimePurchaser
import com.hopcape.odo.infrastructure.billing.purchase.UnconfiguredPurchaser
import com.hopcape.odo.infrastructure.billing.status.RevenueCatSubscriptionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import com.hopcape.odo.infrastructure.billing.observability.BillingTelemetry
import org.koin.dsl.module

/**
 * The store adapters.
 *
 * Listed **after** `coreDataModule` in `initKoin`, for the same reason `supabaseModule` is:
 * from S6 its `EntitlementSource` binding replaces the `FreePlanEntitlementSource` bound
 * there. Koin lets a later definition win, so that position *is* the wiring.
 *
 * Today it registers the bootstrap and nothing else. Configuring the SDK is a slice of its
 * own deliberately: it is the one part that can fail for reasons outside this codebase — a
 * key that is wrong, an SDK that will not link on a target — and finding that out before
 * anything is built on top of it is cheaper than finding it out afterwards.
 *
 * **A build with no key still works.** `RevenueCatBootstrap` does nothing without one, the
 * free-plan entitlement source stays bound, and the app runs. That is the state of every
 * fresh checkout and of CI.
 *
 * Only this `val` is public. The environment, the telemetry facade and the bootstrap stay
 * `internal`: the rest of the app knows the ports, not who implements them.
 */
val billingInfrastructureModule = billingInfrastructureModule(BillingEnvironment.fromBuild())

/**
 * The graph for a given [environment]. Both branches — a build with a key and one without —
 * are reachable from a test this way, rather than depending on whatever is in the developer's
 * `local.properties`. Same shape as `supabaseModule(environment)`.
 */
internal fun billingInfrastructureModule(environment: BillingEnvironment) = module {
    single { BillingTelemetry(logger = get(), crash = get()) }
    billingScope()
    // Resolved at startup rather than on first use, because it configures the SDK in its
    // constructor and nothing else in this module works until it has.
    single(createdAtStart = true) { RevenueCatBootstrap(environment = environment, telemetry = get()) }

    // Both branches are bound, never neither: a missing definition is a crash the moment
    // someone opens the paywall. Without a key `Purchases.sharedInstance` does not exist, so
    // the real adapter would throw there — the unconfigured one says nothing is for sale,
    // which is true and is a screen the paywall already has.
    if (environment.isConfigured) {
        single<SubscriptionCatalog> { RevenueCatCatalog(telemetry = get()) }
        single<SubscriptionPurchaser> { RevenueCatPurchaser(telemetry = get()) }
        single<OneTimePurchaser> { RevenueCatOneTimePurchaser(telemetry = get()) }
        // The SDK allows one delegate, so one object owns customer info and both readers
        // below share it.
        single { CustomerInfoStream(scope = get(named(QUALIFIER_BILLING_SCOPE)), telemetry = get()) }
        // Replaces coreDataModule's FreePlanEntitlementSource — the swap every gate in the
        // app has been waiting for since S2, and it is this one line.
        single<EntitlementSource> { RevenueCatEntitlementSource(stream = get(), telemetry = get()) }
        // The same customer info, read for what the profile card says rather than for what
        // the owner may do.
        single<SubscriptionStatusSource> { RevenueCatSubscriptionStatus(stream = get()) }
        // Replaces coreDataModule's NoopSubscriptionIdentity, which :feature:auth calls
        // unconditionally.
        single<SubscriptionIdentity> {
            RevenueCatIdentity(scope = get(named(QUALIFIER_BILLING_SCOPE)), telemetry = get())
        }
    } else {
        single<SubscriptionCatalog> { UnconfiguredCatalog() }
        // The paywall's ViewModel asks for a purchaser the moment it is built, so this is
        // bound too. No entitlement source and no identity: coreDataModule's free-plan source
        // and no-op identity already stand, and replacing them with more nothing would be
        // noise.
        single<SubscriptionPurchaser> { UnconfiguredPurchaser() }
        single<OneTimePurchaser> { UnconfiguredOneTimePurchaser() }
        // Nobody has a subscription on a build that cannot sell one.
        single<SubscriptionStatusSource> { SubscriptionStatusSource { flowOf(null) } }
    }
}

/**
 * Where the two fire-and-forget adapters run their work.
 *
 * App-lifetime by design: the entitlement stream lives as long as the process, and an
 * identity link started at sign-in must not be cancelled by the screen that triggered it
 * going away. `SupervisorJob` so one failed call cannot take the other down with it.
 */
private const val QUALIFIER_BILLING_SCOPE = "billing"

private fun org.koin.core.module.Module.billingScope() {
    single(named(QUALIFIER_BILLING_SCOPE)) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
