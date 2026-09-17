package com.hopcape.odo.infrastructure.firebase.analytics

import com.hopcape.odo.core.domain.device.AnalyticsInstallId

/**
 * Firebase's app instance id — what GA4 reports as `user_pseudo_id`.
 *
 * It changes when the app is reinstalled, which is what makes it the right key for "is this
 * the same install", and the wrong one for "is this the same person".
 */
class FirebaseAnalyticsInstallId : AnalyticsInstallId {

    override suspend fun value(): String? = readAppInstanceId()
}

/**
 * The platform's own lookup. Null when Firebase never initialised or the call failed — the
 * caller stores the device either way, since a row without this id is still worth having.
 */
internal expect suspend fun readAppInstanceId(): String?
