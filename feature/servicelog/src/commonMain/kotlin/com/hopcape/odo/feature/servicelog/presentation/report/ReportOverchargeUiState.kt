package com.hopcape.odo.feature.servicelog.presentation.report

import com.hopcape.odo.core.domain.fairness.model.OverchargeReason
import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.feature.servicelog.presentation.state.Submission
import com.hopcape.odo.feature.servicelog.presentation.state.WorkDone
import kotlinx.datetime.LocalDate

/**
 * The flagged entry the report is about — shown in the header strip. [amountOver] is what
 * the stored verdict said it was overcharged by, so the screen states the same figure the
 * owner was shown on the list and the detail.
 */
internal data class ReportHeaderUiState(
    val workshopName: String?,
    val amountOver: Amount,
    val workDone: WorkDone,
    val serviceDate: LocalDate,
)

/**
 * Report-overcharge render state. [content] is the mutually-exclusive load phase; the chosen
 * [reason], the optional [note] and the [submission] are orthogonal and stay top-level — the
 * owner's answers survive a re-emit of the entry behind them.
 */
internal data class ReportOverchargeUiState(
    val content: Content = Content.Loading,
    val reason: OverchargeReason? = null,
    val note: String = "",
    val submission: Submission = Submission.Idle,
) {
    /**
     * A reason is mandatory: the report exists to tell the fairness pool *what kind* of
     * overcharging this was, and a report without one is a complaint nothing can learn from.
     */
    val canSubmit: Boolean get() = reason != null && !submission.isInFlight && content is Content.Loaded

    /** Filed — the screen swaps to its confirmation. */
    val isSubmitted: Boolean get() = submission.isSucceeded

    sealed interface Content {
        data object Loading : Content

        /** No live entry with this id — never written, or deleted since it was opened. */
        data object NotFound : Content

        /**
         * The entry exists but carries no overcharge verdict, so there is nothing to report.
         * Distinct from [NotFound] because the entry is fine — it just wasn't judged over,
         * and inviting a report against a price the pool called fair would put a claim in
         * the data that the data itself contradicts.
         */
        data object NotFlagged : Content

        data class Loaded(val header: ReportHeaderUiState) : Content
    }
}
