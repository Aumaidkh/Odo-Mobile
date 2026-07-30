package com.hopcape.odo.core.data.fairness

import arrow.core.getOrElse
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.fairness.model.FairnessEstimate
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository
import com.hopcape.odo.core.domain.servicelog.model.ServiceCategory
import com.hopcape.odo.core.domain.shared.Amount

/**
 * City benchmarks, read through [FairnessRemoteDataSource].
 *
 * Deliberately **not** cached locally yet. Unlike the owner's own data, benchmarks are
 * reference data owned by the server, and a cache is only worth building against the real
 * `get_fairness_estimate` RPC — its freshness window and sample sizes decide what a sane
 * cache lifetime even is. Until then a read that cannot reach the source returns nothing,
 * and the UI shows no verdict, which is the same honest outcome as having no benchmark.
 *
 * A failure never propagates: the caller asked whether a price looks fair, and the answer
 * "we don't know" is a legitimate one. It is logged rather than thrown, because a fairness
 * source that is quietly always empty would otherwise look identical to a city with no data.
 */
internal class FairnessRepositoryImpl(
    private val remote: FairnessRemoteDataSource,
    private val telemetry: DataTelemetry,
) : FairnessRepository {

    override suspend fun estimates(
        categories: Set<ServiceCategory>,
        city: String,
    ): Map<ServiceCategory, FairnessEstimate> {
        if (categories.isEmpty()) return emptyMap()

        return telemetry.span(DataTelemetry.FAIRNESS, OP_ESTIMATES) {
            try {
                remote.estimates(categories.map { it.name }, city)
                    .mapNotNull { dto -> dto.toDomain() }
                    .associateBy { it.category }
            } catch (e: Exception) {
                telemetry.crashed(DataTelemetry.FAIRNESS, OP_ESTIMATES, e)
                emptyMap()
            }
        }
    }

    /** An unrecognised category means a newer server taxonomy — drop the row, keep the rest. */
    private fun FairnessEstimateDto.toDomain(): FairnessEstimate? {
        val parsed = ServiceCategory.entries.firstOrNull { it.name == category } ?: return null
        return FairnessEstimate(
            category = parsed,
            city = city,
            cityAverage = Amount.of(cityAveragePaise).getOrElse { return null },
            sampleSize = sampleSize,
        )
    }

    private companion object {
        const val OP_ESTIMATES = "estimates"
    }
}
