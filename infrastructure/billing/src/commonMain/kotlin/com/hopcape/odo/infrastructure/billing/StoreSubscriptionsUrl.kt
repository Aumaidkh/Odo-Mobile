package com.hopcape.odo.infrastructure.billing

/**
 * The store's own subscriptions page, for when the store gave no per-subscriber link.
 *
 * RevenueCat carries a management URL only once the store has one to hand out, and it is
 * null for a subscription that did not come through the store at all. The profile card still
 * has to send an owner somewhere: seeing and cancelling a subscription happens in the store,
 * so a card with no link leaves a paying owner with nowhere to go (#317).
 *
 * Both stores publish a page that opens on the signed-in account and lists what it pays for,
 * which is the right destination even without knowing which subscription is being asked
 * about. Platform-specific because the page is, and this module is the only layer that knows
 * which store the build sells through.
 */
internal expect val storeSubscriptionsUrl: String
