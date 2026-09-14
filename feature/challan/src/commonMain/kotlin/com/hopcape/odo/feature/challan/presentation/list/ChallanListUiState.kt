package com.hopcape.odo.feature.challan.presentation.list

import androidx.compose.runtime.Immutable
import com.hopcape.odo.core.designsystem.text.UiText
import com.hopcape.odo.feature.challan.presentation.state.Loadable

/**
 * Display state for the owner's challans.
 *
 * [sourceDown] sits beside the content rather than replacing it: the records service
 * being down does not erase what was known — the screen shows the failure *and* the last
 * known result with its age, which is the honest version of both facts.
 */
@Immutable
internal data class ChallanListUiState(
    val content: Loadable<ChallanListContent> = Loadable.Loading,
    /** A refresh is in flight — the pill shows it, the screen stays interactive. */
    val refreshing: Boolean = false,
    /** The last refresh could not reach the records source. */
    val sourceDown: Boolean = false,
)

/** Everything the screen can render, in every state, derived once in the ViewModel. */
@Immutable
internal data class ChallanListContent(
    /** Display form of the plate — "MH 12 AB 1234". */
    val regNo: String,
    /** "Checked 2 hours ago", or `null` before the first check. */
    val checkedAgo: UiText?,
    /** The hero card; `null` when there is nothing pending (the clean state renders). */
    val totalPending: TotalPendingCard?,
    /** Court cases — pinned above the payable ones, never in a total. */
    val courtCases: List<CourtCaseRow>,
    /** The payable challans, sectioned the way the screen reads. */
    val sections: List<ChallanSection>,
    /** Years old enough to collapse; `null` when everything is recent. */
    val older: OlderBucket?,
    val olderExpanded: Boolean = false,
    /** The clean state's facts; `null` while anything is outstanding. */
    val clean: CleanStats?,
    /** The pay CTA; `null` when there is nothing payable. */
    val pay: PayCta?,
    /** Whether "I've already paid these" is offered. */
    val offerAlreadyPaid: Boolean,
)

/** The "TOTAL PENDING" hero card. */
@Immutable
internal data class TotalPendingCard(
    val amount: String,
    val countLine: UiText,
    /** Year split segments — present only when the pending spans years. */
    val segments: List<YearSegment>,
)

/** One segment of the hero card's year bar — "2026 · Rs. 2,150". */
@Immutable
internal data class YearSegment(
    val label: String,
    val amount: String,
    /** Share of the pending total, 0..1 — the bar's proportions. */
    val fraction: Float,
)

/** One challan the owner can pay online. */
@Immutable
internal data class ChallanRow(
    val id: String,
    val violation: String,
    val number: String,
    val amount: String,
    val location: String?,
    /** "14 Aug". */
    val date: String,
)

/** A titled group of payable challans — the flat "Pending", or one year. */
@Immutable
internal data class ChallanSection(
    val title: UiText,
    val rows: List<ChallanRow>,
    /** Year sections read compact (date · number under the violation); the flat one full. */
    val compact: Boolean,
)

/** A challan that moved to court — its own card, no pay affordance at all. */
@Immutable
internal data class CourtCaseRow(
    val id: String,
    val violation: String,
    val number: String,
    val amount: String,
    val courtName: String?,
    /** "04 Sep 2026", or `null` when the source has no hearing date. */
    val nextHearing: String?,
)

/** The collapsed "Older · N challans" bucket. */
@Immutable
internal data class OlderBucket(
    val countLine: UiText,
    /** "2023–2024 · may carry added penalties". */
    val rangeLine: UiText,
    val amount: String,
    val rows: List<ChallanRow>,
)

/** The clean state's facts card. */
@Immutable
internal data class CleanStats(
    val body: UiText,
    val lastChecked: UiText?,
    /** "3 · Rs. 2,500", or `null` when nothing was cleared this year. */
    val clearedThisYear: UiText?,
    val nextCheck: UiText,
)

/** The pay CTA — its label changes with what is being paid. */
@Immutable
internal data class PayCta(
    val label: UiText,
    /** "Older challans may carry added penalties" — only under "Pay all". */
    val caption: UiText?,
)
