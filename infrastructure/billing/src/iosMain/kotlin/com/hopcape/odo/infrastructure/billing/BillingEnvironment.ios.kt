package com.hopcape.odo.infrastructure.billing

/**
 * The App Store's key.
 *
 * Blank for now — iOS is built but not shipped (docs/PAYWALL_PLAN.md, decision 2), so there
 * is no App Store Connect app to hold products yet. The code path is real and compiles; it
 * starts working the day a key is put in `local.properties`.
 */
internal actual val storeApiKey: String get() = BuildBillingConfig.IOS_API_KEY
