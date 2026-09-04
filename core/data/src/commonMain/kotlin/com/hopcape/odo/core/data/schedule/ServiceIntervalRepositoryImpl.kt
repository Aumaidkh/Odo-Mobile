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
 * [ServiceIntervalRepository] over the `service_categories` reference table.
 *
 * Rows with neither figure are dropped rather than carried as an empty interval: the caller's
 * question is "does the schedule say anything about this job", and a row that says nothing is
 * the same as no row.
 */
internal class ServiceIntervalRepositoryImpl(
    private val remote: ServiceIntervalRemoteDataSource,
    private val telemetry: DataTelemetry,
) : ServiceIntervalRepository {

    override suspend fun intervals(): Either<DomainError, Map<String, ServiceInterval>> =
        telemetry.span(SOURCE, OP_INTERVALS) {
            runCatchingCancellableSuspend { remote.intervals() }.fold(
                onSuccess = { rows ->
                    rows.asSequence()
                        .map { ServiceInterval(it.slug, it.intervalKm, it.intervalMonths) }
                        .filter { it.isKnown }
                        .associateBy { it.slug }
                        .right()
                },
                onFailure = { throwable ->
                    telemetry.crashed(SOURCE, OP_INTERVALS, throwable)
                    DomainError.LookupUnavailable.left()
                },
            )
        }

    private companion object {
        const val SOURCE = "schedule"
        const val OP_INTERVALS = "intervals"
    }
}
