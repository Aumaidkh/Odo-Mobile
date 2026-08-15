package com.hopcape.odo.core.domain.refuel

import kotlinx.coroutines.flow.Flow

/**
 * Port emitting payments the phone was told about.
 *
 * The adapter is Android's notification listener, which exists on no other platform — which
 * is exactly why this is a port. iOS binds an implementation that never emits, and every
 * layer above stays identical: detection simply never fires there, and the pump-scan and
 * prefill channels carry the feature.
 *
 * A cold stream. Collecting it is what the detection worker does; nothing is buffered for a
 * collector that is not there, because a payment nobody was listening for is not one to
 * surface an hour later.
 */
fun interface PaymentNoticeSource {

    fun notices(): Flow<PaymentNotice>
}
