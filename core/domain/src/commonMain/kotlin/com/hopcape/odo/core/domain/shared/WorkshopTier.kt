package com.hopcape.odo.core.domain.shared

/**
 * The kind of workshop a car is serviced at.
 *
 * Every price comparison resolves its labour rate through this, so without it an
 * authorised centre always reads "over" and a local garage always reads "under" — each
 * verdict wrong in a predictable direction rather than merely vague.
 *
 * The constants mirror the `workshop_tier` labels the labour-rate table is keyed by.
 * [MULTI_BRAND] sits between the other two, which is also what an owner who services in
 * both places should be quoted against.
 */
enum class WorkshopTier {
    /** The maker's own network — Maruti Arena, Hyundai, Tata. */
    AUTHORISED,

    /** An independent chain, and the fallback when the owner does not know. */
    MULTI_BRAND,

    /** The neighbourhood mechanic. */
    LOCAL,
}
