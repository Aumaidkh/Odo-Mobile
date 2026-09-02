package com.hopcape.odo.core.domain.city

/**
 * Tells Odo about a city [CityCatalog] doesn't have, so it can be reviewed and added for
 * everyone. A port, not a repository: there is nothing local to read back, so a feature only
 * ever calls [report] and moves on.
 *
 * Best-effort by contract. A profile is saved from whatever the owner typed whether or not this
 * succeeds — [report] never returns a failure a caller has to act on, and an offline or
 * unconfigured build is expected to swallow the report silently rather than retry it.
 */
fun interface UnlistedCityReporter {

    /** [name] as the owner typed it. */
    suspend fun report(name: String)
}
