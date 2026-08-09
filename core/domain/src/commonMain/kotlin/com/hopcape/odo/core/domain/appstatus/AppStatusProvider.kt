package com.hopcape.odo.core.domain.appstatus

import kotlinx.coroutines.flow.StateFlow

/**
 * The one thing the rest of the app asks about app availability. Nobody outside
 * `:core:data` touches [AppStatus] or [AppStatusSource] directly.
 */
interface AppStatusProvider {

    /** The current verdict. Starts at [AppAvailability.Allowed] — fail open. */
    val availability: StateFlow<AppAvailability>

    /** Fetches a fresh [AppStatus] and re-evaluates [availability]. Safe to call anytime. */
    suspend fun refresh()
}
