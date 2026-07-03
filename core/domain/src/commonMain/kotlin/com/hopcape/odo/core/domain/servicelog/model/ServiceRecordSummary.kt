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

        fun of(entries: List<ServiceLogEntry>): ServiceRecordSummary {
            if (entries.isEmpty()) return EMPTY

            val serviceCount = entries.size
            val verifiedCount = entries.count { it.verification == VerificationStatus.VERIFIED }
            val totalSpent = entries.map { it.totalAmount }.sum()
            // Odometer is ever-increasing, so the highest reading is the latest.
            val latestOdometer = entries.maxByOrNull { it.odometer.km }?.odometer
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
