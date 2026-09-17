package com.hopcape.odo.core.domain.device

/**
 * The analytics vendor's own id for this installation.
 *
 * It is stored beside the device because the analytics console reports on this id and not on
 * the account, so without it a support answer cannot be looked up there.
 */
interface AnalyticsInstallId {

    /** Null until the vendor's SDK has one, and on a build with no analytics configured. */
    suspend fun value(): String?
}
