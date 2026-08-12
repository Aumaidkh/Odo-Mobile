package com.hopcape.odo.core.domain.fairness.analysis

import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessReport

/**
 * Runs a fairness check: benchmarks a [FairnessQuery] against city averages and returns a
 * [FairnessReport]. This is a **port** — the real adapter fetches the benchmarks from
 * [FairnessRepository][com.hopcape.odo.core.domain.fairness.repository.FairnessRepository]
 * (the server's de-identified aggregate) and hands them to [FairnessReport.of]; the MVP
 * ships a sample stand-in. Every feature invokes fairness through this one contract, so
 * the analysis lives in one place.
 *
 * The port covers *fetching* the benchmarks; the verdict math is [FairnessReport.of] and
 * is pure, which is why an adapter never re-implements it.
 */
interface FairnessAnalyzer {
    suspend fun analyze(query: FairnessQuery): FairnessReport
}
