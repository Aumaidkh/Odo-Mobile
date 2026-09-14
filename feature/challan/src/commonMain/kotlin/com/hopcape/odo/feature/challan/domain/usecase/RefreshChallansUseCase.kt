package com.hopcape.odo.feature.challan.domain.usecase

import arrow.core.Either
import com.hopcape.odo.core.domain.car.model.RegistrationNumber
import com.hopcape.odo.core.domain.challan.repository.ChallanRepository
import com.hopcape.odo.core.domain.shared.DomainError
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Ask the records source afresh — and the staleness rule the screens share: Odo checks
 * for new challans weekly ([CHECK_INTERVAL]), so a screen opening onto a younger answer
 * does not spend a network call re-asking what it already knows.
 */
internal class RefreshChallansUseCase(
    private val challans: ChallanRepository,
    private val clock: Clock = Clock.System,
) {

    suspend operator fun invoke(regNo: RegistrationNumber): Either<DomainError, Unit> =
        challans.refresh(regNo)

    /** Whether [lastCheckedAt] is old enough that opening the screen should re-ask. */
    fun isStale(lastCheckedAt: Instant?): Boolean =
        lastCheckedAt == null || clock.now() - lastCheckedAt >= CHECK_INTERVAL

    /**
     * Whole days until the weekly re-check falls due; 0 only when it is actually due.
     * Floored ("checked 2 hours ago" reads "in 6 days"), but never rounded down to
     * zero while time remains — "due now" beside a refresh the screen is not about to
     * run would be a broken promise.
     */
    fun daysUntilNextCheck(lastCheckedAt: Instant?): Int {
        lastCheckedAt ?: return 0
        val remaining = CHECK_INTERVAL - (clock.now() - lastCheckedAt)
        if (remaining.isNegative() || remaining == kotlin.time.Duration.ZERO) return 0
        return remaining.inWholeDays.toInt().coerceAtLeast(1)
    }

    companion object {
        /** "Odo checks for new challans every week." */
        val CHECK_INTERVAL = 7.days
    }
}
