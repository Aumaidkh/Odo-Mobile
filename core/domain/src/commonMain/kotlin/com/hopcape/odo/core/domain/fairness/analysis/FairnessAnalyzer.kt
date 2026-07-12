package com.hopcape.odo.core.domain.fairness.analysis

import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessReport

/**
 * Runs a fairness check: benchmarks a [FairnessQuery] against city averages and returns a
 * [FairnessReport]. This is a **port** — the real adapter reads the server aggregate
 * (`get_fairness_estimate`, de-identified pool); the MVP ships a sample stand-in. Every
 * feature invokes fairness through this one contract, so the analysis lives in one place.
 */
interface FairnessAnalyzer {
    suspend fun analyze(query: FairnessQuery): FairnessReport
}
