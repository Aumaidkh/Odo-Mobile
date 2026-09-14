package com.hopcape.odo.core.data.benchmark

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.benchmark.BandWorking
import com.hopcape.odo.core.domain.benchmark.BenchmarkBasis
import com.hopcape.odo.core.domain.benchmark.BenchmarkScope
import com.hopcape.odo.core.domain.benchmark.PriceBand
import com.hopcape.odo.core.domain.benchmark.PriceBandQuery
import com.hopcape.odo.core.domain.benchmark.PriceBandRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * [PriceBandRepository] over the server's benchmark RPC.
 *
 * **A band the app cannot read in full is no band.** If the scope or the basis is missing or
 * unrecognised, this answers null rather than guessing: both are shown to the owner as the
 * reason to trust the figure, and a band presented without them is exactly the unsourced
 * number this feature exists to argue against.
 */
internal class PriceBandRepositoryImpl(
    private val remote: PriceBandRemoteDataSource,
    private val telemetry: DataTelemetry,
) : PriceBandRepository {

    override suspend fun bandFor(query: PriceBandQuery): Either<DomainError, PriceBand?> =
        telemetry.span(SOURCE, OP_BAND, id = query.categorySlug) {
            runCatchingCancellableSuspend {
                remote.band(
                    categorySlug = query.categorySlug,
                    city = query.city,
                    segment = query.segment?.name?.lowercase(),
                    fuel = query.fuel?.name?.lowercase(),
                    workshopTier = query.workshopTier?.name?.lowercase(),
                )
            }.fold(
                onSuccess = { dto -> dto?.toBand(query).right() },
                onFailure = { throwable ->
                    telemetry.crashed(SOURCE, OP_BAND, throwable, query.categorySlug)
                    DomainError.LookupUnavailable.left()
                },
            )
        }

    /**
     * Null when the row cannot be read as a band the owner could be shown.
     *
     * The category is reported, never the city or the car: which jobs the tables cannot
     * answer for is a coverage gap someone can close, and it is the number worth watching as
     * reference data is entered.
     */
    private suspend fun PriceBandDto.toBand(query: PriceBandQuery): PriceBand? {
        val scope = BenchmarkScope.of(scope)
        val basis = BenchmarkBasis.of(basis)
        if (scope == null || basis == null) {
            telemetry.missing(SOURCE, OP_BAND, query.categorySlug)
            return null
        }
        return PriceBand(
            low = Amount.of(p25Paise).getOrNull() ?: return null,
            typical = Amount.of(avgPaise).getOrNull() ?: return null,
            high = Amount.of(p75Paise).getOrNull() ?: return null,
            sampleSize = sampleSize,
            scope = scope,
            basis = basis,
            working = working(),
        )
    }

    /** Only a modelled band has a sum behind it, and only when every part of it came back. */
    private fun PriceBandDto.working(): BandWorking? {
        val parts = partsPaise ?: return null
        val hours = labourHours ?: return null
        val rate = Amount.of(labourPaisePerHour ?: return null).getOrNull() ?: return null
        return BandWorking(partsPaise = parts, labourHours = hours, labourRatePerHour = rate)
    }

    private companion object {
        const val SOURCE = "priceband"
        const val OP_BAND = "band"
    }
}
