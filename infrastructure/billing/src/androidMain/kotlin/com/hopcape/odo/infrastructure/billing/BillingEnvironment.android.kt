package com.hopcape.odo.infrastructure.billing

/** Google Play's key. Purchases here go through Play Billing. */
internal actual val storeApiKey: String get() = BuildBillingConfig.ANDROID_API_KEY
