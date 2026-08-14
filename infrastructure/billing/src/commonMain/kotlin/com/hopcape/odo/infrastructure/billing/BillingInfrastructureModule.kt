package com.hopcape.odo.infrastructure.billing

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
    // Resolved at startup rather than on first use, because it configures the SDK in its
    // constructor and nothing else in this module works until it has.
    single(createdAtStart = true) { RevenueCatBootstrap(environment = environment, telemetry = get()) }
}
