package com.hopcape.odo.core.data.schedule

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.odo.core.common.runCatchingCancellableSuspend
import com.hopcape.odo.core.data.observability.DataTelemetry
import com.hopcape.odo.core.domain.schedule.ServiceInterval
import com.hopcape.odo.core.domain.schedule.ServiceIntervalRepository
import com.hopcape.odo.core.domain.shared.DomainError

/**
 * [ServiceIntervalRepository] over the `service_schedule` reference table.
 *
 * Rows with neither figure are dropped rather than carried as an empty interval: the caller's
 * question is "does the schedule say anything about this job", and a row that says nothing is
 * the same as no row.
 */
internal class ServiceIntervalRepositoryImpl(
    private val remote: ServiceIntervalRemoteDataSource,
    private val telemetry: DataTelemetry,
) : ServiceIntervalRepository {

    override suspend fun intervals(brand: String?): Either<DomainError, Map<String, ServiceInterval>> =
        telemetry.span(SOURCE, OP_INTERVALS) {
            runCatchingCancellableSuspend { remote.schedule() }.fold(
                onSuccess = { rows -> rows.resolveFor(brand).right() },
                onFailure = { throwable ->
                    telemetry.crashed(SOURCE, OP_INTERVALS, throwable)
                    DomainError.LookupUnavailable.left()
                },
            )
        }

    /**
     * The default set, with this make's exceptions written over it.
     *
     * Applied in two passes rather than by sorting, because a job may have a default row, an
     * exception row, or only one of the two, and the exception has to win in all three cases.
     */
    private fun List<ServiceIntervalDto>.resolveFor(brand: String?): Map<String, ServiceInterval> {
        val wanted = brand?.key()
        val defaults = filter { it.brand?.key().isNullOrEmpty() }
        val exceptions = filter { wanted != null && it.brand?.key() == wanted }
        return (defaults + exceptions).asSequence()
            .map { ServiceInterval(it.slug.key(), it.displayName, it.intervalKm, it.intervalMonths) }
            .filter { it.isKnown }
            .associateBy { it.slug }
    }

    /**
     * The form two free-text keys are compared in.
     *
     * `service_schedule.brand` and `item_slug` have no foreign key and nothing constrains
     * their spelling; the slug is also what the price tables and the job vocabulary key on.
     * A row typed with a stray capital or a trailing space would otherwise apply to nothing,
     * silently and with no error to notice.
     */
    private fun String.key(): String = trim().lowercase()

    private companion object {
        const val SOURCE = "schedule"
        const val OP_INTERVALS = "intervals"
    }
}
