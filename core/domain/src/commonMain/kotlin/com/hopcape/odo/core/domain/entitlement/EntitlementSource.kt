package com.hopcape.odo.core.domain.entitlement

import kotlinx.coroutines.flow.Flow

/**
 * Port over wherever the owner's plan actually comes from (RevenueCat in production).
 *
 * A stream rather than the suspending read `ProEntitlement` was. A purchase now completes
 * inside the app, so the answer changes mid-session and every screen that gated on it has to
 * follow.
 *
 * The client only mirrors entitlement. What someone is entitled to is decided by the store
 * and validated by RevenueCat, so an implementation of this reports a state it was told,
 * never one it computed.
 */
interface EntitlementSource {

    /**
     * The owner's entitlements, starting with what is known now.
     *
     * Never empty and never fails: an implementation that cannot reach anything emits
     * [Entitlements.Unknown] rather than leaving callers with no answer.
     */
    fun observe(): Flow<Entitlements>

    /**
     * Ask the store again, for the cases where waiting for the stream is not enough — the
     * app coming back to the foreground, or the owner pulling to refresh.
     *
     * Does not report a result. A refresh that fails leaves the last known entitlements in
     * place, which is what the caller would have done with the failure anyway. Restoring a
     * purchase is a different job, and it does report one.
     */
    suspend fun refresh()
}
