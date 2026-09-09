package com.hopcape.odo.feature.challan

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.hopcape.analytics.api.AnalyticsTracker
import com.hopcape.analytics.api.ConsentStatus
import com.hopcape.analytics.api.UserTraits
import com.hopcape.logging.api.LogLevel
import com.hopcape.logging.api.Logger
import com.hopcape.logging.api.TraceContext
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.model.Challan
import com.hopcape.odo.core.domain.challan.model.ChallanId
import com.hopcape.odo.core.domain.challan.model.ChallanLookup
import com.hopcape.odo.core.domain.challan.model.ChallanStatus
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.challan.presentation.ChallanTelemetry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant

/** Test doubles for the challans feature. */

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

internal class RecordingAnalytics : AnalyticsTracker {
    val events = mutableListOf<Pair<String, Map<String, Any?>>>()
    override fun identify(traits: UserTraits) = Unit
    override fun track(eventName: String, properties: Map<String, Any?>) {
        events += eventName to properties
    }
    override fun setConsent(status: ConsentStatus) = Unit
    override fun flush() = Unit
}

private object NoopLogger : Logger {
    override fun log(
        level: LogLevel,
        tag: String,
        event: String,
        traceContext: TraceContext?,
        fields: Map<String, Any?>,
    ) = Unit

    override fun flush() = Unit
}

internal fun testTelemetry(analytics: AnalyticsTracker = RecordingAnalytics()) =
    ChallanTelemetry(logger = NoopLogger, analytics = analytics)

/**
 * An in-memory [ChallanRepository]: seeded rows, mutable via the same operations the real
 * one offers, and switchable to "source down" for the failure paths.
 */
internal class FakeChallanRepository(
    challans: List<Challan> = emptyList(),
    lastChecked: Instant? = null,
    var sourceDown: Boolean = false,
    var vehicleKnown: Boolean = true,
    private val clock: Clock = Clock.System,
) : ChallanRepository {

    val challansFlow = MutableStateFlow(challans)
    val lastCheckedFlow = MutableStateFlow(lastChecked)
    var refreshCount = 0
        private set

    override fun observe(regNo: RegistrationNumber): Flow<List<Challan>> = challansFlow

    override fun observeLastChecked(regNo: RegistrationNumber): Flow<Instant?> = lastCheckedFlow

    override suspend fun refresh(regNo: RegistrationNumber): Either<DomainError, Unit> {
        refreshCount += 1
        if (sourceDown) return DomainError.ChallanRecordsUnreachable.left()
        lastCheckedFlow.value = clock.now()
        return Unit.right()
    }

    override suspend fun markAllPendingPaid(regNo: RegistrationNumber): Either<DomainError, Unit> {
        challansFlow.value = challansFlow.value.map { challan ->
            if (challan.status == ChallanStatus.PENDING) challan.copy(status = ChallanStatus.PAID) else challan
        }
        return Unit.right()
    }

    override suspend fun lookup(regNo: RegistrationNumber): Either<DomainError, ChallanLookup> = when {
        sourceDown -> DomainError.ChallanRecordsUnreachable.left()
        !vehicleKnown -> ChallanLookup.VehicleNotFound.right()
        else -> ChallanLookup.Found(challansFlow.value).right()
    }
}

internal val TEST_REG: RegistrationNumber = RegistrationNumber.of("MH12AB1234")!!

internal fun challan(
    id: String = "MH1220260814004521",
    status: ChallanStatus = ChallanStatus.PENDING,
    amountPaise: Long = 1_000_00,
    issuedOn: LocalDate = LocalDate(2026, 8, 14),
    violation: String = "Red light violation",
    location: String? = "Baner Road, Pune",
    courtName: String? = null,
    nextHearingOn: LocalDate? = null,
): Challan = Challan(
    id = ChallanId(id),
    regNo = TEST_REG,
    violation = violation,
    amount = Amount.of(amountPaise).getOrNull()!!,
    location = location,
    issuedOn = issuedOn,
    status = status,
    courtName = courtName,
    nextHearingOn = nextHearingOn,
)
