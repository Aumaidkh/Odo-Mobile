package com.hopcape.odo.feature.autoodometer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import com.hopcape.odo.core.domain.settings.repository.AppSettingsRepository
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.core.triptracker.TripTracker
import kotlinx.coroutines.flow.first

/** Settings' manual "resume" out of a pause: clears the marker and turns tracking back on. */
internal class ResumeTracking(
    private val settings: AppSettingsRepository,
    private val tracker: TripTracker,
) {
    suspend operator fun invoke(): Either<DomainError, Unit> = either {
        val current = settings.observe().first()
        settings.save(current.copy(autoOdoPausedUntil = null)).bind()
        tracker.setEnabled(true)
    }
}
