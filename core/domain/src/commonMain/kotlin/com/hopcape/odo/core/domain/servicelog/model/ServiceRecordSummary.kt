package com.hopcape.odo.core.domain.servicelog.model

import com.hopcape.odo.core.domain.shared.Amount
import com.hopcape.odo.core.domain.shared.AmountRange
import com.hopcape.odo.core.domain.shared.Distance
import com.hopcape.odo.core.domain.shared.sum
import kotlin.math.roundToInt

/**
 * Qualitative strength of a car's service record — a coarse band derived from the share
 * of **verified** (bill-backed) entries. Complements the numeric [ServiceRecordSummary.score].
 */
enum class RecordStrength {
    EMPTY, WEAK, FAIR, STRONG;

    companion object {
        fun of(serviceCount: Int, verifiedRatio: Float): RecordStrength = when {
            serviceCount == 0 -> EMPTY
            verifiedRatio >= 0.66f -> STRONG
            verifiedRatio >= 0.33f -> FAIR
            else -> WEAK
        }
    }
}

/**
 * Aggregate view of a car's service log — the single source that powers the ledger's
 * "total spent / N services" header and the timeline's record ring ("N of M verified",
 * the 0–100 [score], and the [strength] band). Built from rich domain models ([Amount],
 * [Distance], [VerificationStatus]) rather than raw primitives.
 *
 * [score] is a rule-based 0–100 [RecordScore] (PRD Health-Score style: verified share +
 * history coverage) — a defined rule, not a fake precise number. [resaleUplift] is a
 * **rough** provisional resale uplift [AmountRange] (money as paise, like everything
 * else), not an exact figure. Fairness savings are a separate concern (they need city
 * benchmarks) — see FairnessSavings.
 */
data class ServiceRecordSummary(
    val serviceCount: Int,
    val verifiedCount: Int,
    val totalSpent: Amount,
    val latestOdometer: Distance?,
    val verifiedRatio: Float,
    val strength: RecordStrength,
    val score: RecordScore,
    val resaleUplift: AmountRange?,
) {
    companion object {
        val EMPTY = ServiceRecordSummary(
            serviceCount = 0,
            verifiedCount = 0,
            totalSpent = Amount.ZERO,
            latestOdometer = null,
            verifiedRatio = 0f,
            strength = RecordStrength.EMPTY,
            score = RecordScore.ZERO,
            resaleUplift = null,
        )

        /**
         * A declared service is left out of every figure except the reading.
         *
         * It carries no bill and no money, so counting it could only lower [verifiedRatio]
         * and add a zero to [totalSpent] against a higher [serviceCount] — the record would
         * read as weaker for the owner having answered an optional question.
         */
        fun of(entries: List<ServiceLogEntry>): ServiceRecordSummary {
            val shown = entries.provable()
            if (shown.isEmpty()) return EMPTY

            val serviceCount = shown.size
            val verifiedCount = shown.count { it.verification == VerificationStatus.VERIFIED }
            val totalSpent = shown.map { it.totalAmount }.sum()
            // The most recently *serviced* entry's reading — not the highest. Entries can be
            // logged backwards (history added after onboarding), so the largest odometer is
            // not necessarily the current one. Ties on a date fall back to the higher reading.
            // Read from every entry rather than `shown`: a declared service's odometer is a
            // real reading of this car even though its money is not.
            val latestOdometer = entries
                .maxWithOrNull(compareBy({ it.serviceDate }, { it.odometer.km }))
                ?.odometer
            val verifiedRatio = verifiedCount.toFloat() / serviceCount

            return ServiceRecordSummary(
                serviceCount = serviceCount,
                verifiedCount = verifiedCount,
                totalSpent = totalSpent,
                latestOdometer = latestOdometer,
                verifiedRatio = verifiedRatio,
                strength = RecordStrength.of(serviceCount, verifiedRatio),
                score = scoreOf(serviceCount, verifiedRatio),
                resaleUplift = resaleUpliftOf(verifiedCount),
            )
        }

        /**
         * Provisional 0–100 record score: 60% weight on the verified share, 40% on
         * history coverage (saturating at 6 logged services). A defined rule, refined
         * later by the full Health Score.
         */
        private fun scoreOf(serviceCount: Int, verifiedRatio: Float): RecordScore {
            val verified = verifiedRatio * 60f
            val coverage = (minOf(serviceCount, 6) / 6f) * 40f
            return RecordScore.of((verified + coverage).roundToInt())
        }

        /**
         * Rough resale uplift range — proof pays off at resale; provisional, not exact.
         * ~Rs. 5,000–8,000 per verified service (in paise, like all money).
         */
        private fun resaleUpliftOf(verifiedCount: Int): AmountRange? =
            if (verifiedCount == 0) null
            else AmountRange.ofPaise(verifiedCount * 500_000L, verifiedCount * 800_000L)
    }
}
