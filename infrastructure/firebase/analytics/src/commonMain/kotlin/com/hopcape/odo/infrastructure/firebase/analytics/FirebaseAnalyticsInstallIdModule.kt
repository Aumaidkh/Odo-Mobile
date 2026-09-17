package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.odo.core.domain.device.AnalyticsInstallId
import org.koin.dsl.module

/**
 * Publishes Firebase's app instance id as [AnalyticsInstallId].
 *
 * Bound here rather than in the Android bootstrap, unlike this module's analytics sink: the
 * lookup needs no Context and both targets want the same binding.
 */
val firebaseAnalyticsInstallIdModule = module {
    single<AnalyticsInstallId> { FirebaseAnalyticsInstallId() }
}
