package com.hopcape.odo.core.domain.fairness.repository

import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory

/**
 * Port for reading city fairness benchmarks. The implementation (in `:core:data` /
 * `:core:network`) calls the server's `get_fairness_estimate` `SECURITY DEFINER` RPC,
 * which returns only the aggregate average + sample size — the raw de-identified pool is
 * never client-readable.
 */
interface FairnessRepository {
    /**
     * The benchmarks for [categories] in [city], keyed by category. A category with no
     * benchmark yet is simply **absent** from the map rather than mapping to null, so
     * "have data" and "no data" are one lookup for the caller.
     *
     * Asked for in bulk because that is how the app reads it: a bill or a service entry
     * benchmarks several categories at once, and one call per line turns a list screen
     * into an N+1. The server RPC takes a single category — batching or caching that is
     * the adapter's business, not the caller's.
     */
    suspend fun estimates(categories: Set<ServiceCategory>, city: String): Map<ServiceCategory, FairnessEstimate>
}
