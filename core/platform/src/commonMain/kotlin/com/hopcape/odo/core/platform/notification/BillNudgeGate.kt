package com.hopcape.odo.core.platform.notification

import com.hopcape.odo.core.domain.car.ActiveCarProvider
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.servicelog.repository.ServiceLogRepository
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Whether a nudge is posted once per install. Device-local; a reinstall starts again. */
internal interface NudgeSentStore {
    suspend fun isSent(nudge: EngagementNudge): Boolean
    suspend fun markSent(nudge: EngagementNudge)
}

/** What the next-day bill nudge should do when its day arrives. */
internal enum class BillNudgeCheck { POST, ALREADY_SENT, HAS_BILL }

/**
 * Decides whether the next-day bill nudge still has something to say. A car with no resolved
 * id counts as having no bill.
 */
internal class BillNudgeGate(
    private val serviceLogs: ServiceLogRepository,
    private val activeCar: ActiveCarProvider,
    private val sent: NudgeSentStore,
    private val carTimeoutMillis: Long = CAR_TIMEOUT_MILLIS,
) {
    suspend fun check(): BillNudgeCheck {
        if (sent.isSent(EngagementNudge.FIRST_SCORE)) return BillNudgeCheck.ALREADY_SENT
        val carId = withTimeoutOrNull(carTimeoutMillis) { activeCar.activeCarId.filterNotNull().first() }
            ?: return BillNudgeCheck.POST
        val hasBill = serviceLogs.observe(carId).first().any { it.verification == VerificationStatus.VERIFIED }
        return if (hasBill) BillNudgeCheck.HAS_BILL else BillNudgeCheck.POST
    }

    private companion object {
        const val CAR_TIMEOUT_MILLIS = 5_000L
    }
}
