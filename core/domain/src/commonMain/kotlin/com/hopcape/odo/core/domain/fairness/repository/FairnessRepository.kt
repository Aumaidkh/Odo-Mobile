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
    /** The benchmark for [category] in [city], or `null` if none exists yet. */
    suspend fun estimate(category: ServiceCategory, city: String): FairnessEstimate?
}
