package com.hopcape.odo.feature.servicelog.presentation.list

import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.feature.servicelog.domain.usecase.ServiceLogFeed
import com.hopcape.odo.feature.servicelog.presentation.state.workDone

/**
 * The car's entries → what the list renders. A pure function, kept out of the ViewModel so
 * the ViewModel reads as "observe the feed, show it" and this reads as the one place a
 * domain entry becomes a row.
 *
 * The filter is applied here rather than by the UI because the chips' counts and the rows
 * have to agree: both are derived from the same feed in the same pass.
 */
internal fun ServiceLogFeed.toContent(filter: ServiceLogFilter): ServiceLogListUiState.Content {
    if (entries.isEmpty()) return ServiceLogListUiState.Content.Empty

    val cards = entries.map(ServiceLogEntry::toCardUiState)
    return ServiceLogListUiState.Content.Loaded(
        cards = cards.filter { it.matches(filter) },
        summary = summary,
        savings = savings,
        counts = ServiceLogFilterCounts(
            all = cards.size,
            verified = verifiedCount,
            flagged = flaggedCount,
        ),
    )
}

internal fun ServiceLogEntry.toCardUiState(): ServiceLogCardUiState = ServiceLogCardUiState(
    id = id,
    workshopName = workshopName?.value,
    serviceDate = serviceDate,
    odometer = odometer,
    amount = totalAmount,
    verification = verification,
    workDone = workDone(),
    fairness = fairnessBadge(),
)

/**
 * The badge for one entry, in the order the trust model reads: without a bill there is
 * nothing to judge, without a stored verdict nothing has been judged yet, and only then
 * does the verdict itself decide.
 *
 * The outcome is the one that was **stored** when the check ran, never a fresh one — see
 * `FairnessSnapshot`: the figure the owner was shown is the figure they keep seeing.
 */
private fun ServiceLogEntry.fairnessBadge(): ServiceLogFairnessBadge = when {
    verification == VerificationStatus.SELF_REPORTED -> ServiceLogFairnessBadge.AddBillToVerify
    else -> when (val outcome = fairness?.outcome) {
        // No check stored, and a check that found no city average to compare against, are
        // the same thing to a card: nothing has been judged.
        null, FairnessOutcome.NoBenchmark -> ServiceLogFairnessBadge.NotYetChecked
        is FairnessOutcome.Over -> ServiceLogFairnessBadge.Overcharged(outcome.by)
        is FairnessOutcome.TooLittleData -> ServiceLogFairnessBadge.NotEnoughData(outcome.estimate)
        // Under the average is a fair price too — the product flags overcharging, and an
        // owner who paid less doesn't need a second, cleverer word for "you're fine".
        FairnessOutcome.Fair, is FairnessOutcome.Under -> ServiceLogFairnessBadge.FairPrice
    }
}

private fun ServiceLogCardUiState.matches(filter: ServiceLogFilter): Boolean = when (filter) {
    ServiceLogFilter.ALL -> true
    ServiceLogFilter.VERIFIED -> verification == VerificationStatus.VERIFIED
    ServiceLogFilter.FLAGGED -> fairness is ServiceLogFairnessBadge.Overcharged
}
