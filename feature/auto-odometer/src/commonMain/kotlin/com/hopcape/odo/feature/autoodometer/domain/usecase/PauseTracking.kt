package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.triptracker.TripTracker
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Settings' "Pause for a week" (M7): stops tracking now and stores when it should be
 * considered pausable-expired.
 *
 * Nothing auto-resumes when the week passes — there is no scheduling job for it (plan
 * §4.1's note on [PauseTracking]/[ResumeTracking]). `autoOdoPausedUntil` is a value the
 * settings screen reads and renders ("Paused until Tue"); resuming early or after expiry
 * is always the explicit [ResumeTracking] action.
 */
internal class PauseTracking(
    private val settings: AppSettingsRepository,
    private val tracker: TripTracker,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Either<DomainError, Unit> = either {
        val current = settings.observe().first()
        val pausedUntil = clock.now() + PAUSE_DURATION
        settings.save(current.copy(autoOdoPausedUntil = pausedUntil)).bind()
        tracker.setEnabled(false)
    }

    private companion object {
        val PAUSE_DURATION = 7.days
    }
}
