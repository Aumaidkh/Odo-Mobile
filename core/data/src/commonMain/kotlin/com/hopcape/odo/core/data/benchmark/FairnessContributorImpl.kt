package com.hopcape.odo.core.data.benchmark

import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.benchmark.FairnessContributor
import com.hopcape.odo.core.domain.benchmark.PriceObservation

/**
 * [FairnessContributor] over the shared pool.
 *
 * **Never fails at its caller.** A contribution is a gift made after the owner already has
 * their answer: a refusal because they have not consented, or because they are signed out, is
 * the normal case and not something to put on a screen. Failures are logged, because a pool
 * that silently stops filling is a feature that quietly stops improving.
 */
internal class FairnessContributorImpl(
    private val remote: FairnessContributionRemoteDataSource,
    private val telemetry: DataTelemetry,
) : FairnessContributor {

    override suspend fun contribute(observations: List<PriceObservation>) {
        if (observations.isEmpty()) return
        telemetry.span(SOURCE, OP_CONTRIBUTE) {
            runCatchingCancellableSuspend {
                remote.contribute(observations.map { it.toDto() })
            }.onFailure { telemetry.crashed(SOURCE, OP_CONTRIBUTE, it) }
        }
    }

    private fun PriceObservation.toDto() = FairnessContributionDto(
        categorySlug = categorySlug,
        city = city,
        amountPaise = amount.paise,
        segment = segment?.name?.lowercase(),
        fuel = fuel.name.lowercase(),
        workshopTier = workshopTier.name.lowercase(),
        carMake = carMake,
    )

    private companion object {
        const val SOURCE = "fairnesspool"
        const val OP_CONTRIBUTE = "contribute"
    }
}
