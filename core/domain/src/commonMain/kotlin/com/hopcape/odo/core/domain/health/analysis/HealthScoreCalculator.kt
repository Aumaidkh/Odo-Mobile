package com.hopcape.odo.core.domain.health.analysis

import com.hopcape.odo.core.domain.document.model.Document
import com.hopcape.odo.core.domain.document.model.DocumentType
import com.hopcape.odo.core.domain.document.model.DocumentValidity
import com.hopcape.odo.core.domain.document.model.latestOfType
import com.hopcape.odo.core.domain.fairness.model.FairnessOutcome
import com.hopcape.odo.core.domain.health.model.HealthFactor
import com.hopcape.odo.core.domain.health.model.HealthFactorKind
import com.hopcape.odo.core.domain.health.model.HealthScore
import com.hopcape.odo.core.domain.servicelog.analysis.OdometerTimeline
import com.hopcape.odo.core.domain.servicelog.model.OdometerReading
import com.hopcape.odo.core.domain.servicelog.model.ServiceLogEntry
import com.hopcape.odo.core.domain.servicelog.model.VerificationStatus
import com.hopcape.odo.core.domain.servicelog.model.verification
import com.hopcape.odo.core.domain.servicelog.policy.ServiceDueStatus
import com.hopcape.odo.core.domain.servicelog.policy.ServiceIntervalPolicy
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Works out a car's 0–100 health score.
 *
 * A pure domain service — no repository, no clock, no network. It takes the car's logs,
 * papers and odometer readings and returns the number with its breakdown, so the same
 * input always gives the same score (ROADMAP M3 asks for exactly that).
 *
 * It lives in `:core:domain` rather than in `:feature:healthscore` because three surfaces
 * need the same figure: the health-score screen, the home tile, and the Resale Passport.
 * A feature may not import another feature, so the math sits in the shared kernel — the
 * same reason
 * [RunningCostCalculator][com.hopcape.odo.core.domain.cost.analysis.RunningCostCalculator]
 * does.
 *
 * **Rule-based, deterministic, explainable** (PRD §5.4, TDD ADR-006). Every point is
 * traceable to something the owner did: logged a service, uploaded a paper, attached a
 * bill. Nothing is inferred about the car's mechanical condition — the app cannot see it,
 * and pretending otherwise is the false precision the PRD forbids. An ML model can replace
 * this behind the same consumer interface later; no call site would change.
 */
object HealthScoreCalculator {

    /**
     * Which set of rules produced a score — stored alongside every snapshot (DB_SCHEMA
     * §9.9 `algo_version`).
     *
     * **Bump it whenever the point rules below change.** A stored history is only
     * comparable within one version: without this, a score that moved because the rules
     * moved is indistinguishable from one that moved because the owner did something, and
     * the "your score dropped" push would fire for a release.
     */
    const val RULES_VERSION: String = "rule-v1"

    /**
     * The shortest record worth full marks. Below it, a proportion is quoted out of three
     * rather than out of what little exists — otherwise one fair bill on a one-entry log
     * would earn all twenty cost points, which reads as a verdict on the car when it is a
     * verdict on a single receipt.
     */
    const val MIN_ENTRIES_FOR_FULL_CREDIT: Int = 3

    /** Two services a year is the cadence a 6-month interval implies. */
    private const val EXPECTED_SERVICES_PER_YEAR: Int = 2

    private const val ON_TIME_POINTS: Int = 20
    private const val CADENCE_POINTS: Int = 15
    private const val VERIFIED_POINTS: Int = 10
    private const val CONSISTENCY_POINTS: Int = 5

    /**
     * What each paper is worth, out of the documentation factor's 30.
     *
     * Insurance leads because driving without it is both illegal and the expensive thing
     * to be caught without; the RC trails because it is filed once and never renewed.
     * A licence or a loan letter earns nothing — they say nothing about the car.
     */
    private val DOCUMENT_WEIGHTS: Map<DocumentType, Int> = mapOf(
        DocumentType.INSURANCE to 12,
        DocumentType.PUC to 10,
        DocumentType.RC to 8,
    )

    /**
     * The papers that earn points, heaviest first — what a "you are missing a document"
     * nudge should ask for.
     *
     * Exposed so
     * [InsightPicker][com.hopcape.odo.core.domain.insight.analysis.InsightPicker] asks for
     * the papers that actually move the score, rather than keeping a second list that
     * drifts from this one the first time a weight changes.
     */
    val SCORED_DOCUMENT_TYPES: List<DocumentType> get() = DOCUMENT_WEIGHTS.keys.toList()

    /**
     * The score for a car as it stands on [today].
     *
     * @param entries every non-deleted service log for the car.
     * @param documents every non-deleted document for the car.
     * @param readings every known odometer reading — the logs' plus the onboarding
     *   baseline — which is what the consistency check and the service interval read.
     */
    fun compute(
        today: LocalDate,
        entries: List<ServiceLogEntry>,
        documents: List<Document>,
        readings: List<OdometerReading>,
    ): HealthScore = HealthScore(
        factors = listOf(
            HealthFactor.of(HealthFactorKind.MAINTENANCE, maintenancePoints(entries, readings, today)),
            HealthFactor.of(HealthFactorKind.DOCUMENTATION, documentationPoints(documents, today)),
            HealthFactor.of(HealthFactorKind.COST_EFFICIENCY, costEfficiencyPoints(entries)),
            HealthFactor.of(HealthFactorKind.HISTORY, historyPoints(entries, readings)),
        ),
    )

    /**
     * Maintenance (35): how the car stands against its service interval right now (20),
     * plus whether it is serviced regularly at all (15).
     *
     * Both, because they answer different questions. A car serviced twice a year and now
     * two weeks from its next slot is in better shape than one serviced once, on time,
     * three years ago.
     *
     * **Every logged entry counts as a workshop visit.** Splitting "service" from "repair"
     * would need a job taxonomy the app does not have, and a car that only ever visits a
     * garage for repairs is not the car this factor should reward anyway.
     */
    private fun maintenancePoints(
        entries: List<ServiceLogEntry>,
        readings: List<OdometerReading>,
        today: LocalDate,
    ): Int {
        if (entries.isEmpty()) return 0
        val status = ServiceIntervalPolicy.statusFor(entries, readings, today)
        return onTimePoints(status) + cadencePoints(entries, today)
    }

    /**
     * Points for where the car sits in its interval. An overdue service still scores
     * something while it is only mildly overdue — the score is meant to prompt a booking,
     * not to write the car off the day it slips.
     */
    private fun onTimePoints(status: ServiceDueStatus): Int = when (status) {
        ServiceDueStatus.NeverServiced -> 0
        is ServiceDueStatus.NotDue -> ON_TIME_POINTS
        is ServiceDueStatus.DueSoon -> 16
        is ServiceDueStatus.Overdue -> when {
            status.daysOverdue <= 30 && (status.kmOverdue ?: 0) <= 2_000 -> 10
            status.daysOverdue <= 90 && (status.kmOverdue ?: 0) <= 5_000 -> 5
            else -> 0
        }
    }

    /** Points for services logged in the last twelve months, full marks at two. */
    private fun cadencePoints(entries: List<ServiceLogEntry>, today: LocalDate): Int {
        val since = today.minus(12, DateTimeUnit.MONTH)
        val recent = entries.count { it.serviceDate > since }
        return CADENCE_POINTS * minOf(recent, EXPECTED_SERVICES_PER_YEAR) / EXPECTED_SERVICES_PER_YEAR
    }

    /**
     * Documentation (30): each paper earns its weight while it is in force, half of it
     * inside the renewal window, and nothing once it has lapsed or if it was never
     * uploaded.
     *
     * Half rather than nothing for a paper about to expire, because the owner has not done
     * anything wrong yet — the deduction is the nudge, and it doubles the day it lapses.
     */
    private fun documentationPoints(documents: List<Document>, today: LocalDate): Int =
        DOCUMENT_WEIGHTS.entries.sumOf { (type, weight) ->
            when (documents.latestOfType(type)?.validity(today)) {
                null -> 0
                is DocumentValidity.NoExpiry -> weight
                is DocumentValidity.Valid -> weight
                is DocumentValidity.ExpiringSoon -> weight / 2
                is DocumentValidity.Expired -> 0
            }
        }

    /**
     * Cost efficiency (20): the share of the car's spending that was checked against city
     * rates and came back fair.
     *
     * An entry earns here only if a fairness check cleared it. An overcharged entry earns
     * nothing, and so does one nobody ever checked — which is what makes scanning bills
     * (the PRD's North Star) the way to move this number. A
     * [TooLittleData][FairnessOutcome.TooLittleData] result counts as unchecked, never as
     * an overcharge: too thin a sample is not evidence against the owner.
     */
    private fun costEfficiencyPoints(entries: List<ServiceLogEntry>): Int {
        if (entries.isEmpty()) return 0
        val fair = entries.count { entry ->
            when (entry.fairness?.outcome) {
                FairnessOutcome.Fair, is FairnessOutcome.Under -> true
                else -> false
            }
        }
        return HealthFactorKind.COST_EFFICIENCY.weight * fair / denominator(entries.size)
    }

    /**
     * History (15): bills attached to entries (10), and an odometer history that holds
     * together (5).
     *
     * These are the two things a buyer checks, which is why the Resale Passport is built
     * from them. Consistency is all-or-nothing: a timeline with a reading that counts
     * backwards is either explained or it isn't, and a car with one rollback in it does
     * not have "most of" a clean history.
     */
    private fun historyPoints(entries: List<ServiceLogEntry>, readings: List<OdometerReading>): Int {
        val verified = entries.count { it.verification == VerificationStatus.VERIFIED }
        val verifiedPoints =
            if (entries.isEmpty()) 0 else VERIFIED_POINTS * verified / denominator(entries.size)

        // One reading proves nothing either way — a car cannot contradict itself yet.
        val consistent = readings.size >= 2 && OdometerTimeline.anomalies(readings).isEmpty()
        return verifiedPoints + if (consistent) CONSISTENCY_POINTS else 0
    }

    private fun denominator(entryCount: Int): Int = maxOf(entryCount, MIN_ENTRIES_FOR_FULL_CREDIT)
}
