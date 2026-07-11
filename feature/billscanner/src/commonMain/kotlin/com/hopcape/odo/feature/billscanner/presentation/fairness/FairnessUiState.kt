package com.hopcape.odo.feature.billscanner.presentation.fairness

import arrow.core.getOrElse
import com.hopcape.odo.core.domain.shared.Amount

/** The overall fairness verdict — is the bill in line with the city average, or over? */
internal enum class FairnessVerdict { FAIR, OVER }

/**
 * One line item benchmarked against the city average for that job. [over] is non-null
 * (the amount above average) only when this item is flagged; a fair item leaves it null.
 */
internal data class FairnessLineItem(
    val label: String,
    val cityAverage: Amount,
    val amount: Amount,
    val over: Amount? = null,
)

/**
 * Display state for the "Fairness check" screen — how the reviewed bill compares to
 * the city benchmark.
 *
 * Confidence is never faked: [sampleSize] is always shown ("based on N verified bills")
 * so the benchmark can't imply false precision (per the product guardrail — with a thin
 * pool the caller surfaces a range + low-confidence label instead).
 */
internal data class FairnessUiState(
    val verdict: FairnessVerdict,
    val city: String,
    val sampleSize: Int,
    val yourBill: Amount,
    val cityAverage: Amount,
    val difference: Amount,
    val lineItems: List<FairnessLineItem>,
)

/** Overcharge sample — the continuation of the review flow (bill Rs. 3,550 vs Rs. 2,850). */
internal fun sampleFairnessOver(): FairnessUiState = FairnessUiState(
    verdict = FairnessVerdict.OVER,
    city = "Pune",
    sampleSize = 12,
    yourBill = amountOf(355_000),
    cityAverage = amountOf(285_000),
    difference = amountOf(70_000),
    lineItems = listOf(
        FairnessLineItem("Oil change", cityAverage = amountOf(210_000), amount = amountOf(280_000), over = amountOf(70_000)),
        FairnessLineItem("Air filter", cityAverage = amountOf(43_000), amount = amountOf(45_000)),
        FairnessLineItem("Labour", cityAverage = amountOf(32_000), amount = amountOf(30_000)),
    ),
)

/** Fair sample — a bill within Rs. 60 of the city average (green variant). */
internal fun sampleFairnessFair(): FairnessUiState = FairnessUiState(
    verdict = FairnessVerdict.FAIR,
    city = "Pune",
    sampleSize = 12,
    yourBill = amountOf(291_000),
    cityAverage = amountOf(285_000),
    difference = amountOf(6_000),
    lineItems = listOf(
        FairnessLineItem("Oil change", cityAverage = amountOf(210_000), amount = amountOf(280_000)),
        FairnessLineItem("Air filter", cityAverage = amountOf(43_000), amount = amountOf(45_000)),
        FairnessLineItem("Labour", cityAverage = amountOf(32_000), amount = amountOf(30_000)),
    ),
)

private fun amountOf(paise: Long): Amount = Amount.of(paise).getOrElse { Amount.ZERO }
