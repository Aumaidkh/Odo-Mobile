package com.hopcape.odo.feature.billscanner.presentation

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.DomainError
import com.hopcape.odo.feature.billscanner.presentation.state.Submission
import com.hopcape.odo.feature.billscanner.resources.Res
import com.hopcape.odo.feature.billscanner.resources.bs_error_generic
import com.hopcape.odo.feature.billscanner.resources.bs_error_quota
import com.hopcape.odo.feature.billscanner.resources.bs_error_save_failed
import com.hopcape.odo.feature.billscanner.resources.bs_error_scan_unavailable
import com.hopcape.odo.feature.billscanner.resources.bs_error_unreadable

/**
 * Turns a [DomainError] into what the owner is told.
 *
 * One place rather than a `when` on every screen, so the same failure never gets two different
 * explanations. Some errors deliberately share a message: an unreadable photo and one that
 * yielded nothing are the same problem from the owner's side.
 */
internal fun DomainError.toSubmissionFailure(): Submission.Failed = Submission.Failed(
    when (this) {
        DomainError.ScanUnreadable, DomainError.ScanRejected -> UiText(Res.string.bs_error_unreadable)
        DomainError.ScanUnavailable -> UiText(Res.string.bs_error_scan_unavailable)
        is DomainError.ScanQuotaExhausted -> UiText(Res.string.bs_error_quota, listOf(limit))
        is DomainError.PersistenceFailure -> UiText(Res.string.bs_error_save_failed)
        else -> UiText(Res.string.bs_error_generic)
    },
)

/** The first failure is what the screen shows; the rest are for the telemetry to count. */
internal fun List<DomainError>.toSubmissionFailure(): Submission.Failed =
    firstOrNull()?.toSubmissionFailure() ?: Submission.Failed(UiText(Res.string.bs_error_generic))
