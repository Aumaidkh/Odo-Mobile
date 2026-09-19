package com.hopcape.odo.core.domain.subscription

/**
 * Whether this device can complete a purchase at all, asked before one is offered.
 *
 * The catalog says what is for sale, which the store will answer even where it would refuse
 * to sell. Starting a flow into that refusal crashes inside the store's own screen.
 */
fun interface StoreAvailability {

    /** Reads the store's current answer. Cheap to call, but not free — it opens a connection. */
    suspend fun check(): StoreReadiness
}

/** Why a purchase cannot be started, or [READY] when it can. */
enum class StoreReadiness {
    READY,

    /** The store did not install this copy, so it will not sell to it. */
    NOT_FROM_STORE,

    /** The store is present but cannot take a payment — signed out, outdated, unsupported. */
    PAYMENTS_UNAVAILABLE,
}
