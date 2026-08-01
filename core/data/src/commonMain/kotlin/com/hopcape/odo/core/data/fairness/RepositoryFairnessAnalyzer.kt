package com.hopcape.odo.core.data.fairness

import com.hopcape.odo.core.domain.fairness.analysis.FairnessAnalyzer
import com.hopcape.odo.core.domain.fairness.model.FairnessQuery
import com.hopcape.odo.core.domain.fairness.model.FairnessReport
import com.hopcape.odo.core.domain.fairness.repository.FairnessRepository

/**
 * The real [FairnessAnalyzer]: fetch the city benchmarks for the query's categories, then
 * hand them to the domain's own math.
 *
 * It is a composition of a port and a pure function, which is why it lives in the data layer
 * rather than in a feature. Any feature that needs a verdict injects [FairnessAnalyzer] and
 * gets the same numbers; nobody re-implements [FairnessReport.of], and there is one set of
 * benchmarks in the app rather than one per caller.
 */
internal class RepositoryFairnessAnalyzer(
    private val fairness: FairnessRepository,
) : FairnessAnalyzer {

    override suspend fun analyze(query: FairnessQuery): FairnessReport =
        FairnessReport.of(query, fairness.estimates(query.categories, query.city))
}
