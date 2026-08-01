package com.hopcape.odo.core.domain.entitlement

/**
 * Port answering whether the owner is on Odo Pro.
 *
 * Shared kernel: several features gate on the same subscription — the health score's
 * factor breakdown, the AI Doctor, unlimited logs and scans — so the question is asked
 * through one port rather than each feature inventing its own idea of "paid".
 *
 * The client only ever *mirrors* entitlement. What someone is entitled to is decided
 * server-side (payments are verified in an Edge Function, per the TDD), so an
 * implementation of this reads a state it was told, never one it computed.
 *
 * A suspending read rather than a stream, because today nothing can change it mid-session:
 * the subscription is bought outside the app. When Razorpay lands in M6 and a purchase can
 * complete in-session, this grows a flow — the callers that only ask once stay as they are.
 */
fun interface ProEntitlement {

    /** True while the owner has an active Pro subscription. */
    suspend fun isPro(): Boolean
}
