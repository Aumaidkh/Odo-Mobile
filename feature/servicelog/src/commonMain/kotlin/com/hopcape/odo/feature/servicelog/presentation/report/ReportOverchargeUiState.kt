package com.hopcape.odo.feature.servicelog.presentation.report

import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.core.domain.shared.Amount
import kotlinx.datetime.LocalDate

/** The "what went wrong?" options a report can cite (single-select). */
internal enum class OverchargeReason { ABOVE_MARKET_RATE, WORK_NOT_DONE, UNNECESSARY_PARTS }

/** The flagged entry the report is about — shown in the header strip. */
internal data class ReportHeaderUiState(
    val workshopName: String?,
    val amountOver: Amount,
    val workDone: String?,
    val serviceDate: LocalDate,
)

/**
 * Report-overcharge render state. [content] is the mutually-exclusive load phase; the
 * chosen [reason] + optional [note] and the submit flags are orthogonal and stay
 * top-level. A reason must be picked before the report can be submitted.
 */
internal data class ReportOverchargeUiState(
    val content: Content = Content.Loading,
    val reason: OverchargeReason? = null,
    val note: String = "",
    val isSubmitting: Boolean = false,
    val submitError: UiText? = null,
) {
    val canSubmit: Boolean get() = reason != null && !isSubmitting

    sealed interface Content {
        data object Loading : Content
        data object NotFound : Content
        data class Loaded(val header: ReportHeaderUiState) : Content
    }
}
